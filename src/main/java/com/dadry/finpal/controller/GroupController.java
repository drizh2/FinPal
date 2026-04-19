package com.dadry.finpal.controller;

import com.dadry.finpal.model.User;
import com.dadry.finpal.service.GroupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @GetMapping
    public String groupsPage(Model model, HttpServletRequest request) {
        Long userId = getCurrentUserId(request);
        model.addAttribute("groups", groupService.getGroupsForUser(userId));
        model.addAttribute("pendingInvitations", groupService.getPendingInvitationsForUser(userId));
        return "groups";
    }

    @PostMapping("/create")
    public String createGroup(@RequestParam String name, HttpServletRequest request, RedirectAttributes redirectAttributes) {
        try {
            groupService.createGroup(name, getCurrentUser(request));
            redirectAttributes.addFlashAttribute("successMessage", "Group created successfully.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/groups";
    }

    @PostMapping("/{groupId}/invite")
    public String inviteUser(@PathVariable Long groupId,
                             @RequestParam String inviteeEmail,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        try {
            groupService.inviteUser(groupId, getCurrentUserId(request), inviteeEmail);
            redirectAttributes.addFlashAttribute("successMessage", "Invitation sent successfully.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/groups";
    }

    @PostMapping("/{groupId}/expenses")
    public String addExpense(@PathVariable Long groupId,
                             @RequestParam String description,
                             @RequestParam BigDecimal amount,
                             HttpServletRequest request,
                             RedirectAttributes redirectAttributes) {
        try {
            groupService.addExpense(groupId, getCurrentUserId(request), description, amount);
            redirectAttributes.addFlashAttribute("successMessage", "Group transaction added successfully.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/groups";
    }

    @PostMapping("/invitations/{invitationId}/accept")
    public String acceptInvitation(@PathVariable Long invitationId,
                                   HttpServletRequest request,
                                   RedirectAttributes redirectAttributes) {
        try {
            groupService.respondToInvitation(invitationId, getCurrentUserId(request), true);
            redirectAttributes.addFlashAttribute("successMessage", "Invitation accepted.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/groups";
    }

    @PostMapping("/invitations/{invitationId}/decline")
    public String declineInvitation(@PathVariable Long invitationId,
                                    HttpServletRequest request,
                                    RedirectAttributes redirectAttributes) {
        try {
            groupService.respondToInvitation(invitationId, getCurrentUserId(request), false);
            redirectAttributes.addFlashAttribute("successMessage", "Invitation declined.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }

        return "redirect:/groups";
    }

    private Long getCurrentUserId(HttpServletRequest request) {
        return getCurrentUser(request).getId();
    }

    private User getCurrentUser(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("You need to sign in to manage groups.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }

        throw new IllegalArgumentException("Authenticated user was not found.");
    }
}
