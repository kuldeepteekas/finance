package com.example.finance_app.bank.exception;

import com.example.finance_app.bank.enums.Currency;

public class SameCurrencyExchangeException extends RuntimeException {

    public SameCurrencyExchangeException(Currency currency) {
        super("Exchange requires different currencies — both accounts are " + currency
                + ". Use /transfer for same-currency movements.");
    }
}
