package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.response.ExchangeRateResponse;
import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.exception.ExchangeRateNotFoundException;
import com.example.finance_app.bank.model.ExchangeRate;
import com.example.finance_app.bank.repository.ExchangeRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateRepository exchangeRateRepository;

    // Returns the single latest rate for a specific currency pair.
    @Transactional(readOnly = true)
    public ExchangeRateResponse getRate(Currency from, Currency to) {
        return exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveFromDesc(from, to)
                .map(this::toResponse)
                .orElseThrow(() -> new ExchangeRateNotFoundException(from, to));
    }

    // Returns the latest rate for every pair originating from the given currency.
    // Useful for the frontend to show all options when a user picks a source account.
    @Transactional(readOnly = true)
    public List<ExchangeRateResponse> getRatesFrom(Currency from) {
        List<ExchangeRate> all = exchangeRateRepository
                .findByFromCurrencyOrderByEffectiveFromDesc(from);

        // Keep only the latest rate per target currency (list is already DESC by effectiveFrom)
        Map<Currency, ExchangeRate> latestPerTarget = new LinkedHashMap<>();
        for (ExchangeRate rate : all) {
            latestPerTarget.putIfAbsent(rate.getToCurrency(), rate);
        }

        return latestPerTarget.values().stream().map(this::toResponse).toList();
    }

    private ExchangeRateResponse toResponse(ExchangeRate rate) {
        return ExchangeRateResponse.builder()
                .fromCurrency(rate.getFromCurrency())
                .toCurrency(rate.getToCurrency())
                .rate(rate.getRate())
                .effectiveFrom(rate.getEffectiveFrom())
                .build();
    }
}
