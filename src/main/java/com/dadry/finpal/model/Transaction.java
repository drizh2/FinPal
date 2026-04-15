package com.dadry.finpal.model;

import com.dadry.finpal.model.enums.Category;
import com.dadry.finpal.model.enums.TransactionType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Setter
@Getter
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    private Long userId;

    private BigDecimal amount;

    private LocalDateTime date;

    @Enumerated(EnumType.STRING)
    private Category category;

    private String description;

    @Enumerated(EnumType.STRING)
    private TransactionType type;

}
