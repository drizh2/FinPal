package com.dadry.finpal.repository;

import com.dadry.finpal.model.ExpenseGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseGroupRepository extends JpaRepository<ExpenseGroup, Long> {
    List<ExpenseGroup> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<ExpenseGroup> findByIdAndOwnerId(Long id, Long ownerId);
}
