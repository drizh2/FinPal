package com.dadry.finpal.repository;

import com.dadry.finpal.model.GroupExpense;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GroupExpenseRepository extends JpaRepository<GroupExpense, Long> {
    List<GroupExpense> findByGroup_IdOrderByCreatedAtDesc(Long groupId);
}
