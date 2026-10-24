package service;

import dao.JobDAO;
import dao.StudentDAO;
import model.Job;
import model.Student;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Core business logic that compares student records against job
 * eligibility criteria. This is the heart of the "Eligibility Analysis"
 * half of the project - it never touches SQL directly, only DAOs and
 * in-memory Student/Job objects, which keeps the rule engine easy to
 * unit-test and independent of the database.
 * <p>
 * Web version: the rules are unchanged. The methods that used to build
 * console text now return small records the web pages can display.
 */
public class EligibilityService {

    /** A student who qualifies for a job, with how many of the job's required skills they have. */
    public record RankedStudent(Student student, int matchedSkills, int requiredSkills) {
    }

    /** A job a student qualifies for, with how many of its required skills the student has. */
    public record RankedJob(Job job, int matchedSkills, int requiredSkills) {
    }

    /** One line of the eligibility explanation, e.g. CGPA 8.50 vs required >= 7.00 -> PASS. */
    public record CriterionCheck(String criterion, String studentValue, String requirement,
                                 String detail, boolean passed) {
    }

    /** Full PASS/FAIL breakdown of one student against one job. */
    public record EligibilityReport(Student student, Job job, List<CriterionCheck> checks, boolean eligible) {
    }

    private final StudentDAO studentDAO;
    private final JobDAO jobDAO;

    public EligibilityService(StudentDAO studentDAO, JobDAO jobDAO) {
        this.studentDAO = studentDAO;
        this.jobDAO = jobDAO;
    }

    /**
     * Returns true if the given student satisfies every eligibility
     * criterion of the given job: minimum CGPA, backlog ceiling,
     * department restriction, and at least one matching required skill.
     */
    public boolean isEligible(Student student, Job job) {
        return meetsCgpa(student, job)
                && meetsBacklogLimit(student, job)
                && meetsDepartment(student, job)
                && meetsSkills(student, job);
    }

    private boolean meetsCgpa(Student student, Job job) {
        return student.getCgpa() >= job.getMinCgpa();
    }

    private boolean meetsBacklogLimit(Student student, Job job) {
        return student.getBacklogs() <= job.getMaxBacklogs();
    }

    private boolean meetsDepartment(Student student, Job job) {
        String[] eligibleDepts = job.getEligibleDepartmentList();
        if (eligibleDepts.length == 0) {
            return true; // no restriction specified
        }
        if (Arrays.asList(eligibleDepts).contains("ALL")) {
            return true;
        }
        return Arrays.asList(eligibleDepts).contains(student.getDepartment().toUpperCase());
    }

    /**
     * Partial skill matching: a student passes the skill check if they have
     * AT LEAST ONE of the skills the job requires (or if the job lists no
     * required skills at all). They do not need every skill.
     */
    private boolean meetsSkills(Student student, Job job) {
        String[] required = job.getRequiredSkillList();
        return passesSkillCheck(required.length, matchingSkills(skillSet(student), required).size());
    }

    /** The skill rule itself: no skills required, or at least one of them matched. */
    private static boolean passesSkillCheck(int requiredCount, int matchedCount) {
        return requiredCount == 0 || matchedCount > 0;
    }

    /** The student's skills (lower case, trimmed) as a set, built once per student. */
    private static Set<String> skillSet(Student student) {
        return new HashSet<>(Arrays.asList(student.getSkillList()));
    }

    /** The job's required skills that the student has (lower case, in the job's order). */
    private static List<String> matchingSkills(Set<String> studentSkills, String[] requiredSkills) {
        List<String> matches = new ArrayList<>();
        for (String skill : requiredSkills) {
            if (studentSkills.contains(skill)) {
                matches.add(skill);
            }
        }
        return matches;
    }

