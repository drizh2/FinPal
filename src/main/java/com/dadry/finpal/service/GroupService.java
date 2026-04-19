package com.dadry.finpal.service;

import com.dadry.finpal.model.ExpenseGroup;
import com.dadry.finpal.model.GroupExpense;
import com.dadry.finpal.model.GroupInvitation;
import com.dadry.finpal.model.GroupMember;
import com.dadry.finpal.model.User;
import com.dadry.finpal.model.enums.InvitationStatus;
import com.dadry.finpal.repository.ExpenseGroupRepository;
import com.dadry.finpal.repository.GroupExpenseRepository;
import com.dadry.finpal.repository.GroupInvitationRepository;
import com.dadry.finpal.repository.GroupMemberRepository;
import com.dadry.finpal.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class GroupService {

    private static final int MAX_GROUP_MEMBERS = 10;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final ExpenseGroupRepository expenseGroupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupExpenseRepository groupExpenseRepository;
    private final GroupInvitationRepository groupInvitationRepository;
    private final UserRepository userRepository;

    public void createGroup(String groupName, User owner) {
        String normalizedGroupName = normalize(groupName);
        if (normalizedGroupName == null) {
            throw new IllegalArgumentException("Group name is required.");
        }

        ExpenseGroup group = new ExpenseGroup();
        group.setName(normalizedGroupName);
        group.setOwnerId(owner.getId());
        group.setCreatedAt(LocalDateTime.now());
        ExpenseGroup savedGroup = expenseGroupRepository.save(group);

        groupMemberRepository.save(buildMember(savedGroup, owner));
    }

    public void inviteUser(Long groupId, Long ownerId, String inviteeEmail) {
        ExpenseGroup group = getOwnedGroup(groupId, ownerId);
        String normalizedEmail = normalize(inviteeEmail);
        if (normalizedEmail == null) {
            throw new IllegalArgumentException("Invite email is required.");
        }

        User invitedUser = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("No registered user found with that email."));

        if (invitedUser.getId().equals(ownerId)) {
            throw new IllegalArgumentException("You cannot invite yourself to your own group.");
        }

        if (groupMemberRepository.countByGroup_Id(groupId) >= MAX_GROUP_MEMBERS) {
            throw new IllegalArgumentException("This group already has the maximum of 10 users.");
        }

        if (groupMemberRepository.existsByGroup_IdAndUserId(groupId, invitedUser.getId())) {
            throw new IllegalArgumentException("That user is already in this group.");
        }

        if (groupInvitationRepository.findByGroup_IdAndStatus(groupId, InvitationStatus.PENDING).isPresent()) {
            throw new IllegalArgumentException("This group already has a pending invitation.");
        }

        GroupInvitation invitation = new GroupInvitation();
        invitation.setGroup(group);
        invitation.setInvitedUserId(invitedUser.getId());
        invitation.setInvitedEmail(invitedUser.getEmail());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setCreatedAt(LocalDateTime.now());
        groupInvitationRepository.save(invitation);
    }

    public void respondToInvitation(Long invitationId, Long invitedUserId, boolean accept) {
        GroupInvitation invitation = groupInvitationRepository.findByIdAndInvitedUserIdAndStatus(
                        invitationId, invitedUserId, InvitationStatus.PENDING)
                .orElseThrow(() -> new IllegalArgumentException("Invitation not found."));

        if (accept) {
            if (groupMemberRepository.countByGroup_Id(invitation.getGroup().getId()) >= MAX_GROUP_MEMBERS
                    && !groupMemberRepository.existsByGroup_IdAndUserId(invitation.getGroup().getId(), invitedUserId)) {
                throw new IllegalArgumentException("This group already has its maximum number of members.");
            }

            if (!groupMemberRepository.existsByGroup_IdAndUserId(invitation.getGroup().getId(), invitedUserId)) {
                User invitedUser = userRepository.findById(invitedUserId)
                        .orElseThrow(() -> new IllegalArgumentException("Invited user not found."));
                groupMemberRepository.save(buildMember(invitation.getGroup(), invitedUser));
            }

            invitation.setStatus(InvitationStatus.ACCEPTED);
        } else {
            invitation.setStatus(InvitationStatus.DECLINED);
        }

        invitation.setRespondedAt(LocalDateTime.now());
        groupInvitationRepository.save(invitation);
    }

    public void addExpense(Long groupId, Long userId, String description, BigDecimal amount) {
        GroupMember currentMember = groupMemberRepository.findByGroup_IdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException("You are not a member of this group."));
        String normalizedDescription = normalize(description);

        if (normalizedDescription == null) {
            throw new IllegalArgumentException("Transaction description is required.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transaction amount must be greater than zero.");
        }

        GroupExpense expense = new GroupExpense();
        expense.setGroup(currentMember.getGroup());
        expense.setPaidBy(currentMember);
        expense.setDescription(normalizedDescription);
        expense.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        expense.setCreatedAt(LocalDateTime.now());
        groupExpenseRepository.save(expense);
    }

    public List<GroupDetails> getGroupsForUser(Long userId) {
        ensureOwnerMemberships(userId);

        return groupMemberRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing((GroupMember member) -> member.getGroup().getCreatedAt()).reversed())
                .map(member -> buildGroupDetails(member.getGroup(), userId))
                .toList();
    }

    public List<InvitationDetails> getPendingInvitationsForUser(Long userId) {
        return groupInvitationRepository.findByInvitedUserIdAndStatusOrderByCreatedAtDesc(userId, InvitationStatus.PENDING)
                .stream()
                .map(invitation -> {
                    User inviter = userRepository.findById(invitation.getGroup().getOwnerId()).orElse(null);
                    return new InvitationDetails(
                            invitation.getId(),
                            invitation.getGroup().getName(),
                            inviter != null ? displayName(inviter) : "Group owner",
                            invitation.getInvitedEmail(),
                            DATE_FORMATTER.format(invitation.getCreatedAt())
                    );
                })
                .toList();
    }

    private GroupDetails buildGroupDetails(ExpenseGroup group, Long currentUserId) {
        List<GroupMember> members = groupMemberRepository.findByGroup_IdOrderByNameAsc(group.getId());
        List<GroupExpense> expenses = groupExpenseRepository.findByGroup_IdOrderByCreatedAtDesc(group.getId());
        GroupInvitation pendingInvitation = groupInvitationRepository.findByGroup_IdAndStatus(group.getId(), InvitationStatus.PENDING)
                .orElse(null);

        BigDecimal totalExpenses = expenses.stream()
                .map(GroupExpense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        List<MemberBalance> balances = buildBalances(members, expenses);
        List<ExpenseSummary> expenseSummaries = expenses.stream()
                .map(expense -> new ExpenseSummary(
                        expense.getDescription(),
                        expense.getPaidBy().getName(),
                        expense.getAmount(),
                        calculateShare(expense.getAmount(), members.size()),
                        DATE_FORMATTER.format(expense.getCreatedAt())
                ))
                .toList();

        boolean currentUserIsOwner = group.getOwnerId().equals(currentUserId);
        boolean currentUserCanAddTransactions = groupMemberRepository.existsByGroup_IdAndUserId(group.getId(), currentUserId);

        return new GroupDetails(
                group.getId(),
                group.getName(),
                DATE_FORMATTER.format(group.getCreatedAt()),
                members.stream()
                        .map(member -> new MemberInfo(member.getName(), member.getEmail(), group.getOwnerId().equals(member.getUserId())))
                        .toList(),
                balances,
                expenseSummaries,
                totalExpenses,
                currentUserIsOwner,
                currentUserCanAddTransactions,
                members.size() < MAX_GROUP_MEMBERS && pendingInvitation == null,
                pendingInvitation != null ? pendingInvitation.getInvitedEmail() : null
        );
    }

    private List<MemberBalance> buildBalances(List<GroupMember> members, List<GroupExpense> expenses) {
        Map<Long, BigDecimal> paidTotals = new LinkedHashMap<>();
        Map<Long, BigDecimal> owedTotals = new LinkedHashMap<>();

        for (GroupMember member : members) {
            paidTotals.put(member.getId(), BigDecimal.ZERO);
            owedTotals.put(member.getId(), BigDecimal.ZERO);
        }

        for (GroupExpense expense : expenses) {
            BigDecimal share = calculateShare(expense.getAmount(), members.size());

            for (GroupMember member : members) {
                owedTotals.put(member.getId(), owedTotals.get(member.getId()).add(share));
            }

            Long payerId = expense.getPaidBy().getId();
            paidTotals.put(payerId, paidTotals.get(payerId).add(expense.getAmount()));
        }

        List<MemberBalance> balances = new ArrayList<>();
        for (GroupMember member : members) {
            BigDecimal paid = paidTotals.get(member.getId()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal owes = owedTotals.get(member.getId()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal balance = paid.subtract(owes).setScale(2, RoundingMode.HALF_UP);
            balances.add(new MemberBalance(member.getName(), member.getEmail(), paid, owes, balance));
        }

        return balances;
    }

    private BigDecimal calculateShare(BigDecimal amount, int membersCount) {
        if (membersCount <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }

        return amount.divide(BigDecimal.valueOf(membersCount), 2, RoundingMode.HALF_UP);
    }

    private ExpenseGroup getOwnedGroup(Long groupId, Long ownerId) {
        return expenseGroupRepository.findByIdAndOwnerId(groupId, ownerId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found."));
    }

    private GroupMember buildMember(ExpenseGroup group, User user) {
        GroupMember member = new GroupMember();
        member.setGroup(group);
        member.setUserId(user.getId());
        member.setName(displayName(user));
        member.setEmail(user.getEmail());
        return member;
    }

    private void ensureOwnerMemberships(Long userId) {
        User owner = userRepository.findById(userId).orElse(null);
        if (owner == null) {
            return;
        }

        for (ExpenseGroup group : expenseGroupRepository.findByOwnerIdOrderByCreatedAtDesc(userId)) {
            if (!groupMemberRepository.existsByGroup_IdAndUserId(group.getId(), userId)) {
                groupMemberRepository.save(buildMember(group, owner));
            }
        }
    }

    private String displayName(User user) {
        String normalizedName = normalize(user.getName());
        return normalizedName != null ? normalizedName : user.getEmail();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class GroupDetails {
        private final Long id;
        private final String name;
        private final String createdAt;
        private final List<MemberInfo> members;
        private final List<MemberBalance> balances;
        private final List<ExpenseSummary> expenses;
        private final BigDecimal totalExpenses;
        private final boolean currentUserIsOwner;
        private final boolean currentUserCanAddTransactions;
        private final boolean canInvite;
        private final String pendingInviteEmail;

        public GroupDetails(Long id, String name, String createdAt, List<MemberInfo> members,
                            List<MemberBalance> balances, List<ExpenseSummary> expenses, BigDecimal totalExpenses,
                            boolean currentUserIsOwner, boolean currentUserCanAddTransactions,
                            boolean canInvite, String pendingInviteEmail) {
            this.id = id;
            this.name = name;
            this.createdAt = createdAt;
            this.members = members;
            this.balances = balances;
            this.expenses = expenses;
            this.totalExpenses = totalExpenses;
            this.currentUserIsOwner = currentUserIsOwner;
            this.currentUserCanAddTransactions = currentUserCanAddTransactions;
            this.canInvite = canInvite;
            this.pendingInviteEmail = pendingInviteEmail;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getCreatedAt() {
            return createdAt;
        }

        public List<MemberInfo> getMembers() {
            return members;
        }

        public List<MemberBalance> getBalances() {
            return balances;
        }

        public List<ExpenseSummary> getExpenses() {
            return expenses;
        }

        public BigDecimal getTotalExpenses() {
            return totalExpenses;
        }

        public boolean isCurrentUserIsOwner() {
            return currentUserIsOwner;
        }

        public boolean isCurrentUserCanAddTransactions() {
            return currentUserCanAddTransactions;
        }

        public boolean isCanInvite() {
            return canInvite;
        }

        public String getPendingInviteEmail() {
            return pendingInviteEmail;
        }
    }

    public static class MemberInfo {
        private final String name;
        private final String email;
        private final boolean owner;

        public MemberInfo(String name, String email, boolean owner) {
            this.name = name;
            this.email = email;
            this.owner = owner;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public boolean isOwner() {
            return owner;
        }
    }

    public static class MemberBalance {
        private final String name;
        private final String email;
        private final BigDecimal paid;
        private final BigDecimal owes;
        private final BigDecimal balance;

        public MemberBalance(String name, String email, BigDecimal paid, BigDecimal owes, BigDecimal balance) {
            this.name = name;
            this.email = email;
            this.paid = paid;
            this.owes = owes;
            this.balance = balance;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public BigDecimal getPaid() {
            return paid;
        }

        public BigDecimal getOwes() {
            return owes;
        }

        public BigDecimal getBalance() {
            return balance;
        }
    }

    public static class ExpenseSummary {
        private final String description;
        private final String paidBy;
        private final BigDecimal amount;
        private final BigDecimal sharePerMember;
        private final String createdAt;

        public ExpenseSummary(String description, String paidBy, BigDecimal amount,
                              BigDecimal sharePerMember, String createdAt) {
            this.description = description;
            this.paidBy = paidBy;
            this.amount = amount;
            this.sharePerMember = sharePerMember;
            this.createdAt = createdAt;
        }

        public String getDescription() {
            return description;
        }

        public String getPaidBy() {
            return paidBy;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public BigDecimal getSharePerMember() {
            return sharePerMember;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }

    public static class InvitationDetails {
        private final Long id;
        private final String groupName;
        private final String inviterName;
        private final String invitedEmail;
        private final String createdAt;

        public InvitationDetails(Long id, String groupName, String inviterName, String invitedEmail, String createdAt) {
            this.id = id;
            this.groupName = groupName;
            this.inviterName = inviterName;
            this.invitedEmail = invitedEmail;
            this.createdAt = createdAt;
        }

        public Long getId() {
            return id;
        }

        public String getGroupName() {
            return groupName;
        }

        public String getInviterName() {
            return inviterName;
        }

        public String getInvitedEmail() {
            return invitedEmail;
        }

        public String getCreatedAt() {
            return createdAt;
        }
    }
}
