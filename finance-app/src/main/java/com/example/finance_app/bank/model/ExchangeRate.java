package com.example.finance_app.bank.model;

import com.example.finance_app.bank.enums.Currency;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "exchange_rates")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Multiply source amount by rate to get target amount
    // Example: fromCurrency=EUR, toCurrency=USD, rate=1.08 → 100 EUR = 108 USD
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency fromCurrency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency toCurrency;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rate;

    // Service always picks the record with the latest effectiveFrom for a given pair
    @Column(nullable = false)
    private LocalDateTime effectiveFrom;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
