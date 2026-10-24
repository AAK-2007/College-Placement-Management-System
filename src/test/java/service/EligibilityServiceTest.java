package service;

import dao.JobDAO;
import dao.StudentDAO;
import model.Job;
import model.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import service.EligibilityService.CriterionCheck;
import service.EligibilityService.EligibilityReport;
import service.EligibilityService.RankedJob;
import service.EligibilityService.RankedStudent;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Eligibility rules: CGPA, backlogs, department (or ALL), at least one skill, ranking. No database needed. */
class EligibilityServiceTest {

    private StudentDAO studentDAO;
    private JobDAO jobDAO;
    private EligibilityService service;

    @BeforeEach
    void setUp() {
        studentDAO = mock(StudentDAO.class);
        jobDAO = mock(JobDAO.class);
        service = new EligibilityService(studentDAO, jobDAO);
    }

    static Student student(String name, String dept, double cgpa, int backlogs, String skills) {
        Student s = new Student("REG-" + name, name, dept, cgpa, backlogs, skills, null, null);
        s.setStudentId(Math.abs(name.hashCode()));
        return s;
    }

    static Job job(double minCgpa, String depts, int maxBacklogs, String skills) {
        return new Job(1, "Role", minCgpa, depts, maxBacklogs, skills, 5.0);
    }

    @Nested
    @DisplayName("isEligible")
    class IsEligible {

        @Test
        void passesWhenEveryCriterionIsMet() {
            assertTrue(service.isEligible(student("a", "CSE", 8.0, 0, "Java,SQL"), job(7.0, "CSE,IT", 1, "Java")));
        }

        @Test
        void cgpaEqualToMinimumPasses() {
            assertTrue(service.isEligible(student("a", "CSE", 7.0, 0, "Java"), job(7.0, "ALL", 0, "Java")));
        }

        @Test
        void cgpaBelowMinimumFails() {
            assertFalse(service.isEligible(student("a", "CSE", 6.99, 0, "Java"), job(7.0, "ALL", 0, "Java")));
        }

        @Test
        void backlogsEqualToMaximumPass() {
            assertTrue(service.isEligible(student("a", "CSE", 8.0, 2, "Java"), job(7.0, "ALL", 2, "Java")));
        }

        @Test
        void backlogsAboveMaximumFail() {
            assertFalse(service.isEligible(student("a", "CSE", 8.0, 3, "Java"), job(7.0, "ALL", 2, "Java")));
        }

        @Test
        void departmentNotInListFails() {
            assertFalse(service.isEligible(student("a", "MECH", 8.0, 0, "Java"), job(7.0, "CSE,IT", 0, "Java")));
        }

        @Test
        void departmentAllAcceptsEveryDepartment() {
            assertTrue(service.isEligible(student("a", "MECH", 8.0, 0, "Java"), job(7.0, "ALL", 0, "Java")));
        }

        @Test
        void departmentMatchIgnoresCaseAndSpaces() {
            assertTrue(service.isEligible(student("a", "it", 8.0, 0, "Java"), job(7.0, "CSE, IT", 0, "Java")));
        }

        @Test
        void oneMatchingSkillOutOfSeveralIsEnough() {
            assertTrue(service.isEligible(student("a", "CSE", 8.0, 0, "Python"), job(7.0, "ALL", 0, "Java,Python,C")));
        }

        @Test
        void noMatchingSkillFails() {
            assertFalse(service.isEligible(student("a", "CSE", 8.0, 0, "Go,Rust"), job(7.0, "ALL", 0, "Java,Python")));
        }

        @Test
        void noRequiredSkillsPassesEvenWithoutSkills() {
            assertTrue(service.isEligible(student("a", "CSE", 8.0, 0, null), job(7.0, "ALL", 0, "")));
        }

        @Test
        void studentWithoutSkillsFailsWhenSkillsAreRequired() {
            assertFalse(service.isEligible(student("a", "CSE", 8.0, 0, null), job(7.0, "ALL", 0, "Java")));
        }

        @Test
        void skillMatchIgnoresCaseAndSpaces() {
            assertTrue(service.isEligible(student("a", "CSE", 8.0, 0, "  jAvA , sql"), job(7.0, "ALL", 0, "Java")));
        }
    }

    @Nested
    @DisplayName("getEligibleStudentsForJob")
    class StudentsForJob {

