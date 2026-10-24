package web.controller;

import dao.ApplicationDAO;
import dao.ApplicationDAO.ApplicationView;
import dao.InterviewDAO;
import model.ApplicationStatus;
import model.Interview;
import model.InterviewResult;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import service.PlacementService;
import service.PlacementService.FinalizeResult;
import util.FormValidator;
import web.security.Access;
import web.security.AppUser;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Interviews and selection: schedule a round, record PASS/FAIL with remarks,
 * view the rounds of an application, and finalize the selection.
 * The officer can work on every application; a company only on applications to its own jobs.
 */
@Controller
@RequestMapping("/interviews")
public class InterviewController {

    private final PlacementService placementService;
    private final ApplicationDAO applicationDAO;
    private final InterviewDAO interviewDAO;

    public InterviewController(PlacementService placementService, ApplicationDAO applicationDAO,
                               InterviewDAO interviewDAO) {
        this.placementService = placementService;
        this.applicationDAO = applicationDAO;
        this.interviewDAO = interviewDAO;
    }

    /** Applications in the interview stage first, then all the others. */
    @GetMapping
    public String index(@AuthenticationPrincipal AppUser me, Model model) {
        Integer companyId = me.isCompany() ? me.getCompanyId() : null;
        List<ApplicationView> active = new ArrayList<>();
        List<ApplicationView> others = new ArrayList<>();
        for (ApplicationView a : applicationDAO.getApplicationViews(null, null, companyId)) {
            if (a.status() == ApplicationStatus.SHORTLISTED || a.status() == ApplicationStatus.INTERVIEW_SCHEDULED) {
                active.add(a);
            } else {
                others.add(a);
            }
        }
        model.addAttribute("active", active);
        model.addAttribute("others", others);
        return "interviews/index";
    }

    /** One application: its rounds, the schedule form, result forms and the finalize button. */
    @GetMapping("/application/{id}")
    public String application(@AuthenticationPrincipal AppUser me, @PathVariable int id,
                              Model model, RedirectAttributes redirect) {
        ApplicationView application = applicationDAO.getApplicationView(id);
        if (application == null) {
            redirect.addFlashAttribute("error", "Application not found.");
            return "redirect:/interviews";
        }
        Access.requireOwnApplication(me, application);
        model.addAttribute("app", application);
        model.addAttribute("rounds", interviewDAO.getInterviewsByApplication(id));
        model.addAttribute("today", LocalDate.now());
        return "interviews/application";
    }

    @PostMapping("/application/{id}/schedule")
    public String schedule(@AuthenticationPrincipal AppUser me, @PathVariable int id,
                           @RequestParam(required = false) String round,
                           @RequestParam(required = false) String date, RedirectAttributes redirect) {
        if (!checkOwnership(me, id, redirect)) {
            return "redirect:/interviews";
        }
        FormValidator v = new FormValidator();
        String roundName = v.requiredText("round", round, 50);
        LocalDate when = v.dateOrToday("date", date);
        if (v.hasErrors()) {
            StringBuilder problems = new StringBuilder("The round was not scheduled.");
            v.getErrors().forEach((field, message) ->
                    problems.append(field.equals("round") ? " Round name: " : " Date: ").append(message));
            redirect.addFlashAttribute("error", problems.toString());
            return "redirect:/interviews/application/" + id;
        }

        PlacementService.ScheduleResult scheduled = placementService.scheduleInterview(id, when, roundName);
        switch (scheduled.result()) {
            case OK -> redirect.addFlashAttribute("success",
                    "Round \"" + roundName + "\" scheduled for " + when + " (interview ID " + scheduled.interviewId() + ").");
            case NOT_FOUND -> {
                redirect.addFlashAttribute("error", "Application not found.");
                return "redirect:/interviews";
            }
            default -> redirect.addFlashAttribute("error",
                    "An interview can only be scheduled for a SHORTLISTED or INTERVIEW_SCHEDULED application. "
                            + "Shortlist it first (Applications page); decided applications cannot get new rounds.");
        }
        return "redirect:/interviews/application/" + id;
    }

