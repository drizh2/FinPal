package com.dadry.finpal.repository;

import com.dadry.finpal.model.PlannedExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PlannedExpenseRepository extends JpaRepository<PlannedExpense, Long> {
    List<PlannedExpense> findByUserIdOrderByPlannedDateAsc(Long userId);

    List<PlannedExpense> findByUserIdAndPlannedDateBetweenOrderByPlannedDateAsc(Long userId, LocalDate start, LocalDate end);
}
