package com.example.finance_app.bank.dto.response;

import com.example.finance_app.bank.enums.Currency;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class ExchangeRateResponse {
    private Currency fromCurrency;
    private Currency toCurrency;
    private BigDecimal rate;
    private LocalDateTime effectiveFrom;
}
