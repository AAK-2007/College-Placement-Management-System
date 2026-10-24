package web.controller;

import dao.ApplicationDAO;
import dao.CompanyDAO;
import dao.JobDAO;
import dao.StudentDAO;
import model.Job;
import model.Student;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import service.EligibilityService;
import web.security.AppUser;

/**
 * Eligibility analysis.
 * Officer: eligible students for a job, eligible jobs for any student, any explanation.
 * Student: only their own eligible jobs and their own explanation (the student is always themselves).
 */
@Controller
@RequestMapping("/eligibility")
public class EligibilityController {

    private final EligibilityService eligibilityService;
    private final StudentDAO studentDAO;
    private final JobDAO jobDAO;
    private final CompanyDAO companyDAO;
    private final ApplicationDAO applicationDAO;

    public EligibilityController(EligibilityService eligibilityService, StudentDAO studentDAO,
                                 JobDAO jobDAO, CompanyDAO companyDAO, ApplicationDAO applicationDAO) {
        this.eligibilityService = eligibilityService;
        this.studentDAO = studentDAO;
        this.jobDAO = jobDAO;
        this.companyDAO = companyDAO;
        this.applicationDAO = applicationDAO;
    }

    @GetMapping
    public String index(@AuthenticationPrincipal AppUser me) {
        return me.isStudent() ? "redirect:/eligibility/student" : "redirect:/eligibility/job";
    }

    /** Officer only (see SecurityConfig): pick a job -> eligible students, best skill match first. */
    @GetMapping("/job")
    public String studentsForJob(@AuthenticationPrincipal AppUser me,
                                 @RequestParam(required = false) Integer jobId, Model model) {
        addDropdowns(me, model, false, true);
        model.addAttribute("selectedJobId", jobId);
        if (jobId != null) {
            Job job = jobDAO.getJobById(jobId);
            if (job == null) {
                model.addAttribute("error", "Job not found.");
            } else {
                model.addAttribute("job", job);
                model.addAttribute("results", eligibilityService.getEligibleStudentsForJob(job));
            }
        }
        return "eligibility/job";
    }

    /** Pick a student -> eligible jobs, best skill match first. A student always sees their own. */
    @GetMapping("/student")
    public String jobsForStudent(@AuthenticationPrincipal AppUser me,
                                 @RequestParam(required = false) Integer studentId, Model model) {
        if (me.isStudent()) {
            studentId = me.getStudentId();
        }
        addDropdowns(me, model, true, false);
        model.addAttribute("selectedStudentId", studentId);
        if (studentId != null) {
            Student student = studentDAO.getStudentById(studentId);
            if (student == null) {
                model.addAttribute("error", "Student not found.");
            } else {
                model.addAttribute("student", student);
                model.addAttribute("results", eligibilityService.getEligibleJobsForStudent(student));
                model.addAttribute("appliedJobIds",
                        HomeController.appliedJobIds(applicationDAO.getApplicationViews(studentId, null, null)));
            }
        }
        return "eligibility/student";
    }

    /** Pick a student and a job -> PASS/FAIL for each criterion. A student is always themselves. */
    @GetMapping("/explain")
    public String explain(@AuthenticationPrincipal AppUser me,
                          @RequestParam(required = false) Integer studentId,
                          @RequestParam(required = false) Integer jobId, Model model) {
        if (me.isStudent()) {
            studentId = me.getStudentId();
        }
        addDropdowns(me, model, true, true);
        model.addAttribute("selectedStudentId", studentId);
        model.addAttribute("selectedJobId", jobId);
        if (studentId != null && jobId != null) {
            Student student = studentDAO.getStudentById(studentId);
            Job job = jobDAO.getJobById(jobId);
            if (student == null || job == null) {
                model.addAttribute("error", "Student or job not found.");
            } else {
                model.addAttribute("report", eligibilityService.explainEligibility(student, job));
            }
        } else if (me.isOfficer() && (studentId != null || jobId != null)) {
            model.addAttribute("error", "Choose both a student and a job.");
        }
        return "eligibility/explain";
    }

    /**
     * Company names for every page; the student dropdown (officer only) and the job dropdown
     * are loaded only on the pages that show them.
     */
    private void addDropdowns(AppUser me, Model model, boolean withStudents, boolean withJobs) {
        model.addAttribute("companyNames", companyDAO.getCompanyNames());
        if (withStudents && me.isOfficer()) {
            model.addAttribute("students", studentDAO.getAllStudents());
        }
        if (withJobs) {
            model.addAttribute("jobs", jobDAO.getAllJobs());
        }
    }
}