        @Test
        void pushesCgpaBacklogAndDepartmentToSqlThenFiltersSkillsAndRanks() {
            Job job = job(7.0, "CSE, IT", 1, "Java,SQL,Python");
            Student one = student("one", "CSE", 8.0, 0, "Java");               // 1 match
            Student none = student("none", "CSE", 9.0, 0, "Go");              // 0 matches -> excluded
            Student three = student("three", "IT", 7.5, 1, "Python,SQL,Java"); // 3 matches
            Student two = student("two", "CSE", 8.5, 0, "SQL,Java");           // 2 matches
            when(studentDAO.findCandidatesByCriteria(eq(7.0), eq(1), aryEq(new String[]{"CSE", "IT"})))
                    .thenReturn(List.of(one, none, three, two));

            List<RankedStudent> result = service.getEligibleStudentsForJob(job);

            assertEquals(List.of("three", "two", "one"), result.stream().map(r -> r.student().getName()).toList());
            assertEquals(List.of(3, 2, 1), result.stream().map(RankedStudent::matchedSkills).toList());
            assertTrue(result.stream().allMatch(r -> r.requiredSkills() == 3));
        }

        @Test
        void tiesKeepTheDatabaseOrder() {
            Job job = job(0, "ALL", 5, "Java");
            Student first = student("first", "CSE", 8.0, 0, "Java");
            Student second = student("second", "IT", 8.0, 0, "Java");
            when(studentDAO.findCandidatesByCriteria(anyDouble(), anyInt(), any())).thenReturn(List.of(first, second));

            List<RankedStudent> result = service.getEligibleStudentsForJob(job);

            assertEquals(List.of("first", "second"), result.stream().map(r -> r.student().getName()).toList());
        }

        @Test
        void noRequiredSkillsKeepsEveryCandidate() {
            Job job = job(6.0, "ALL", 0, null);
            when(studentDAO.findCandidatesByCriteria(anyDouble(), anyInt(), any()))
                    .thenReturn(List.of(student("x", "CSE", 7.0, 0, null), student("y", "IT", 7.0, 0, "Go")));

            List<RankedStudent> result = service.getEligibleStudentsForJob(job);

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(r -> r.requiredSkills() == 0 && r.matchedSkills() == 0));
        }
    }

    @Nested
    @DisplayName("getEligibleJobsForStudent")
    class JobsForStudent {

        @Test
        void pushesStudentFiguresToSqlThenFiltersSkillsAndRanks() {
            Student s = student("s", "CSE", 8.0, 0, "Java,SQL");
            Job oneMatch = job(7.0, "ALL", 0, "Java,C");
            Job noMatch = job(7.0, "ALL", 0, "Go");
            Job twoMatches = job(7.0, "CSE", 0, "SQL,Java,Python");
            Job noSkills = job(7.0, "ALL", 0, "");
            oneMatch.setRole("one");
            noMatch.setRole("none");
            twoMatches.setRole("two");
            noSkills.setRole("open");
            when(jobDAO.findJobsMatchingStudent(8.0, 0, "CSE")).thenReturn(List.of(oneMatch, noMatch, twoMatches, noSkills));

            List<RankedJob> result = service.getEligibleJobsForStudent(s);

            assertEquals(List.of("two", "one", "open"), result.stream().map(r -> r.job().getRole()).toList());
            verify(jobDAO).findJobsMatchingStudent(8.0, 0, "CSE");
        }
    }

    @Nested
    @DisplayName("explainEligibility")
    class Explain {

        @Test
        void listsEachCriterionWithPassOrFail() {
            Student s = student("s", "ECE", 6.5, 3, "Java,Go");
            Job j = job(7.0, "CSE,IT", 2, "Java,Python");

            EligibilityReport report = service.explainEligibility(s, j);

            assertEquals(List.of("CGPA", "Backlogs", "Department", "Skills"),
                    report.checks().stream().map(CriterionCheck::criterion).toList());
            assertEquals(List.of(false, false, false, true),
                    report.checks().stream().map(CriterionCheck::passed).toList());
            assertEquals("1 of 2 matched (java)", report.checks().get(3).detail());
            assertFalse(report.eligible());
        }

        @Test
        void overallResultAlwaysAgreesWithIsEligible() {
            Student s = student("s", "CSE", 8.0, 0, "Python");
            Job j = job(7.0, "ALL", 1, "");

            EligibilityReport report = service.explainEligibility(s, j);

            assertTrue(report.eligible());
            assertEquals(service.isEligible(s, j), report.eligible());
            assertEquals("No skills required", report.checks().get(3).detail());
            assertTrue(report.checks().stream().allMatch(CriterionCheck::passed));
        }
    }
}
