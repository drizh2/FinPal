package com.dadry.finpal.controller;

import com.dadry.finpal.model.User;
import com.dadry.finpal.service.PlanningService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller
@RequestMapping("/planning")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningService planningService;

    @GetMapping
    public String planningPage(@RequestParam(required = false) String month, Model model, HttpServletRequest request) {
        YearMonth selectedMonth = parseMonth(month);
        PlanningService.PlanningViewData planningData = planningService.getPlanningData(getCurrentUserId(request), selectedMonth);

        model.addAttribute("planning", planningData);
        return "planning";
    }

    @PostMapping("/goals")
    public String createGoal(@RequestParam String title,
                             @RequestParam(required = false) String description,
                             @RequestParam BigDecimal targetAmount,
                             @RequestParam LocalDate targetDate,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        try {
            planningService.createGoal(getCurrentUserId(request), title, description, targetAmount, targetDate);
            redirectAttributes.addFlashAttribute("successMessage", "Financial goal added successfully.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/planning";
    }

    @PostMapping("/expenses")
    public String createPlannedExpense(@RequestParam String title,
                                       @RequestParam(required = false) String notes,
                                       @RequestParam BigDecimal amount,
                                       @RequestParam LocalDate plannedDate,
                                       HttpServletRequest request,
                                       RedirectAttributes redirectAttributes) {
        try {
            planningService.createPlannedExpense(getCurrentUserId(request), title, notes, amount, plannedDate);
            redirectAttributes.addFlashAttribute("successMessage", "Planned expense added successfully.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/planning";
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        return getCurrentUser(request).getId();
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }

        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ignored) {
            return YearMonth.now();
        }
    }

    private User getCurrentUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("You need to sign in to manage planning.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }

        throw new IllegalArgumentException("Authenticated user was not found.");
    }
}
