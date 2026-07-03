package com.example.finance_app.bank.dto.response;

import com.example.finance_app.bank.enums.AccountStatus;
import com.example.finance_app.bank.enums.Currency;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AccountResponse {

    private UUID id;
    private String accountNumber;
    private String accountName;
    private Currency currency;
    private BigDecimal balance;
    private AccountStatus status;
    private LocalDateTime createdAt;
}
