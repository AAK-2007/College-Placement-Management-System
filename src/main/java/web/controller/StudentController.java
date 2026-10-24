package web.controller;

import dao.StudentDAO;
import model.Student;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import util.FormValidator;
import web.form.StudentForm;
import web.security.Access;
import web.security.AppUser;

/**
 * Students.
 * Officer: list, add, edit (empty field = keep current value), delete with confirmation.
 * Student: only /students/me, their own profile (view and edit). See SecurityConfig.
 */
@Controller
@RequestMapping("/students")
public class StudentController {

    private final StudentDAO studentDAO;

    public StudentController(StudentDAO studentDAO) {
        this.studentDAO = studentDAO;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("students", studentDAO.getAllStudents());
        return "students/list";
    }

    // ---------------- Add ----------------

    @GetMapping("/add")
    public String addForm(Model model) {
        model.addAttribute("form", new StudentForm());
        return "students/add";
    }

    @PostMapping("/add")
    public String add(@ModelAttribute("form") StudentForm form, Model model, RedirectAttributes redirect) {
        FormValidator v = new FormValidator();
        String reg = v.requiredText("registerNumber", form.getRegisterNumber(), 20);
        String name = v.requiredText("name", form.getName(), 100);
        String dept = v.requiredText("department", form.getDepartment(), 50);
        Double cgpa = v.decimalInRange("cgpa", form.getCgpa(), 0, 10);
        Integer backlogs = v.intInRange("backlogs", form.getBacklogs(), 0, Integer.MAX_VALUE);
        String skills = v.optionalText("skills", form.getSkills(), 255);
        String email = v.optionalText("email", form.getEmail(), 100);
        String phone = v.optionalText("phone", form.getPhone(), 20);

        if (reg != null && studentDAO.existsByRegisterNumber(reg)) {
            v.reject("registerNumber", "A student with register number " + reg + " already exists.");
        }
        if (v.hasErrors()) {
            model.addAttribute("errors", v.getErrors());
            model.addAttribute("error", "Please fix the highlighted fields. Nothing was saved.");
            return "students/add";
        }

        Student student = new Student(reg, name, normalizeDepartment(dept), cgpa, backlogs, skills, email, phone);
        int id = studentDAO.addStudent(student);
        redirect.addFlashAttribute("success", "Student " + name + " added with ID " + id + ".");
        return "redirect:/students";
    }

    // ---------------- Edit ----------------

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable int id, Model model, RedirectAttributes redirect) {
        Student student = studentDAO.getStudentById(id);
        if (student == null) {
            redirect.addFlashAttribute("error", "Student not found.");
            return "redirect:/students";
        }
        model.addAttribute("student", student);
        model.addAttribute("form", new StudentForm());
        return "students/edit";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable int id, @ModelAttribute("form") StudentForm form,
                       Model model, RedirectAttributes redirect) {
        Student existing = studentDAO.getStudentById(id);
        if (existing == null) {
            redirect.addFlashAttribute("error", "Student not found.");
            return "redirect:/students";
        }

        FormValidator v = new FormValidator();
        if (!applyEdit(existing, form, v)) {
            model.addAttribute("student", existing);
            model.addAttribute("errors", v.getErrors());
            model.addAttribute("error", "Please fix the highlighted fields. Nothing was saved.");
            return "students/edit";
        }
        if (studentDAO.updateStudent(existing)) {
            redirect.addFlashAttribute("success", "Student " + existing.getName() + " updated.");
        } else {
            redirect.addFlashAttribute("error", "Student not found. Nothing was updated.");
        }
        return "redirect:/students";
    }

    // ---------------- A student's own profile ----------------

    @GetMapping("/me")
    public String profile(@AuthenticationPrincipal AppUser me, Model model) {
        model.addAttribute("student", ownStudent(me));
        model.addAttribute("form", new StudentForm());
        return "students/profile";
    }

    @PostMapping("/me")
    public String updateProfile(@AuthenticationPrincipal AppUser me, @ModelAttribute("form") StudentForm form,
                                Model model, RedirectAttributes redirect) {
        Student existing = ownStudent(me);
        FormValidator v = new FormValidator();
        if (!applyEdit(existing, form, v)) {
            model.addAttribute("student", existing); // unchanged: applyEdit only copies values when all are valid
            model.addAttribute("errors", v.getErrors());
            model.addAttribute("error", "Please fix the highlighted fields. Nothing was saved.");
            return "students/profile";
        }
        studentDAO.updateStudent(existing);
        redirect.addFlashAttribute("success", "Your profile was updated.");
        return "redirect:/students/me";
    }

    /** The logged-in student's own record (never anyone else's). */
    private Student ownStudent(AppUser me) {
        Student student = studentDAO.getStudentById(me.getStudentId());
        if (student == null) {
            throw Access.denied();
        }
        return student;
    }

    /**
     * Same rule as the console: an empty field keeps the current value.
     * Copies the valid values into {@code existing}; returns false (errors in {@code v}) if any are invalid.
     */
    private static boolean applyEdit(Student existing, StudentForm form, FormValidator v) {
        String name = v.textOrKeep("name", form.getName(), existing.getName(), 100);
        String dept = v.textOrKeep("department", form.getDepartment(), existing.getDepartment(), 50);
        Double cgpa = v.decimalInRangeOrKeep("cgpa", form.getCgpa(), 0, 10, existing.getCgpa());
        Integer backlogs = v.intInRangeOrKeep("backlogs", form.getBacklogs(), 0, Integer.MAX_VALUE,
                existing.getBacklogs());
        String skills = v.textOrKeep("skills", form.getSkills(), existing.getSkills(), 255);
        String email = v.textOrKeep("email", form.getEmail(), existing.getEmail(), 100);
        String phone = v.textOrKeep("phone", form.getPhone(), existing.getPhone(), 20);
        if (v.hasErrors()) {
            return false;
        }
        existing.setName(name);
        existing.setDepartment(normalizeDepartment(dept));
        existing.setCgpa(cgpa);
        existing.setBacklogs(backlogs);
        existing.setSkills(skills);
        existing.setEmail(email);
        existing.setPhone(phone);
        return true;
    }

    // ---------------- Delete ----------------

    @GetMapping("/{id}/delete")
    public String confirmDelete(@PathVariable int id, Model model, RedirectAttributes redirect) {
        Student student = studentDAO.getStudentById(id);
        if (student == null) {
            redirect.addFlashAttribute("error", "Student not found.");
            return "redirect:/students";
        }
        model.addAttribute("student", student);
        model.addAttribute("applicationCount", studentDAO.countApplications(id));
        model.addAttribute("interviewCount", studentDAO.countInterviews(id));
        return "students/delete";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable int id, RedirectAttributes redirect) {
        Student student = studentDAO.getStudentById(id);
        if (student != null && studentDAO.deleteStudent(id)) {
            redirect.addFlashAttribute("success", "Student " + student.getName()
                    + " and all of their applications and interviews were deleted.");
        } else {
            redirect.addFlashAttribute("error", "Student not found. Nothing was deleted.");
        }
        return "redirect:/students";
    }

    /** Departments are stored in upper case (e.g. "cse" -> "CSE") so reports group them consistently. */
    static String normalizeDepartment(String dept) {
        return dept == null ? null : dept.trim().toUpperCase();
    }
}
