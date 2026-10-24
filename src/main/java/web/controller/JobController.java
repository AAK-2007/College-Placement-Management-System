package web.controller;

import dao.ApplicationDAO;
import dao.CompanyDAO;
import dao.JobDAO;
import model.Company;
import model.Job;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import util.FormValidator;
import web.form.JobForm;
import web.security.AppUser;

import java.util.*;

/**
 * Jobs.
 * Officer: all jobs (or one company's), add a job for any company.
 * Student: all jobs, read only, with links to check eligibility and apply.
 * Company: only its own jobs; posts jobs for itself.
 */
@Controller
@RequestMapping("/jobs")
public class JobController {

    private final JobDAO jobDAO;
    private final CompanyDAO companyDAO;
    private final ApplicationDAO applicationDAO;

    public JobController(JobDAO jobDAO, CompanyDAO companyDAO, ApplicationDAO applicationDAO) {
        this.jobDAO = jobDAO;
        this.companyDAO = companyDAO;
        this.applicationDAO = applicationDAO;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal AppUser me,
                       @RequestParam(required = false) Integer companyId, Model model) {
        if (me.isCompany()) {
            companyId = me.getCompanyId(); // a company only sees its own jobs
        }
        List<Company> companies = companyDAO.getAllCompanies();
        model.addAttribute("companies", companies);
        model.addAttribute("companyNames", companyNames(companies));
        model.addAttribute("selectedCompanyId", companyId);
        model.addAttribute("jobs", companyId == null ? jobDAO.getAllJobs() : jobDAO.getJobsByCompany(companyId));
        model.addAttribute("appliedJobIds", me.isStudent()
                ? HomeController.appliedJobIds(applicationDAO.getApplicationViews(me.getStudentId(), null, null))
                : Set.of());
        return "jobs/list";
    }

    @GetMapping("/add")
    public String addForm(@AuthenticationPrincipal AppUser me,
                          @RequestParam(required = false) Integer companyId, Model model) {
        JobForm form = new JobForm();
        if (me.isCompany()) {
            companyId = me.getCompanyId();
        }
        if (companyId != null) {
            form.setCompanyId(String.valueOf(companyId));
        }
        model.addAttribute("form", form);
        addCompanies(me, model);
        return "jobs/add";
    }

    @PostMapping("/add")
    public String add(@AuthenticationPrincipal AppUser me, @ModelAttribute("form") JobForm form,
                      Model model, RedirectAttributes redirect) {
        if (me.isCompany()) {
            form.setCompanyId(String.valueOf(me.getCompanyId())); // a company can only post for itself
        }
        FormValidator v = new FormValidator();
        Integer companyId = v.intInRange("companyId", form.getCompanyId(), 1, Integer.MAX_VALUE);
        if (companyId != null && companyDAO.getCompanyById(companyId) == null) {
            v.reject("companyId", "No such company. Add the company first.");
        }
        String role = v.requiredText("role", form.getRole(), 100);
        Double minCgpa = v.decimalInRange("minCgpa", form.getMinCgpa(), 0, 10);
        String depts = v.requiredText("eligibleDepartments", form.getEligibleDepartments(), 255);
        Integer maxBacklogs = v.intInRange("maxBacklogs", form.getMaxBacklogs(), 0, Integer.MAX_VALUE);
        String skills = v.optionalText("requiredSkills", form.getRequiredSkills(), 255);
        Double packageLpa = v.decimalInRange("packageLpa", form.getPackageLpa(), 0, 1000);

        String normalizedDepts = depts == null ? null : normalizeDepartments(depts);
        if (depts != null && normalizedDepts.isEmpty()) {
            v.reject("eligibleDepartments", "Enter at least one department, or ALL.");
        }

        if (v.hasErrors()) {
            addCompanies(me, model);
            model.addAttribute("errors", v.getErrors());
            model.addAttribute("error", "Please fix the highlighted fields. Nothing was saved.");
            return "jobs/add";
        }

        Job job = new Job(companyId, role, minCgpa, normalizedDepts, maxBacklogs, skills, packageLpa);
        int id = jobDAO.addJob(job);
        redirect.addFlashAttribute("success", "Job " + role + " added with ID " + id + ".");
        return "redirect:/jobs";
    }

    /**
     * Stores departments as an upper-case list without extra spaces, e.g. " cse, it " -> "CSE,IT".
     * If ALL appears anywhere the job is open to every department, so it is stored as just "ALL"
     * (the SQL eligibility filter only recognises ALL when it is the whole value).
     */
    static String normalizeDepartments(String raw) {
        List<String> parts = new ArrayList<>();
        for (String p : raw.split(",")) {
            String d = p.trim().toUpperCase();
            if (d.equals("ALL")) {
                return "ALL";
            }
            if (!d.isEmpty() && !parts.contains(d)) {
                parts.add(d);
            }
        }
        return String.join(",", parts);
    }

    /** Officer chooses from every company; a company only sees itself. */
    private void addCompanies(AppUser me, Model model) {
        if (me.isCompany()) {
            Company own = companyDAO.getCompanyById(me.getCompanyId());
            model.addAttribute("companies", own == null ? List.of() : List.of(own));
        } else {
            model.addAttribute("companies", companyDAO.getAllCompanies());
        }
    }

    private static Map<Integer, String> companyNames(List<Company> companies) {
        Map<Integer, String> names = new HashMap<>();
        for (Company c : companies) {
            names.put(c.getCompanyId(), c.getName());
        }
        return names;
    }
}
