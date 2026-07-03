package com.example.finance_app.bank.model;

import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.enums.ExternalCallStatus;
import com.example.finance_app.bank.enums.TransactionStatus;
import com.example.finance_app.bank.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    // Snapshot of account balance at time of this transaction
    // For FAILED transactions: balanceBefore == balanceAfter (no change occurred)
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceBefore;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(length = 500)
    private String description;

    @Column(length = 500)
    private String failureReason;

    // Groups related transactions — EXCHANGE_OUT + EXCHANGE_IN share the same correlationId
    @Column(nullable = false)
    private UUID correlationId;

    @Column(length = 255)
    private String idempotencyKey;

    // Tracks the other side of an internal transfer (EXCHANGE_OUT ↔ EXCHANGE_IN).
    // NULL for DEPOSIT and WITHDRAWAL (no internal counterparty).
    @Column(name = "counterparty_account_id")
    private UUID counterpartyAccountId;

    // Only populated for WITHDRAWAL and EXCHANGE_OUT (where external audit call is made)
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExternalCallStatus externalCallStatus;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