    @PostMapping("/{interviewId}/result")
    public String recordResult(@AuthenticationPrincipal AppUser me, @PathVariable int interviewId,
                               @RequestParam(required = false) String result,
                               @RequestParam(required = false) String remarks, RedirectAttributes redirect) {
        Interview interview = interviewDAO.getInterviewById(interviewId);
        if (interview == null) {
            redirect.addFlashAttribute("error", "Interview not found.");
            return "redirect:/interviews";
        }
        int applicationId = interview.getApplicationId();
        if (!checkOwnership(me, applicationId, redirect)) {
            return "redirect:/interviews";
        }

        FormValidator v = new FormValidator();
        String cleanRemarks = v.optionalText("remarks", remarks, 255);
        InterviewResult parsed = parseResult(result);
        if (v.hasErrors()) {
            redirect.addFlashAttribute("error", "Remarks: " + v.getErrors().get("remarks"));
            return "redirect:/interviews/application/" + applicationId;
        }

        PlacementService.StepResult outcome = placementService.recordInterviewResult(interviewId, parsed, cleanRemarks);
        switch (outcome) {
            case OK -> redirect.addFlashAttribute("success", "Result " + parsed + " recorded for interview #" + interviewId + ".");
            case NOT_FOUND -> redirect.addFlashAttribute("error", "Interview not found.");
            case INVALID_RESULT -> redirect.addFlashAttribute("error", "Result must be PASS or FAIL.");
            default -> redirect.addFlashAttribute("error",
                    "This application is already decided, so its interview results can no longer be changed.");
        }
        return "redirect:/interviews/application/" + applicationId;
    }

    @PostMapping("/application/{id}/finalize")
    public String finalizeSelection(@AuthenticationPrincipal AppUser me, @PathVariable int id,
                                    RedirectAttributes redirect) {
        if (!checkOwnership(me, id, redirect)) {
            return "redirect:/interviews";
        }
        FinalizeResult result = placementService.finalizeSelection(id);
        switch (result.outcome()) {
            case SELECTED -> redirect.addFlashAttribute("success", "All rounds passed. The student is SELECTED.");
            case REJECTED -> redirect.addFlashAttribute("success", "A round was failed. The application is REJECTED.");
            case ROUNDS_PENDING -> redirect.addFlashAttribute("error",
                    "Some rounds are still PENDING. Record every result before finalizing. Status unchanged: "
                            + result.status() + ".");
            case NO_ROUNDS -> redirect.addFlashAttribute("error",
                    "No interview rounds yet, so there is nothing to decide. Status unchanged: " + result.status() + ".");
            case ALREADY_DECIDED -> redirect.addFlashAttribute("error",
                    "This application is already " + result.status() + "; a decision cannot be changed.");
            case NOT_INTERVIEWING -> redirect.addFlashAttribute("error",
                    "Schedule at least one interview round first. Status unchanged: " + result.status() + ".");
            default -> {
                redirect.addFlashAttribute("error", "Application not found.");
                return "redirect:/interviews";
            }
        }
        return "redirect:/interviews/application/" + id;
    }

    /**
     * Returns false (with an error banner) if the application does not exist; throws
     * AccessDeniedException if it belongs to another company.
     */
    private boolean checkOwnership(AppUser me, int applicationId, RedirectAttributes redirect) {
        ApplicationView application = applicationDAO.getApplicationView(applicationId);
        if (application == null) {
            redirect.addFlashAttribute("error", "Application not found.");
            return false;
        }
        Access.requireOwnApplication(me, application);
        return true;
    }

    /** "PASS"/"FAIL" (any case) -> enum; anything else -> PENDING, which the service rejects with INVALID_RESULT. */
    private static InterviewResult parseResult(String value) {
        if (value == null) {
            return InterviewResult.PENDING;
        }
        try {
            return InterviewResult.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return InterviewResult.PENDING;
        }
    }
}
