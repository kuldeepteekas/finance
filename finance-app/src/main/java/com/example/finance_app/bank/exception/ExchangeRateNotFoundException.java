package com.example.finance_app.bank.exception;

import com.example.finance_app.bank.enums.Currency;

public class ExchangeRateNotFoundException extends RuntimeException {

    public ExchangeRateNotFoundException(Currency from, Currency to) {
        super("No exchange rate found for " + from + " → " + to);
    }
}
