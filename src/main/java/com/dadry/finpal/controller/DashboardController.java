package com.dadry.finpal.controller;

import com.dadry.finpal.model.User;
import com.dadry.finpal.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final TransactionService transactionService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);

        Map<String, BigDecimal> totalsByCategory = transactionService.getTotalByCategory(userId);

        List<String> categories = new ArrayList<>(totalsByCategory.keySet());
        List<BigDecimal> totals = new ArrayList<>(totalsByCategory.values());

        model.addAttribute("categories", categories);
        model.addAttribute("totals", totals);
        model.addAttribute("transactionsByMonth", transactionService.getTransactionsGroupedByMonth(userId));
        model.addAttribute("monthlyStats", transactionService.getMonthlyStats(userId));

        return "dashboard";
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null; // або киньте виняток
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getId();
        }

        return null;
    }
}
