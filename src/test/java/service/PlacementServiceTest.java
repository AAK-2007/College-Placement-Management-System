package service;

import dao.ApplicationDAO;
import dao.InterviewDAO;
import dao.JobDAO;
import dao.StudentDAO;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import service.PlacementService.*;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Apply rules, status flow and finalize rules. DAOs are mocked, so no database is needed. */
class PlacementServiceTest {

    private static final int STUDENT_ID = 1;
    private static final int JOB_ID = 10;
    private static final int APP_ID = 100;

    private StudentDAO studentDAO;
    private JobDAO jobDAO;
    private ApplicationDAO applicationDAO;
    private InterviewDAO interviewDAO;
    private PlacementService service;

    @BeforeEach
    void setUp() {
        studentDAO = mock(StudentDAO.class);
        jobDAO = mock(JobDAO.class);
        applicationDAO = mock(ApplicationDAO.class);
        interviewDAO = mock(InterviewDAO.class);
        // The real eligibility rules are used; only the database is replaced.
        EligibilityService eligibility = new EligibilityService(studentDAO, jobDAO);
        service = new PlacementService(studentDAO, jobDAO, applicationDAO, interviewDAO, eligibility);
    }

    // ---------------- helpers ----------------

    private static Student eligibleStudent() {
        Student s = new Student("REG1", "Asha", "CSE", 8.5, 0, "Java,SQL", null, null);
        s.setStudentId(STUDENT_ID);
        return s;
    }

    private static Job job() {
        Job j = new Job(1, "Developer", 7.0, "CSE,IT", 0, "Java", 6.0);
        j.setJobId(JOB_ID);
        return j;
    }

    private void givenApplication(ApplicationStatus status) {
        Application a = new Application(STUDENT_ID, JOB_ID, LocalDate.now(), status);
        a.setApplicationId(APP_ID);
        when(applicationDAO.getApplicationById(APP_ID)).thenReturn(a);
    }

    private void givenRounds(InterviewResult... results) {
        List<Interview> rounds = Arrays.stream(results)
                .map(r -> new Interview(APP_ID, LocalDate.now(), "Round", r, ""))
                .toList();
        when(interviewDAO.getInterviewsByApplication(APP_ID)).thenReturn(rounds);
    }

    // ---------------- apply ----------------

    @Nested
    @DisplayName("apply")
    class Apply {

        @Test
        void studentNotFound() {
            assertEquals(ApplyResult.STUDENT_NOT_FOUND, service.apply(STUDENT_ID, JOB_ID));
            verify(applicationDAO, never()).addApplication(any());
        }

        @Test
        void jobNotFound() {
            when(studentDAO.getStudentById(STUDENT_ID)).thenReturn(eligibleStudent());
            assertEquals(ApplyResult.JOB_NOT_FOUND, service.apply(STUDENT_ID, JOB_ID));
            verify(applicationDAO, never()).addApplication(any());
        }

        @Test
        void alreadyApplied() {
            when(studentDAO.getStudentById(STUDENT_ID)).thenReturn(eligibleStudent());
            when(jobDAO.getJobById(JOB_ID)).thenReturn(job());
            when(applicationDAO.hasApplied(STUDENT_ID, JOB_ID)).thenReturn(true);

            assertEquals(ApplyResult.ALREADY_APPLIED, service.apply(STUDENT_ID, JOB_ID));
            verify(applicationDAO, never()).addApplication(any());
        }

        @Test
        void notEligible() {
            Student weak = eligibleStudent();
            weak.setCgpa(6.0);
            when(studentDAO.getStudentById(STUDENT_ID)).thenReturn(weak);
            when(jobDAO.getJobById(JOB_ID)).thenReturn(job());

            assertEquals(ApplyResult.NOT_ELIGIBLE, service.apply(STUDENT_ID, JOB_ID));
            verify(applicationDAO, never()).addApplication(any());
        }

        @Test
        void successStoresAnAppliedApplicationDatedToday() {
            when(studentDAO.getStudentById(STUDENT_ID)).thenReturn(eligibleStudent());
            when(jobDAO.getJobById(JOB_ID)).thenReturn(job());

            assertEquals(ApplyResult.SUCCESS, service.apply(STUDENT_ID, JOB_ID));

            ArgumentCaptor<Application> saved = ArgumentCaptor.forClass(Application.class);
            verify(applicationDAO).addApplication(saved.capture());
            assertEquals(STUDENT_ID, saved.getValue().getStudentId());
            assertEquals(JOB_ID, saved.getValue().getJobId());
            assertEquals(ApplicationStatus.APPLIED, saved.getValue().getStatus());
            assertEquals(LocalDate.now(), saved.getValue().getApplicationDate());
        }

