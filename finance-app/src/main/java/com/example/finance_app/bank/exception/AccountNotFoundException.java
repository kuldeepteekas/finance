package com.example.finance_app.bank.exception;

import java.util.UUID;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(UUID accountId) {
        super("Account not found: " + accountId);
    }
}
