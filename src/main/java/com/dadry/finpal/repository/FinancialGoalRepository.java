package com.dadry.finpal.repository;

import com.dadry.finpal.model.FinancialGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FinancialGoalRepository extends JpaRepository<FinancialGoal, Long> {
    List<FinancialGoal> findByUserIdOrderByTargetDateAsc(Long userId);
}
