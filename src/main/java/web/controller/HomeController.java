package web.controller;

import dao.ApplicationDAO;
import dao.ApplicationDAO.ApplicationView;
import dao.CompanyDAO;
import dao.JobDAO;
import dao.StudentDAO;
import model.ApplicationStatus;
import model.Company;
import model.Student;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import service.EligibilityService;
import service.ReportService;
import web.GlobalExceptionHandler;
import web.security.Access;
import web.security.AppUser;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The home page. Visitors see the public welcome page with log-in and sign-up links;
 * after logging in each role gets its own dashboard.
 */
@Controller
public class HomeController {

    private final ReportService reportService;
    private final EligibilityService eligibilityService;
    private final StudentDAO studentDAO;
    private final CompanyDAO companyDAO;
    private final JobDAO jobDAO;
    private final ApplicationDAO applicationDAO;

    public HomeController(ReportService reportService, EligibilityService eligibilityService, StudentDAO studentDAO,
                          CompanyDAO companyDAO, JobDAO jobDAO, ApplicationDAO applicationDAO) {
        this.reportService = reportService;
        this.eligibilityService = eligibilityService;
        this.studentDAO = studentDAO;
        this.companyDAO = companyDAO;
        this.jobDAO = jobDAO;
        this.applicationDAO = applicationDAO;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AppUser me, Model model) {
        if (me == null) {
            return "home/landing";
        }
        return switch (me.getRole()) {
            case OFFICER -> officer(model);
            case STUDENT -> student(me, model);
            case COMPANY -> company(me, model);
        };
    }

    /** Placement summary report. */
    private String officer(Model model) {
        try {
            model.addAttribute("summary", reportService.getSummary());
        } catch (RuntimeException e) {
            // Show the reason (e.g. wrong DB password) on the dashboard itself instead of an error page.
            model.addAttribute("dbMessage", GlobalExceptionHandler.friendlyMessage(e));
        }
        return "dashboard";
    }

    /** The student's profile, jobs they qualify for, and their applications. */
    private String student(AppUser me, Model model) {
        Student student = studentDAO.getStudentById(me.getStudentId());
        if (student == null) {
            throw Access.denied();
        }
        List<ApplicationView> applications = applicationDAO.getApplicationViews(me.getStudentId(), null, null);
        model.addAttribute("student", student);
        model.addAttribute("eligibleJobs", eligibilityService.getEligibleJobsForStudent(student));
        model.addAttribute("applications", applications);
        model.addAttribute("appliedJobIds", appliedJobIds(applications));
        model.addAttribute("companyNames", companyDAO.getCompanyNames());
        return "home/student";
    }

    /** The company's details, its jobs, and how its applications stand. */
    private String company(AppUser me, Model model) {
        Company company = companyDAO.getCompanyById(me.getCompanyId());
        if (company == null) {
            throw Access.denied();
        }
        List<ApplicationView> applications = applicationDAO.getApplicationViews(null, null, me.getCompanyId());
        // Count per status name (e.g. "APPLIED" -> 3), with every status present.
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        for (ApplicationView a : applications) {
            byStatus.merge(a.status().name(), 1L, Long::sum);
        }
        model.addAttribute("company", company);
        model.addAttribute("jobs", jobDAO.getJobsByCompany(me.getCompanyId()));
        model.addAttribute("applications", applications);
        model.addAttribute("byStatus", byStatus);
        return "home/company";
    }

    /** Jobs this student already applied to, so pages show "Applied" instead of an Apply button. */
    static Set<Integer> appliedJobIds(List<ApplicationView> applications) {
        Set<Integer> ids = new HashSet<>();
        for (ApplicationView a : applications) {
            ids.add(a.jobId());
        }
        return ids;
    }
}
