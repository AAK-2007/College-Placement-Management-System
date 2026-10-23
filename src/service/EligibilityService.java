package service;

import dao.JobDAO;
import dao.StudentDAO;
import model.Job;
import model.Student;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Core business logic that compares student records against job
 * eligibility criteria. This is the heart of the "Eligibility Analysis"
 * half of the project - it never touches SQL directly, only DAOs and
 * in-memory Student/Job objects, which keeps the rule engine easy to
 * unit-test and independent of the database.
 */
public class EligibilityService {

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
     * required skills at all). They no longer need every skill.
     */
    private boolean meetsSkills(Student student, Job job) {
        if (job.getRequiredSkillList().length == 0) {
            return true; // no specific skill requirement
        }
        return countMatchingSkills(student, job) > 0;
    }

    /** How many of the job's required skills this student has. Used for the check and for ranking. */
    private int countMatchingSkills(Student student, Job job) {
        List<String> studentSkills = Arrays.asList(student.getSkillList());
        int matches = 0;
        for (String skill : job.getRequiredSkillList()) {
            if (studentSkills.contains(skill)) {
                matches++;
            }
        }
        return matches;
    }

    /**
     * Returns every student eligible for the given job.
     * <p>
     * Performance note: rather than pulling every student row into Java and
     * checking all four criteria here, the CGPA/backlog/department filter
     * is pushed down to SQL first ({@code StudentDAO.findCandidatesByCriteria}),
     * since those are cheap, indexable, typed comparisons the database
     * engine handles far better than a Java loop over the whole table.
     * Only the skill check - which cannot be expressed cleanly in SQL over
     * a comma-separated free-text column - runs in Java, and only against
     * the small candidate set that already passed everything else.
     */
    public List<Student> getEligibleStudentsForJob(Job job) {
        List<Student> candidates = studentDAO.findCandidatesByCriteria(
                job.getMinCgpa(), job.getMaxBacklogs(), job.getEligibleDepartmentList());

        List<Student> eligible = new ArrayList<>();
        for (Student s : candidates) {
            if (meetsSkills(s, job)) {
                eligible.add(s);
            }
        }
        // Best skill match first, so students with more matching skills are listed at the top.
        eligible.sort(Comparator.comparingInt((Student s) -> countMatchingSkills(s, job)).reversed());
        return eligible;
    }

    /**
     * Returns every job that the given student currently qualifies for.
     * Same approach as {@link #getEligibleStudentsForJob}: CGPA, backlogs,
     * and department are filtered in SQL; only the skill check runs in
     * Java, over the reduced result set.
     */
    public List<Job> getEligibleJobsForStudent(Student student) {
        List<Job> candidates = jobDAO.findJobsMatchingStudent(
                student.getCgpa(), student.getBacklogs(), student.getDepartment());

        List<Job> eligible = new ArrayList<>();
        for (Job j : candidates) {
            if (meetsSkills(student, j)) {
                eligible.add(j);
            }
        }
        // Jobs where the student matches the most required skills come first.
        eligible.sort(Comparator.comparingInt((Job j) -> countMatchingSkills(student, j)).reversed());
        return eligible;
    }

    /**
     * Produces a human-readable explanation of why a student does or does
     * not qualify for a job - useful for showing students exactly which
     * criterion they are failing.
     */
    public String explainEligibility(Student student, Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append("Eligibility check for ").append(student.getName())
                .append(" against ").append(job.getRole()).append(":\n");
        sb.append(String.format("  CGPA: %.2f (required >= %.2f) -> %s%n",
                student.getCgpa(), job.getMinCgpa(), meetsCgpa(student, job) ? "PASS" : "FAIL"));
        sb.append(String.format("  Backlogs: %d (allowed <= %d) -> %s%n",
                student.getBacklogs(), job.getMaxBacklogs(), meetsBacklogLimit(student, job) ? "PASS" : "FAIL"));
        sb.append(String.format("  Department: %s (eligible: %s) -> %s%n",
                student.getDepartment(), job.getEligibleDepartments(), meetsDepartment(student, job) ? "PASS" : "FAIL"));
        sb.append(String.format("  Skills: %s (required: %s) -> %d of %d matched -> %s%n",
                student.getSkills(), job.getRequiredSkills(),
                countMatchingSkills(student, job), job.getRequiredSkillList().length,
                meetsSkills(student, job) ? "PASS" : "FAIL"));
        sb.append("  Overall: ").append(isEligible(student, job) ? "ELIGIBLE" : "NOT ELIGIBLE");
        return sb.toString();
    }
}