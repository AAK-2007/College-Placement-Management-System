package web.controller;

import dao.CompanyDAO;
import model.Company;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import util.FormValidator;
import web.form.CompanyForm;

/** Companies: list, add, delete with confirmation. */
@Controller
@RequestMapping("/companies")
public class CompanyController {

    private final CompanyDAO companyDAO;

    public CompanyController(CompanyDAO companyDAO) {
        this.companyDAO = companyDAO;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("companies", companyDAO.getAllCompanies());
        return "companies/list";
    }

    @GetMapping("/add")
    public String addForm(Model model) {
        model.addAttribute("form", new CompanyForm());
        return "companies/add";
    }

    @PostMapping("/add")
    public String add(@ModelAttribute("form") CompanyForm form, Model model, RedirectAttributes redirect) {
        FormValidator v = new FormValidator();
        String name = v.requiredText("name", form.getName(), 100);
        String sector = v.optionalText("sector", form.getSector(), 100);
        String email = v.optionalText("contactEmail", form.getContactEmail(), 100);
        String phone = v.optionalText("contactPhone", form.getContactPhone(), 20);

        if (v.hasErrors()) {
            model.addAttribute("errors", v.getErrors());
            model.addAttribute("error", "Please fix the highlighted fields. Nothing was saved.");
            return "companies/add";
        }
        int id = companyDAO.addCompany(new Company(name, sector, email, phone));
        redirect.addFlashAttribute("success", "Company " + name + " added with ID " + id + ".");
        return "redirect:/companies";
    }

    @GetMapping("/{id}/delete")
    public String confirmDelete(@PathVariable int id, Model model, RedirectAttributes redirect) {
        Company company = companyDAO.getCompanyById(id);
        if (company == null) {
            redirect.addFlashAttribute("error", "Company not found.");
            return "redirect:/companies";
        }
        model.addAttribute("company", company);
        model.addAttribute("jobCount", companyDAO.countJobs(id));
        model.addAttribute("applicationCount", companyDAO.countApplications(id));
        model.addAttribute("interviewCount", companyDAO.countInterviews(id));
        return "companies/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable int id, RedirectAttributes redirect) {
        Company company = companyDAO.getCompanyById(id);
        if (company != null && companyDAO.deleteCompany(id)) {
            redirect.addFlashAttribute("success", "Company " + company.getName()
                    + " and all of its jobs, applications and interviews were deleted.");
        } else {
            redirect.addFlashAttribute("error", "Company not found. Nothing was deleted.");
        }
        return "redirect:/companies";
    }
}
