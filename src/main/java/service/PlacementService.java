package service;

import dao.ApplicationDAO;
import dao.InterviewDAO;
import dao.JobDAO;
import dao.StudentDAO;
import model.*;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.util.List;

/**
 * Orchestrates the placement workflow: submitting applications, recording
 * interview rounds, and deciding the final selection outcome. This class
 * is where the multiple DAOs are combined into meaningful business
 * operations.
 * <p>
 * Web version: apply, shortlist and scheduleInterview are unchanged from the
 * console. recordInterviewResult and finalizeSelection gained guards so a
 * decided (SELECTED/REJECTED) application can never be changed again, and
 * they return results the pages can turn into precise messages.
 */
public class PlacementService {

    private final StudentDAO studentDAO;
    private final JobDAO jobDAO;
    private final ApplicationDAO applicationDAO;
    private final InterviewDAO interviewDAO;
    private final EligibilityService eligibilityService;

    public PlacementService(StudentDAO studentDAO, JobDAO jobDAO, ApplicationDAO applicationDAO,
                            InterviewDAO interviewDAO, EligibilityService eligibilityService) {
        this.studentDAO = studentDAO;
        this.jobDAO = jobDAO;
        this.applicationDAO = applicationDAO;
        this.interviewDAO = interviewDAO;
        this.eligibilityService = eligibilityService;
    }

    /** Result of an attempted application, so the caller can show a precise message. */
    public enum ApplyResult {
        SUCCESS, ALREADY_APPLIED, NOT_ELIGIBLE, STUDENT_NOT_FOUND, JOB_NOT_FOUND
    }

    /**
     * Validates eligibility and duplicate-application rules, then records
     * a new application. This is the single entry point for applying - it
     * guarantees no ineligible or duplicate application can ever reach the
     * database.
     */
    public ApplyResult apply(int studentId, int jobId) {
        Student student = studentDAO.getStudentById(studentId);
        if (student == null) {
            return ApplyResult.STUDENT_NOT_FOUND;
        }
        Job job = jobDAO.getJobById(jobId);
        if (job == null) {
            return ApplyResult.JOB_NOT_FOUND;
        }
        if (applicationDAO.hasApplied(studentId, jobId)) {
            return ApplyResult.ALREADY_APPLIED;
        }
        if (!eligibilityService.isEligible(student, job)) {
            return ApplyResult.NOT_ELIGIBLE;
        }
        Application application = new Application(studentId, jobId, LocalDate.now(), ApplicationStatus.APPLIED);
        try {
            applicationDAO.addApplication(application);
        } catch (RuntimeException e) {
            // Two requests at the same moment: the UNIQUE(student_id, job_id) key stopped the second one.
            if (e.getCause() instanceof SQLIntegrityConstraintViolationException) {
                return ApplyResult.ALREADY_APPLIED;
            }
            throw e;
        }
        return ApplyResult.SUCCESS;
    }

    /** Outcome of a workflow step on an existing application, so the caller can show a precise message. */
    public enum StepResult {
        OK, NOT_FOUND, WRONG_STATUS, INVALID_RESULT
    }

    /** Result of scheduling an interview: the outcome plus the new interview id (only when the outcome is OK). */
    public record ScheduleResult(StepResult result, int interviewId) {
    }

    /**
     * Moves an application to SHORTLISTED, typically after a resume/aptitude screen.
     * Only an application that is still APPLIED can be shortlisted, so a decided
     * application (SELECTED/REJECTED) can never be moved backwards.
     */
    public StepResult shortlist(int applicationId) {
        Application application = applicationDAO.getApplicationById(applicationId);
        if (application == null) {
            return StepResult.NOT_FOUND;
        }
        if (application.getStatus() != ApplicationStatus.APPLIED) {
            return StepResult.WRONG_STATUS;
        }
        applicationDAO.updateStatus(applicationId, ApplicationStatus.SHORTLISTED);
        return StepResult.OK;
    }

    /**
     * Records a new interview round for an application and marks it as scheduled.
     * The application must exist and must be SHORTLISTED (first round) or
     * INTERVIEW_SCHEDULED (further rounds); a SELECTED or REJECTED application
     * cannot get new rounds, and an APPLIED one has to be shortlisted first.
     */
    public ScheduleResult scheduleInterview(int applicationId, LocalDate date, String round) {
        Application application = applicationDAO.getApplicationById(applicationId);
        if (application == null) {
            return new ScheduleResult(StepResult.NOT_FOUND, -1);
        }
        ApplicationStatus status = application.getStatus();
        if (status != ApplicationStatus.SHORTLISTED && status != ApplicationStatus.INTERVIEW_SCHEDULED) {
            return new ScheduleResult(StepResult.WRONG_STATUS, -1);
        }
        Interview interview = new Interview(applicationId, date, round, InterviewResult.PENDING, "");
        int interviewId = interviewDAO.addInterview(interview);
        applicationDAO.updateStatus(applicationId, ApplicationStatus.INTERVIEW_SCHEDULED);
        return new ScheduleResult(StepResult.OK, interviewId);
    }

