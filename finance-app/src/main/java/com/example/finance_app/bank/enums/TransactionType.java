package com.example.finance_app.bank.enums;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER_OUT,   // same-currency transfer, debit side
    TRANSFER_IN,    // same-currency transfer, credit side
    EXCHANGE_OUT,   // cross-currency transfer, debit side (with rate conversion)
    EXCHANGE_IN     // cross-currency transfer, credit side (converted amount)
}
