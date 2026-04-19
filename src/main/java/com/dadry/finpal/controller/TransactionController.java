package com.dadry.finpal.controller;

import com.dadry.finpal.model.Transaction;
import com.dadry.finpal.model.User;
import com.dadry.finpal.service.TransactionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public String viewTransactions(Model model, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);

        model.addAttribute("transactions", transactionService.getTransactionsForUser(userId));
        model.addAttribute("transactionsByMonth", transactionService.getTransactionsGroupedByMonth(userId));
        model.addAttribute("transaction", new Transaction());

        return "transactions";
    }

    @PostMapping("/add")
    public String addTransaction(@ModelAttribute Transaction transaction, HttpServletRequest request) {
        transaction.setUserId(getCurrentUserId(request));
        transactionService.saveTransaction(transaction);
        return "redirect:/transactions";
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User) {
            return ((User) principal).getId();
        }

        return null;
    }
}