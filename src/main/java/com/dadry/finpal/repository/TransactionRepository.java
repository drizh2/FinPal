package com.dadry.finpal.repository;

import com.dadry.finpal.model.Transaction;
import com.dadry.finpal.model.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByUserId(Long userId);
    List<Transaction> findByUserIdAndDateBetween(Long userId, LocalDateTime start, LocalDateTime end);
    List<Transaction> findByUserIdAndType(Long userId, TransactionType type);
}