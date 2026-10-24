package web.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/** Shown when a logged-in user opens a page their role may not use (configured in SecurityConfig). */
@Controller
public class ErrorPageController {

    @RequestMapping("/error/403")
    public String forbidden(Model model, HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        model.addAttribute("message", "You do not have access to that page.");
        return "error";
    }
}
