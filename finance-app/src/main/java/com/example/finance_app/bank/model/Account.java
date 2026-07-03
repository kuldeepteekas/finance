package com.example.finance_app.bank.model;

import com.example.finance_app.bank.enums.AccountStatus;
import com.example.finance_app.bank.enums.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Human-readable account number (e.g. 1000000001) — set from DB sequence on creation.
    // Immutable after creation: updatable = false.
    @Column(nullable = false, unique = true, updatable = false, length = 10)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Optional display name — helps users distinguish accounts with the same currency
    @Column(length = 100)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    // NUMERIC(19,4) — never negative; DB CHECK constraint is the last line of defense
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    // Status field for model completeness; transition logic is out of scope for v1
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