    /**
     * Returns every student eligible for the given job, best skill match first.
     * <p>
     * Performance note: rather than pulling every student row into Java and
     * checking all four criteria here, the CGPA/backlog/department filter
     * is pushed down to SQL first ({@code StudentDAO.findCandidatesByCriteria}),
     * since those are cheap, typed comparisons the database engine handles far
     * better than a Java loop over the whole table. Only the skill check - which
     * cannot be expressed cleanly in SQL over a comma-separated free-text
     * column - runs in Java, and only against the small candidate set that
     * already passed everything else. Each candidate's skills are matched once.
     */
    public List<RankedStudent> getEligibleStudentsForJob(Job job) {
        List<Student> candidates = studentDAO.findCandidatesByCriteria(
                job.getMinCgpa(), job.getMaxBacklogs(), job.getEligibleDepartmentList());

        String[] required = job.getRequiredSkillList();
        List<RankedStudent> eligible = new ArrayList<>();
        for (Student s : candidates) {
            int matched = matchingSkills(skillSet(s), required).size();
            if (passesSkillCheck(required.length, matched)) {
                eligible.add(new RankedStudent(s, matched, required.length));
            }
        }
        // Best skill match first, so students with more matching skills are listed at the top.
        eligible.sort(Comparator.comparingInt(RankedStudent::matchedSkills).reversed());
        return eligible;
    }

    /**
     * Returns every job that the given student currently qualifies for, best
     * skill match first. Same approach as {@link #getEligibleStudentsForJob}:
     * CGPA, backlogs, and department are filtered in SQL; only the skill
     * check runs in Java, over the reduced result set. The student's skills
     * are split once and reused for every job.
     */
    public List<RankedJob> getEligibleJobsForStudent(Student student) {
        List<Job> candidates = jobDAO.findJobsMatchingStudent(
                student.getCgpa(), student.getBacklogs(), student.getDepartment());

        Set<String> studentSkills = skillSet(student);
        List<RankedJob> eligible = new ArrayList<>();
        for (Job j : candidates) {
            String[] required = j.getRequiredSkillList();
            int matched = matchingSkills(studentSkills, required).size();
            if (passesSkillCheck(required.length, matched)) {
                eligible.add(new RankedJob(j, matched, required.length));
            }
        }
        // Jobs where the student matches the most required skills come first.
        eligible.sort(Comparator.comparingInt(RankedJob::matchedSkills).reversed());
        return eligible;
    }

    /**
     * Explains, criterion by criterion, why a student does or does not
     * qualify for a job. Uses the same private checks as {@link #isEligible},
     * so the explanation can never disagree with the actual decision.
     */
    public EligibilityReport explainEligibility(Student student, Job job) {
        List<CriterionCheck> checks = new ArrayList<>();

        boolean cgpaOk = meetsCgpa(student, job);
        checks.add(new CriterionCheck("CGPA",
                String.format("%.2f", student.getCgpa()),
                String.format(">= %.2f", job.getMinCgpa()),
                null,
                cgpaOk));

        boolean backlogsOk = meetsBacklogLimit(student, job);
        checks.add(new CriterionCheck("Backlogs",
                String.valueOf(student.getBacklogs()),
                "<= " + job.getMaxBacklogs(),
                null,
                backlogsOk));

        boolean departmentOk = meetsDepartment(student, job);
        checks.add(new CriterionCheck("Department",
                student.getDepartment(),
                "one of: " + job.getEligibleDepartments(),
                null,
                departmentOk));

        String[] requiredSkills = job.getRequiredSkillList();
        int required = requiredSkills.length;
        List<String> matched = matchingSkills(skillSet(student), requiredSkills);
        boolean skillsOk = passesSkillCheck(required, matched.size());
        String skillDetail = required == 0
                ? "No skills required"
                : matched.size() + " of " + required + " matched"
                  + (matched.isEmpty() ? "" : " (" + String.join(", ", matched) + ")");
        checks.add(new CriterionCheck("Skills",
                isBlank(student.getSkills()) ? "(none)" : student.getSkills(),
                required == 0 ? "none required" : "at least one of: " + job.getRequiredSkills(),
                skillDetail,
                skillsOk));

        // Same four checks as isEligible, so the overall result always agrees with it.
        boolean eligible = cgpaOk && backlogsOk && departmentOk && skillsOk;
        return new EligibilityReport(student, job, checks, eligible);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
