package com.example.finance_app.bank.repository;

import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.model.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, UUID> {

    // Returns the most recently effective rate for a given currency pair.
    // Spring Data naming convention: findTop = LIMIT 1, OrderByEffectiveFromDesc = latest first.
    Optional<ExchangeRate> findTopByFromCurrencyAndToCurrencyOrderByEffectiveFromDesc(
            Currency fromCurrency, Currency toCurrency);
}
