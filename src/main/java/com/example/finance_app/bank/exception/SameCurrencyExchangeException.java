package com.example.finance_app.bank.exception;

import com.example.finance_app.bank.enums.Currency;

public class SameCurrencyExchangeException extends RuntimeException {

    public SameCurrencyExchangeException(Currency currency) {
        super("Cannot exchange between accounts with the same currency: " + currency);
    }
}
