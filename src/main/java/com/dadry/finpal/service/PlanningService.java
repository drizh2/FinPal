package com.dadry.finpal.service;

import com.dadry.finpal.model.FinancialGoal;
import com.dadry.finpal.model.PlannedExpense;
import com.dadry.finpal.model.Transaction;
import com.dadry.finpal.model.enums.TransactionType;
import com.dadry.finpal.repository.FinancialGoalRepository;
import com.dadry.finpal.repository.PlannedExpenseRepository;
import com.dadry.finpal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanningService {

    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("d");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM yyyy");

    private final FinancialGoalRepository financialGoalRepository;
    private final PlannedExpenseRepository plannedExpenseRepository;
    private final TransactionRepository transactionRepository;

    public void createGoal(Long userId, String title, String description, BigDecimal targetAmount, LocalDate targetDate) {
        String normalizedTitle = normalize(title);
        String normalizedDescription = normalize(description);

        if (normalizedTitle == null) {
            throw new IllegalArgumentException("Goal title is required.");
        }

        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Goal amount must be greater than zero.");
        }

        if (targetDate == null || targetDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Goal target date must be today or later.");
        }

        FinancialGoal goal = new FinancialGoal();
        goal.setUserId(userId);
        goal.setTitle(normalizedTitle);
        goal.setDescription(normalizedDescription);
        goal.setTargetAmount(targetAmount.setScale(2, RoundingMode.HALF_UP));
        goal.setTargetDate(targetDate);
        goal.setCreatedAt(LocalDate.now());
        financialGoalRepository.save(goal);
    }

    public void createPlannedExpense(Long userId, String title, String notes, BigDecimal amount, LocalDate plannedDate) {
        String normalizedTitle = normalize(title);
        String normalizedNotes = normalize(notes);

        if (normalizedTitle == null) {
            throw new IllegalArgumentException("Planned expense title is required.");
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Planned expense amount must be greater than zero.");
        }

        if (plannedDate == null) {
            throw new IllegalArgumentException("Planned expense date is required.");
        }

        PlannedExpense expense = new PlannedExpense();
        expense.setUserId(userId);
        expense.setTitle(normalizedTitle);
        expense.setNotes(normalizedNotes);
        expense.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        expense.setPlannedDate(plannedDate);
        expense.setCreatedAt(LocalDate.now());
        plannedExpenseRepository.save(expense);
    }

    public PlanningViewData getPlanningData(Long userId, YearMonth requestedMonth) {
        YearMonth month = requestedMonth != null ? requestedMonth : YearMonth.now();
        BigDecimal currentSavings = calculateCurrentSavings(userId);

        List<GoalProgress> goalProgress = financialGoalRepository.findByUserIdOrderByTargetDateAsc(userId).stream()
                .map(goal -> buildGoalProgress(goal, currentSavings))
                .toList();

        List<PlannedExpense> monthExpenses = plannedExpenseRepository.findByUserIdAndPlannedDateBetweenOrderByPlannedDateAsc(
                userId,
                month.atDay(1),
                month.atEndOfMonth()
        );

        List<PlannedExpenseSummary> upcomingExpenses = plannedExpenseRepository.findByUserIdOrderByPlannedDateAsc(userId).stream()
                .filter(expense -> !expense.getPlannedDate().isBefore(LocalDate.now()))
                .limit(6)
                .map(this::toExpenseSummary)
                .toList();

        return new PlanningViewData(
                goalProgress,
                upcomingExpenses,
                buildCalendar(month, monthExpenses),
                MONTH_LABEL.format(month.atDay(1)),
                month.minusMonths(1).toString(),
                month.plusMonths(1).toString()
        );
    }

    private GoalProgress buildGoalProgress(FinancialGoal goal, BigDecimal currentSavings) {
        BigDecimal target = goal.getTargetAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal progressAmount = currentSavings.min(target).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        int percent = target.compareTo(BigDecimal.ZERO) == 0
                ? 0
                : progressAmount.multiply(BigDecimal.valueOf(100))
                        .divide(target, 0, RoundingMode.HALF_UP)
                        .intValue();

        BigDecimal remaining = target.subtract(progressAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        return new GoalProgress(
                goal.getTitle(),
                goal.getDescription(),
                target,
                progressAmount,
                remaining,
                percent,
                goal.getTargetDate().toString()
        );
    }

    private CalendarMonth buildCalendar(YearMonth month, List<PlannedExpense> expenses) {
        Map<LocalDate, List<PlannedExpenseSummary>> expensesByDate = expenses.stream()
                .collect(Collectors.groupingBy(
                        PlannedExpense::getPlannedDate,
                        Collectors.mapping(this::toExpenseSummary, Collectors.toList())
                ));

        List<CalendarDay> days = new ArrayList<>();
        LocalDate firstOfMonth = month.atDay(1);
        int shift = firstOfMonth.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();

        for (int i = 0; i < shift; i++) {
            days.add(new CalendarDay("", false, List.of(), BigDecimal.ZERO));
        }

        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate currentDate = month.atDay(day);
            List<PlannedExpenseSummary> currentExpenses = expensesByDate.getOrDefault(currentDate, List.of())
                    .stream()
                    .sorted(Comparator.comparing(PlannedExpenseSummary::getTitle))
                    .toList();

            BigDecimal total = currentExpenses.stream()
                    .map(PlannedExpenseSummary::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .setScale(2, RoundingMode.HALF_UP);

            days.add(new CalendarDay(DAY_LABEL.format(currentDate), true, currentExpenses, total));
        }

        while (days.size() % 7 != 0) {
            days.add(new CalendarDay("", false, List.of(), BigDecimal.ZERO));
        }

        return new CalendarMonth(days, List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"));
    }

    private PlannedExpenseSummary toExpenseSummary(PlannedExpense expense) {
        return new PlannedExpenseSummary(
                expense.getTitle(),
                expense.getNotes(),
                expense.getAmount().setScale(2, RoundingMode.HALF_UP),
                expense.getPlannedDate().toString()
        );
    }

    private BigDecimal calculateCurrentSavings(Long userId) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        BigDecimal income = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.INCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outcome = transactions.stream()
                .filter(transaction -> transaction.getType() == TransactionType.OUTCOME)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return income.subtract(outcome).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static class PlanningViewData {
        private final List<GoalProgress> goals;
        private final List<PlannedExpenseSummary> upcomingExpenses;
        private final CalendarMonth calendar;
        private final String monthLabel;
        private final String previousMonth;
        private final String nextMonth;

        public PlanningViewData(List<GoalProgress> goals, List<PlannedExpenseSummary> upcomingExpenses,
                                CalendarMonth calendar, String monthLabel, String previousMonth, String nextMonth) {
            this.goals = goals;
            this.upcomingExpenses = upcomingExpenses;
            this.calendar = calendar;
            this.monthLabel = monthLabel;
            this.previousMonth = previousMonth;
            this.nextMonth = nextMonth;
        }

        public List<GoalProgress> getGoals() {
            return goals;
        }

        public List<PlannedExpenseSummary> getUpcomingExpenses() {
            return upcomingExpenses;
        }

        public CalendarMonth getCalendar() {
            return calendar;
        }

        public String getMonthLabel() {
            return monthLabel;
        }

        public String getPreviousMonth() {
            return previousMonth;
        }

        public String getNextMonth() {
            return nextMonth;
        }
    }

    public static class GoalProgress {
        private final String title;
        private final String description;
        private final BigDecimal targetAmount;
        private final BigDecimal progressAmount;
        private final BigDecimal remainingAmount;
        private final int progressPercent;
        private final String targetDate;

        public GoalProgress(String title, String description, BigDecimal targetAmount, BigDecimal progressAmount,
                            BigDecimal remainingAmount, int progressPercent, String targetDate) {
            this.title = title;
            this.description = description;
            this.targetAmount = targetAmount;
            this.progressAmount = progressAmount;
            this.remainingAmount = remainingAmount;
            this.progressPercent = progressPercent;
            this.targetDate = targetDate;
        }

        public String getTitle() {
            return title;
        }

        public String getDescription() {
            return description;
        }

        public BigDecimal getTargetAmount() {
            return targetAmount;
        }

        public BigDecimal getProgressAmount() {
            return progressAmount;
        }

        public BigDecimal getRemainingAmount() {
            return remainingAmount;
        }

        public int getProgressPercent() {
            return progressPercent;
        }

        public String getTargetDate() {
            return targetDate;
        }
    }

    public static class PlannedExpenseSummary {
        private final String title;
        private final String notes;
        private final BigDecimal amount;
        private final String plannedDate;

        public PlannedExpenseSummary(String title, String notes, BigDecimal amount, String plannedDate) {
            this.title = title;
            this.notes = notes;
            this.amount = amount;
            this.plannedDate = plannedDate;
        }

        public String getTitle() {
            return title;
        }

        public String getNotes() {
            return notes;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public String getPlannedDate() {
            return plannedDate;
        }
    }

    public static class CalendarMonth {
        private final List<CalendarDay> days;
        private final List<String> headers;

        public CalendarMonth(List<CalendarDay> days, List<String> headers) {
            this.days = days;
            this.headers = headers;
        }

        public List<CalendarDay> getDays() {
            return days;
        }

        public List<String> getHeaders() {
            return headers;
        }
    }

    public static class CalendarDay {
        private final String dayNumber;
        private final boolean inMonth;
        private final List<PlannedExpenseSummary> expenses;
        private final BigDecimal totalAmount;

        public CalendarDay(String dayNumber, boolean inMonth, List<PlannedExpenseSummary> expenses, BigDecimal totalAmount) {
            this.dayNumber = dayNumber;
            this.inMonth = inMonth;
            this.expenses = expenses;
            this.totalAmount = totalAmount;
        }

        public String getDayNumber() {
            return dayNumber;
        }

        public boolean isInMonth() {
            return inMonth;
        }

        public List<PlannedExpenseSummary> getExpenses() {
            return expenses;
        }

        public BigDecimal getTotalAmount() {
            return totalAmount;
        }
    }
}
