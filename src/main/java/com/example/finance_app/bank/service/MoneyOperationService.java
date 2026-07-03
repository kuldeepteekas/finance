package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.request.DepositRequest;
import com.example.finance_app.bank.dto.request.ExchangeRequest;
import com.example.finance_app.bank.dto.request.WithdrawRequest;
import com.example.finance_app.bank.dto.response.TransactionResponse;
import com.example.finance_app.bank.enums.ExternalCallStatus;
import com.example.finance_app.bank.enums.TransactionType;
import com.example.finance_app.bank.exception.AccountNotFoundException;
import com.example.finance_app.bank.exception.InsufficientFundsException;
import com.example.finance_app.bank.exception.SameCurrencyExchangeException;
import com.example.finance_app.bank.model.Account;
import com.example.finance_app.bank.model.IdempotencyKey;
import com.example.finance_app.bank.repository.AccountRepository;
import com.example.finance_app.bank.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrator — no @Transactional annotation here by design.
 *
 * Each method coordinates the full operation in this order:
 *  1. Idempotency check  — return cached response if duplicate request
 *  2. Ownership check    — 404 if account doesn't belong to the caller
 *  3. External audit     — BEFORE opening the DB transaction (keeps lock window short)
 *  4. DB transaction     — delegate to MoneyTransactionService (acquires locks, mutates balance)
 *  5. Failure handling   — catch InsufficientFunds, persist FAILED record in REQUIRES_NEW tx
 *  6. Store idempotency  — save response so duplicate requests can be replayed
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoneyOperationService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final MoneyTransactionService moneyTransactionService;
    private final ExternalAuditService externalAuditService;
    private final IdempotencyService idempotencyService;

    // ─── DEPOSIT ─────────────────────────────────────────────────────────────

    public TransactionResponse deposit(UUID userId, UUID accountId,
                                       DepositRequest request, String idempotencyKey) {

        // Step 1 — idempotency: replay cached response on duplicate request
        Optional<IdempotencyKey> existing = idempotencyService.findExistingKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate deposit — replaying cached response, key={}", idempotencyKey);
            return idempotencyService.deserialize(existing.get().getResponseBody());
        }

        // Step 2 — ownership: 404 if account doesn't belong to this user
        accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        // Step 3 — no external audit for deposits

        // Step 4 — execute deposit inside @Transactional(REPEATABLE_READ) with pessimistic lock
        TransactionResponse response = moneyTransactionService
                .executeDeposit(accountId, userId, request.getAmount(),
                        request.getDescription(), idempotencyKey);

        // Step 6 — store for future replay
        idempotencyService.save(userId, idempotencyKey, 201, response,
                userRepository.getReferenceById(userId));

        return response;
    }

    // ─── WITHDRAW ────────────────────────────────────────────────────────────

    public TransactionResponse withdraw(UUID userId, UUID accountId,
                                        WithdrawRequest request, String idempotencyKey) {

        Optional<IdempotencyKey> existing = idempotencyService.findExistingKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate withdraw — replaying cached response, key={}", idempotencyKey);
            return idempotencyService.deserialize(existing.get().getResponseBody());
        }

        // Ownership check — also gives us the account for the failure-path handler below
        Account account = accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        // Step 3 — external audit BEFORE the DB transaction.
        // The money operation is not gated on audit success (best-effort).
        ExternalCallStatus auditStatus = externalAuditService.audit(
                userId, accountId, "WITHDRAWAL", request.getAmount(), idempotencyKey);

        UUID correlationId = UUID.randomUUID();

        try {
            TransactionResponse response = moneyTransactionService
                    .executeWithdraw(accountId, userId, request.getAmount(),
                            request.getDescription(), idempotencyKey, auditStatus);

            idempotencyService.save(userId, idempotencyKey, 201, response,
                    userRepository.getReferenceById(userId));

            return response;

        } catch (InsufficientFundsException e) {
            // The executeWithdraw transaction rolled back — but we still want an audit trail.
            // saveFailedTransaction uses REQUIRES_NEW: it commits independently.
            log.warn("Insufficient funds: accountId={}, requested={}", accountId, request.getAmount());
            moneyTransactionService.saveFailedTransaction(
                    accountId, userId, TransactionType.WITHDRAWAL,
                    request.getAmount(), e.getMessage(),
                    idempotencyKey, correlationId, auditStatus);
            throw e; // re-throw so controller returns 422
        }
    }

    // ─── EXCHANGE ────────────────────────────────────────────────────────────

    public List<TransactionResponse> exchange(UUID userId, UUID fromAccountId,
                                              ExchangeRequest request, String idempotencyKey) {

        Optional<IdempotencyKey> existing = idempotencyService.findExistingKey(userId, idempotencyKey);
        if (existing.isPresent()) {
            log.info("Duplicate exchange — replaying cached response, key={}", idempotencyKey);
            return idempotencyService.deserializeList(existing.get().getResponseBody());
        }

        // Verify both accounts belong to the authenticated user (returns 404 if not)
        Account fromAccount = accountRepository.findByIdAndUser_Id(fromAccountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(fromAccountId));
        Account toAccount = accountRepository.findByIdAndUser_Id(request.getToAccountId(), userId)
                .orElseThrow(() -> new AccountNotFoundException(request.getToAccountId()));

        // Fail fast before any lock or external call
        if (fromAccount.getCurrency() == toAccount.getCurrency()) {
            throw new SameCurrencyExchangeException(fromAccount.getCurrency());
        }

        // External audit before DB transaction (keeps lock window short)
        ExternalCallStatus auditStatus = externalAuditService.audit(
                userId, fromAccountId, "EXCHANGE", request.getAmount(), idempotencyKey);

        UUID correlationId = UUID.randomUUID();

        try {
            List<TransactionResponse> responses = moneyTransactionService
                    .executeExchange(fromAccountId, request.getToAccountId(),
                            userId, request.getAmount(), request.getDescription(),
                            idempotencyKey, auditStatus);

            idempotencyService.saveList(userId, idempotencyKey, 201, responses,
                    userRepository.getReferenceById(userId));

            return responses;

        } catch (InsufficientFundsException e) {
            log.warn("Insufficient funds for exchange: fromAccountId={}, requested={}", fromAccountId, request.getAmount());
            moneyTransactionService.saveFailedTransaction(
                    fromAccountId, userId, TransactionType.EXCHANGE_OUT,
                    request.getAmount(), e.getMessage(),
                    idempotencyKey, correlationId, auditStatus);
            throw e;
        }
    }
}
