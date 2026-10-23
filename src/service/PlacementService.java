package service;

import dao.ApplicationDAO;
import dao.InterviewDAO;
import dao.JobDAO;
import dao.StudentDAO;
import model.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Orchestrates the placement workflow: submitting applications, recording
 * interview rounds, and deciding the final selection outcome. This class
 * is where the multiple DAOs are combined into meaningful business
 * operations - the "processing and decision-making" the abstract calls for.
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
     * a new application. This is the single entry point students use to
     * apply - it guarantees no ineligible or duplicate application can
     * ever reach the database.
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
        applicationDAO.addApplication(application);
        return ApplyResult.SUCCESS;
    }

    /** Moves an application to SHORTLISTED, typically after a resume/aptitude screen. */
    public boolean shortlist(int applicationId) {
        return applicationDAO.updateStatus(applicationId, ApplicationStatus.SHORTLISTED);
    }

    /** Records a new interview round for an application and marks it as scheduled/in-progress. */
    public int scheduleInterview(int applicationId, LocalDate date, String round) {
        applicationDAO.updateStatus(applicationId, ApplicationStatus.INTERVIEW_SCHEDULED);
        Interview interview = new Interview(applicationId, date, round, InterviewResult.PENDING, "");
        return interviewDAO.addInterview(interview);
    }

    /** Records the result of a specific interview round. */
    public boolean recordInterviewResult(int interviewId, InterviewResult result, String remarks) {
        return interviewDAO.updateResult(interviewId, result, remarks);
    }

    /**
     * Determines the final selection outcome for an application: SELECTED
     * only if every recorded interview round for it resulted in PASS and
     * at least one round has been conducted; REJECTED if any round failed.
     * Applications with rounds still pending are left unchanged.
     */
    public ApplicationStatus finalizeSelection(int applicationId) {
        List<Interview> rounds = interviewDAO.getInterviewsByApplication(applicationId);
        if (rounds.isEmpty()) {
            return applicationDAO.getApplicationById(applicationId).getStatus();
        }

        boolean anyFailed = rounds.stream().anyMatch(r -> r.getResult() == InterviewResult.FAIL);
        boolean anyPending = rounds.stream().anyMatch(r -> r.getResult() == InterviewResult.PENDING);

        ApplicationStatus outcome;
        if (anyFailed) {
            outcome = ApplicationStatus.REJECTED;
        } else if (anyPending) {
            return applicationDAO.getApplicationById(applicationId).getStatus(); // wait for remaining rounds
        } else {
            outcome = ApplicationStatus.SELECTED;
        }
        applicationDAO.updateStatus(applicationId, outcome);
        return outcome;
    }
}