        @Test
        void duplicateCaughtByUniqueKeyIsReportedAsAlreadyApplied() {
            when(studentDAO.getStudentById(STUDENT_ID)).thenReturn(eligibleStudent());
            when(jobDAO.getJobById(JOB_ID)).thenReturn(job());
            when(applicationDAO.addApplication(any())).thenThrow(new RuntimeException("Failed to add application",
                    new SQLIntegrityConstraintViolationException("Duplicate entry")));

            assertEquals(ApplyResult.ALREADY_APPLIED, service.apply(STUDENT_ID, JOB_ID));
        }

        @Test
        void otherDatabaseErrorsAreNotHidden() {
            when(studentDAO.getStudentById(STUDENT_ID)).thenReturn(eligibleStudent());
            when(jobDAO.getJobById(JOB_ID)).thenReturn(job());
            when(applicationDAO.addApplication(any())).thenThrow(new RuntimeException("connection lost"));

            assertThrows(RuntimeException.class, () -> service.apply(STUDENT_ID, JOB_ID));
        }
    }

    // ---------------- status flow ----------------

    @Nested
    @DisplayName("shortlist")
    class Shortlist {

        @Test
        void notFound() {
            assertEquals(StepResult.NOT_FOUND, service.shortlist(APP_ID));
        }

        @Test
        void appliedBecomesShortlisted() {
            givenApplication(ApplicationStatus.APPLIED);
            assertEquals(StepResult.OK, service.shortlist(APP_ID));
            verify(applicationDAO).updateStatus(APP_ID, ApplicationStatus.SHORTLISTED);
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationStatus.class, names = "APPLIED", mode = EnumSource.Mode.EXCLUDE)
        void onlyAppliedCanBeShortlisted(ApplicationStatus status) {
            givenApplication(status);
            assertEquals(StepResult.WRONG_STATUS, service.shortlist(APP_ID));
            verify(applicationDAO, never()).updateStatus(anyInt(), any());
        }
    }

    @Nested
    @DisplayName("scheduleInterview")
    class Schedule {

        @Test
        void notFound() {
            assertEquals(StepResult.NOT_FOUND, service.scheduleInterview(APP_ID, LocalDate.now(), "HR").result());
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationStatus.class, names = {"SHORTLISTED", "INTERVIEW_SCHEDULED"})
        void addsPendingRoundAndMarksInterviewScheduled(ApplicationStatus status) {
            givenApplication(status);
            when(interviewDAO.addInterview(any())).thenReturn(55);
            LocalDate date = LocalDate.of(2026, 11, 5);

            ScheduleResult result = service.scheduleInterview(APP_ID, date, "Technical");

            assertEquals(StepResult.OK, result.result());
            assertEquals(55, result.interviewId());
            ArgumentCaptor<Interview> saved = ArgumentCaptor.forClass(Interview.class);
            verify(interviewDAO).addInterview(saved.capture());
            assertEquals(InterviewResult.PENDING, saved.getValue().getResult());
            assertEquals("Technical", saved.getValue().getRound());
            assertEquals(date, saved.getValue().getInterviewDate());
            verify(applicationDAO).updateStatus(APP_ID, ApplicationStatus.INTERVIEW_SCHEDULED);
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationStatus.class, names = {"APPLIED", "SELECTED", "REJECTED"})
        void refusedForAppliedAndDecidedApplications(ApplicationStatus status) {
            givenApplication(status);
            assertEquals(StepResult.WRONG_STATUS, service.scheduleInterview(APP_ID, LocalDate.now(), "HR").result());
            verify(interviewDAO, never()).addInterview(any());
            verify(applicationDAO, never()).updateStatus(anyInt(), any());
        }
    }

    @Nested
    @DisplayName("recordInterviewResult")
    class RecordResult {

        private static final int INTERVIEW_ID = 7;

        private void givenInterview() {
            Interview i = new Interview(APP_ID, LocalDate.now(), "HR", InterviewResult.PENDING, "");
            i.setInterviewId(INTERVIEW_ID);
            when(interviewDAO.getInterviewById(INTERVIEW_ID)).thenReturn(i);
        }

