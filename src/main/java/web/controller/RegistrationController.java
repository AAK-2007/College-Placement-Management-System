package web.controller;

import dao.CompanyDAO;
import dao.StudentDAO;
import dao.UserDAO;
import model.Company;
import model.Student;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import util.FormValidator;
import web.form.CompanyRegistrationForm;
import web.form.StudentRegistrationForm;
import web.security.AppUser;
import web.security.AppUserDetailsService;
import web.security.Role;

import java.util.regex.Pattern;

/**
 * Public sign-up pages. A student signs up with their student profile; a company with its
 * company details. Each creates the record and a login account linked to it.
 */
@Controller
@RequestMapping("/register")
public class RegistrationController {

    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{3,50}");

    private final StudentDAO studentDAO;
    private final CompanyDAO companyDAO;
    private final UserDAO userDAO;
    private final PasswordEncoder passwordEncoder;
    private final AppUserDetailsService users;

    public RegistrationController(StudentDAO studentDAO, CompanyDAO companyDAO, UserDAO userDAO,
                                  PasswordEncoder passwordEncoder, AppUserDetailsService users) {
        this.studentDAO = studentDAO;
        this.companyDAO = companyDAO;
        this.userDAO = userDAO;
        this.passwordEncoder = passwordEncoder;
        this.users = users;
    }

    // ---------------- Student ----------------

    @GetMapping("/student")
    public String studentForm(@AuthenticationPrincipal AppUser me, Model model) {
        if (me != null) {
            return "redirect:/";
        }
        model.addAttribute("form", new StudentRegistrationForm());
        return "register/student";
    }

    @PostMapping("/student")
    public String registerStudent(@AuthenticationPrincipal AppUser me,
                                  @ModelAttribute("form") StudentRegistrationForm form,
                                  Model model, RedirectAttributes redirect) {
        if (me != null) {
            return "redirect:/";
        }
        FormValidator v = new FormValidator();
        String username = checkAccount(v, form.getUsername(), form.getPassword(), form.getConfirmPassword());

        // Same rules as the officer's "Add student" form.
        String reg = v.requiredText("registerNumber", form.getRegisterNumber(), 20);
        String name = v.requiredText("name", form.getName(), 100);
        String dept = v.requiredText("department", form.getDepartment(), 50);
        Double cgpa = v.decimalInRange("cgpa", form.getCgpa(), 0, 10);
        Integer backlogs = v.intInRange("backlogs", form.getBacklogs(), 0, Integer.MAX_VALUE);
        String skills = v.optionalText("skills", form.getSkills(), 255);
        String email = v.optionalText("email", form.getEmail(), 100);
        String phone = v.optionalText("phone", form.getPhone(), 20);
        if (reg != null && studentDAO.existsByRegisterNumber(reg)) {
            v.reject("registerNumber", "A student with register number " + reg
                    + " already exists. Ask the placement officer if this is you.");
        }
        if (v.hasErrors()) {
            return showErrors(model, v, "register/student");
        }

        int studentId = studentDAO.addStudent(new Student(reg, name, StudentController.normalizeDepartment(dept),
                cgpa, backlogs, skills, email, phone));
        try {
            userDAO.addUser(username, passwordEncoder.encode(form.getPassword()), Role.STUDENT.name(), studentId, null);
        } catch (RuntimeException e) {
            studentDAO.deleteStudent(studentId); // do not leave a student without the account that was asked for
            throw e;
        }
        redirect.addFlashAttribute("success", "Your student account was created. Please log in.");
        return "redirect:/login";
    }

    // ---------------- Company ----------------

    @GetMapping("/company")
    public String companyForm(@AuthenticationPrincipal AppUser me, Model model) {
        if (me != null) {
            return "redirect:/";
        }
        model.addAttribute("form", new CompanyRegistrationForm());
        return "register/company";
    }

    @PostMapping("/company")
    public String registerCompany(@AuthenticationPrincipal AppUser me,
                                  @ModelAttribute("form") CompanyRegistrationForm form,
                                  Model model, RedirectAttributes redirect) {
        if (me != null) {
            return "redirect:/";
        }
        FormValidator v = new FormValidator();
        String username = checkAccount(v, form.getUsername(), form.getPassword(), form.getConfirmPassword());
        String name = v.requiredText("name", form.getName(), 100);
        String sector = v.optionalText("sector", form.getSector(), 100);
        String email = v.optionalText("contactEmail", form.getContactEmail(), 100);
        String phone = v.optionalText("contactPhone", form.getContactPhone(), 20);
        if (v.hasErrors()) {
            return showErrors(model, v, "register/company");
        }

        int companyId = companyDAO.addCompany(new Company(name, sector, email, phone));
        try {
            userDAO.addUser(username, passwordEncoder.encode(form.getPassword()), Role.COMPANY.name(), null, companyId);
        } catch (RuntimeException e) {
            companyDAO.deleteCompany(companyId);
            throw e;
        }
        redirect.addFlashAttribute("success", "Your company account was created. Please log in.");
        return "redirect:/login";
    }

    // ---------------- shared ----------------

    /** Checks the user name and password fields; returns the trimmed user name (or null if invalid). */
    private String checkAccount(FormValidator v, String rawUsername, String password, String confirm) {
        String username = v.requiredText("username", rawUsername, 50);
        if (username != null) {
            if (!USERNAME.matcher(username).matches()) {
                v.reject("username", "Use 3 to 50 letters, digits, dots, dashes or underscores.");
            } else if (users.isOfficerUsername(username) || userDAO.existsByUsername(username)) {
                v.reject("username", "That user name is already taken.");
            }
        }
        if (password == null || password.length() < 8) {
            v.reject("password", "Use at least 8 characters.");
        } else if (password.length() > 72) {
            v.reject("password", "Use at most 72 characters.");
        } else if (!password.equals(confirm)) {
            v.reject("confirmPassword", "The two passwords do not match.");
        }
        return username;
    }

    private static String showErrors(Model model, FormValidator v, String view) {
        model.addAttribute("errors", v.getErrors());
        model.addAttribute("error", "Please fix the highlighted fields. Your account was not created.");
        return view;
    }
}
