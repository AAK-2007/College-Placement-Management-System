package web.controller;

import dao.ApplicationDAO;
import dao.ApplicationDAO.ApplicationView;
import dao.CompanyDAO;
import dao.JobDAO;
import dao.StudentDAO;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.PlacementService;
import web.security.Access;
import web.security.AppUser;

/**
 * Applications.
 * Officer: every application, apply for any student, shortlist.
 * Student: only their own applications; applies only for themselves.
 * Company: only applications to its own jobs; shortlists them.
 */
@Controller
@RequestMapping("/applications")
public class ApplicationController {

    private final PlacementService placementService;
    private final ApplicationDAO applicationDAO;
    private final StudentDAO studentDAO;
    private final JobDAO jobDAO;
    private final CompanyDAO companyDAO;

    public ApplicationController(PlacementService placementService, ApplicationDAO applicationDAO,
                                 StudentDAO studentDAO, JobDAO jobDAO, CompanyDAO companyDAO) {
        this.placementService = placementService;
        this.applicationDAO = applicationDAO;
        this.studentDAO = studentDAO;
        this.jobDAO = jobDAO;
        this.companyDAO = companyDAO;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUser me,
                       @RequestParam(required = false) Integer studentId,
                       @RequestParam(required = false) Integer jobId, Model model) {
        addDropdowns(me, model, !me.isStudent()); // students have no filter dropdowns on this page
        if (me.isStudent()) {
            studentId = me.getStudentId(); // a student only ever sees their own applications
        }
        Integer companyId = me.isCompany() ? me.getCompanyId() : null;
        model.addAttribute("selectedStudentId", me.isOfficer() ? studentId : null);
        model.addAttribute("selectedJobId", jobId);
        model.addAttribute("applications", applicationDAO.getApplicationViews(studentId, jobId, companyId));
        return "applications/list";
    }

    @GetMapping("/apply")
    public String applyForm(@AuthenticationPrincipal AppUser me,
                            @RequestParam(required = false) Integer studentId,
                            @RequestParam(required = false) Integer jobId, Model model) {
        addDropdowns(me, model, true);
        model.addAttribute("selectedStudentId", me.isStudent() ? me.getStudentId() : studentId);
        model.addAttribute("selectedJobId", jobId);
        return "applications/apply";
    }

    @PostMapping("/apply")
    public String apply(@AuthenticationPrincipal AppUser me,
                        @RequestParam(required = false) Integer studentId,
                        @RequestParam(required = false) Integer jobId, RedirectAttributes redirect) {
        if (me.isStudent()) {
            studentId = me.getStudentId(); // students can only apply for themselves
        }
        if (studentId == null || jobId == null) {
            redirect.addFlashAttribute("error", me.isStudent() ? "Choose a job." : "Choose both a student and a job.");
            return "redirect:/applications/apply";
        }
        PlacementService.ApplyResult result = placementService.apply(studentId, jobId);
        if (me.isOfficer()) {
            redirect.addAttribute("studentId", studentId);
        }
        switch (result) {
            case SUCCESS -> {
                redirect.addFlashAttribute("success", "Application submitted successfully.");
                return "redirect:/applications";
            }
            case ALREADY_APPLIED -> {
                redirect.addAttribute("jobId", jobId);
                redirect.addFlashAttribute("error", me.isStudent()
                        ? "You have already applied to this job."
                        : "This student has already applied to this job.");
                return "redirect:/applications/apply";
            }
            case NOT_ELIGIBLE -> {
                // Show exactly which criteria fail.
                redirect.addAttribute("jobId", jobId);
                redirect.addFlashAttribute("error",
                        "Not eligible for this job, so no application was made. The failing criteria are shown below.");
                return "redirect:/eligibility/explain";
            }
            case STUDENT_NOT_FOUND -> {
                redirect.addFlashAttribute("error", "Student not found.");
                return "redirect:/applications/apply";
            }
            default -> { // JOB_NOT_FOUND
                redirect.addFlashAttribute("error", "Job not found.");
                return "redirect:/applications/apply";
            }
        }
    }

    /** Shortlist button (officer, or the company that posted the job). */
    @PostMapping("/{id}/shortlist")
    public String shortlist(@AuthenticationPrincipal AppUser me, @PathVariable int id,
                            @RequestParam(required = false) Integer studentId,
                            @RequestParam(required = false) Integer jobId, RedirectAttributes redirect) {
        ApplicationView application = applicationDAO.getApplicationView(id);
        if (application == null) {
            redirect.addFlashAttribute("error", "Application not found.");
            return "redirect:/applications";
        }
        Access.requireOwnApplication(me, application);

        PlacementService.StepResult result = placementService.shortlist(id);
        switch (result) {
            case OK -> redirect.addFlashAttribute("success", "Application #" + id + " shortlisted.");
            case NOT_FOUND -> redirect.addFlashAttribute("error", "Application not found.");
            default -> redirect.addFlashAttribute("error",
                    "Only an application with status APPLIED can be shortlisted.");
        }
        if (studentId != null && me.isOfficer()) {
            redirect.addAttribute("studentId", studentId);
        }
        if (jobId != null) {
            redirect.addAttribute("jobId", jobId);
        }
        return "redirect:/applications";
    }

    /**
     * Dropdown contents: the officer sees every student and job; a company only its own jobs.
     * The job list is only loaded when the page shows a job dropdown.
     */
    private void addDropdowns(AppUser me, Model model, boolean withJobs) {
        model.addAttribute("companyNames", companyDAO.getCompanyNames());
        if (me.isOfficer()) {
            model.addAttribute("students", studentDAO.getAllStudents());
        }
        if (withJobs) {
            model.addAttribute("jobs", me.isCompany() ? jobDAO.getJobsByCompany(me.getCompanyId()) : jobDAO.getAllJobs());
        }
    }
}
