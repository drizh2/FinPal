package com.dadry.finpal.service;

import com.dadry.finpal.model.Transaction;
import com.dadry.finpal.model.enums.Category;
import com.dadry.finpal.model.enums.TransactionType;
import com.dadry.finpal.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.TextStyle;
import java.time.Month;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public void saveTransaction(Transaction transaction) {
        transaction.setDate(LocalDateTime.now());
        transactionRepository.save(transaction);
    }

    public List<Transaction> getTransactionsForUser(Long userId) {
        return transactionRepository.findByUserId(userId);
    }

    public Map<String, List<Transaction>> getTransactionsGroupedByMonth(Long userId) {
        return transactionRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.groupingBy(
                        t -> t.getDate().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    public Map<String, BigDecimal> getTotalByCategory(Long userId) {
        Map<String, BigDecimal> totals = new HashMap<>();

        for (Category category : Category.values()) {
            totals.put(category.name(), BigDecimal.ZERO);
        }

        List<Transaction> transactions = transactionRepository.findByUserId(userId);

        for (Transaction t : transactions) {
            if (t.getType() == TransactionType.OUTCOME) {
                String categoryName = t.getCategory().name();
                BigDecimal currentTotal = totals.getOrDefault(categoryName, BigDecimal.ZERO);
                totals.put(categoryName, currentTotal.add(t.getAmount()));
            }
        }

        return totals;
    }

    public List<MonthlyStats> getMonthlyStats(Long userId) {
        Map<Month, List<Transaction>> grouped = transactionRepository.findByUserId(userId)
                .stream()
                .collect(Collectors.groupingBy(t -> t.getDate().getMonth()));

        return grouped.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    Month month = entry.getKey();
                    List<Transaction> transactions = entry.getValue();

                    BigDecimal spent = transactions.stream()
                            .filter(t -> t.getType() == TransactionType.OUTCOME)
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal income = transactions.stream()
                            .filter(t -> t.getType() == TransactionType.INCOME)
                            .map(Transaction::getAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal total = income.subtract(spent);

                    return new MonthlyStats(
                            month.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                            spent,
                            total
                    );
                })
                .collect(Collectors.toList());
    }

    public static class MonthlyStats {
        private final String month;
        private final BigDecimal spent;
        private final BigDecimal total;

        public MonthlyStats(String month, BigDecimal spent, BigDecimal total) {
            this.month = month;
            this.spent = spent;
            this.total = total;
        }

        public String getMonth() {
            return month;
        }

        public BigDecimal getSpent() {
            return spent;
        }

        public BigDecimal getTotal() {
            return total;
        }
    }
}