    /**
     * Records the result of a specific interview round.
     * <ul>
     *   <li>NOT_FOUND - no such interview round;</li>
     *   <li>INVALID_RESULT - only PASS or FAIL can be recorded;</li>
     *   <li>WRONG_STATUS - the application is already decided (SELECTED/REJECTED),
     *       so its rounds are frozen and the decision cannot be undone.</li>
     * </ul>
     */
    public StepResult recordInterviewResult(int interviewId, InterviewResult result, String remarks) {
        Interview interview = interviewDAO.getInterviewById(interviewId);
        if (interview == null) {
            return StepResult.NOT_FOUND;
        }
        if (result != InterviewResult.PASS && result != InterviewResult.FAIL) {
            return StepResult.INVALID_RESULT;
        }
        Application application = applicationDAO.getApplicationById(interview.getApplicationId());
        if (application == null) {
            return StepResult.NOT_FOUND;
        }
        if (application.getStatus() != ApplicationStatus.INTERVIEW_SCHEDULED) {
            return StepResult.WRONG_STATUS;
        }
        return interviewDAO.updateResult(interviewId, result, remarks) ? StepResult.OK : StepResult.NOT_FOUND;
    }

    /** Why finalizeSelection did or did not change the status. */
    public enum FinalizeOutcome {
        SELECTED,          // every round PASS -> status set to SELECTED
        REJECTED,          // a round FAILED -> status set to REJECTED
        ROUNDS_PENDING,    // some rounds still PENDING -> unchanged
        NO_ROUNDS,         // no rounds recorded -> unchanged
        ALREADY_DECIDED,   // already SELECTED/REJECTED -> unchanged, never moves backwards
        NOT_INTERVIEWING,  // APPLIED or SHORTLISTED -> unchanged (schedule a round first)
        NOT_FOUND
    }

    /** Result of finalizeSelection: why, and the application's status afterwards (null if not found). */
    public record FinalizeResult(FinalizeOutcome outcome, ApplicationStatus status) {
    }

    /**
     * Determines the final selection outcome for an application: SELECTED
     * only if every recorded interview round resulted in PASS and at least
     * one round has been conducted; REJECTED if any round failed.
     * Applications with rounds still pending, or with no rounds, are left
     * unchanged. Only an INTERVIEW_SCHEDULED application can be decided, so a
     * SELECTED or REJECTED application never changes again.
     */
    public FinalizeResult finalizeSelection(int applicationId) {
        Application application = applicationDAO.getApplicationById(applicationId);
        if (application == null) {
            return new FinalizeResult(FinalizeOutcome.NOT_FOUND, null);
        }
        ApplicationStatus current = application.getStatus();
        if (current == ApplicationStatus.SELECTED || current == ApplicationStatus.REJECTED) {
            return new FinalizeResult(FinalizeOutcome.ALREADY_DECIDED, current);
        }

        List<Interview> rounds = interviewDAO.getInterviewsByApplication(applicationId);
        if (rounds.isEmpty()) {
            return new FinalizeResult(FinalizeOutcome.NO_ROUNDS, current);
        }
        if (current != ApplicationStatus.INTERVIEW_SCHEDULED) {
            return new FinalizeResult(FinalizeOutcome.NOT_INTERVIEWING, current);
        }

        // One pass over the rounds: any FAIL decides the outcome; otherwise note any PENDING.
        boolean anyFailed = false;
        boolean anyPending = false;
        for (Interview round : rounds) {
            if (round.getResult() == InterviewResult.FAIL) {
                anyFailed = true;
                break;
            }
            if (round.getResult() == InterviewResult.PENDING) {
                anyPending = true;
            }
        }

        if (anyFailed) {
            applicationDAO.updateStatus(applicationId, ApplicationStatus.REJECTED);
            return new FinalizeResult(FinalizeOutcome.REJECTED, ApplicationStatus.REJECTED);
        }
        if (anyPending) {
            return new FinalizeResult(FinalizeOutcome.ROUNDS_PENDING, current); // wait for remaining rounds
        }
        applicationDAO.updateStatus(applicationId, ApplicationStatus.SELECTED);
        return new FinalizeResult(FinalizeOutcome.SELECTED, ApplicationStatus.SELECTED);
    }
}