        @Test
        void interviewNotFound() {
            assertEquals(StepResult.NOT_FOUND, service.recordInterviewResult(INTERVIEW_ID, InterviewResult.PASS, ""));
        }

        @Test
        void pendingIsNotAValidResult() {
            givenInterview();
            givenApplication(ApplicationStatus.INTERVIEW_SCHEDULED);
            assertEquals(StepResult.INVALID_RESULT,
                    service.recordInterviewResult(INTERVIEW_ID, InterviewResult.PENDING, ""));
            verify(interviewDAO, never()).updateResult(anyInt(), any(), any());
        }

        @Test
        void recordsPassOrFailWhileInterviewing() {
            givenInterview();
            givenApplication(ApplicationStatus.INTERVIEW_SCHEDULED);
            when(interviewDAO.updateResult(INTERVIEW_ID, InterviewResult.FAIL, "weak")).thenReturn(true);

            assertEquals(StepResult.OK, service.recordInterviewResult(INTERVIEW_ID, InterviewResult.FAIL, "weak"));
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationStatus.class, names = {"SELECTED", "REJECTED"})
        void decidedApplicationsAreFrozen(ApplicationStatus status) {
            givenInterview();
            givenApplication(status);
            assertEquals(StepResult.WRONG_STATUS, service.recordInterviewResult(INTERVIEW_ID, InterviewResult.PASS, ""));
            verify(interviewDAO, never()).updateResult(anyInt(), any(), any());
        }
    }

    // ---------------- finalize ----------------

    @Nested
    @DisplayName("finalizeSelection")
    class Finalize {

        @Test
        void notFound() {
            FinalizeResult r = service.finalizeSelection(APP_ID);
            assertEquals(FinalizeOutcome.NOT_FOUND, r.outcome());
            assertNull(r.status());
        }

        @Test
        void anyFailGivesRejected() {
            givenApplication(ApplicationStatus.INTERVIEW_SCHEDULED);
            givenRounds(InterviewResult.PASS, InterviewResult.FAIL, InterviewResult.PENDING);

            FinalizeResult r = service.finalizeSelection(APP_ID);

            assertEquals(FinalizeOutcome.REJECTED, r.outcome());
            assertEquals(ApplicationStatus.REJECTED, r.status());
            verify(applicationDAO).updateStatus(APP_ID, ApplicationStatus.REJECTED);
        }

        @Test
        void anyPendingLeavesStatusUnchanged() {
            givenApplication(ApplicationStatus.INTERVIEW_SCHEDULED);
            givenRounds(InterviewResult.PASS, InterviewResult.PENDING);

            FinalizeResult r = service.finalizeSelection(APP_ID);

            assertEquals(FinalizeOutcome.ROUNDS_PENDING, r.outcome());
            assertEquals(ApplicationStatus.INTERVIEW_SCHEDULED, r.status());
            verify(applicationDAO, never()).updateStatus(anyInt(), any());
        }

        @Test
        void noRoundsLeavesStatusUnchanged() {
            givenApplication(ApplicationStatus.SHORTLISTED);
            givenRounds();

            FinalizeResult r = service.finalizeSelection(APP_ID);

            assertEquals(FinalizeOutcome.NO_ROUNDS, r.outcome());
            assertEquals(ApplicationStatus.SHORTLISTED, r.status());
            verify(applicationDAO, never()).updateStatus(anyInt(), any());
        }

        @Test
        void allPassGivesSelected() {
            givenApplication(ApplicationStatus.INTERVIEW_SCHEDULED);
            givenRounds(InterviewResult.PASS, InterviewResult.PASS);

            FinalizeResult r = service.finalizeSelection(APP_ID);

            assertEquals(FinalizeOutcome.SELECTED, r.outcome());
            verify(applicationDAO).updateStatus(APP_ID, ApplicationStatus.SELECTED);
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationStatus.class, names = {"SELECTED", "REJECTED"})
        void decidedApplicationsNeverMoveBackwards(ApplicationStatus status) {
            givenApplication(status);
            // Even with rounds that would point the other way, the decision stands.
            givenRounds(status == ApplicationStatus.SELECTED ? InterviewResult.FAIL : InterviewResult.PASS);

            FinalizeResult r = service.finalizeSelection(APP_ID);

            assertEquals(FinalizeOutcome.ALREADY_DECIDED, r.outcome());
            assertEquals(status, r.status());
            verify(applicationDAO, never()).updateStatus(anyInt(), any());
        }
    }
}
