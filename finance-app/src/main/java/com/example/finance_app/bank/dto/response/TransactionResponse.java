package com.example.finance_app.bank.dto.response;

import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.enums.ExternalCallStatus;
import com.example.finance_app.bank.enums.TransactionStatus;
import com.example.finance_app.bank.enums.TransactionType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class TransactionResponse {

    private UUID id;
    private UUID accountId;
    private TransactionType type;
    private BigDecimal amount;
    private Currency currency;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private TransactionStatus status;
    private String description;
    private String failureReason;

    // Groups EXCHANGE_OUT + EXCHANGE_IN pair — same correlationId on both records
    private UUID correlationId;

    // The other side of an internal transfer: non-null for EXCHANGE_OUT and EXCHANGE_IN only.
    // EXCHANGE_OUT → ID of the account that received the funds.
    // EXCHANGE_IN  → ID of the account that sent the funds.
    private UUID counterpartyAccountId;

    private String idempotencyKey;

    // Populated for WITHDRAWAL and EXCHANGE_OUT; SKIPPED for DEPOSIT and EXCHANGE_IN
    private ExternalCallStatus externalCallStatus;

    private LocalDateTime createdAt;
}
