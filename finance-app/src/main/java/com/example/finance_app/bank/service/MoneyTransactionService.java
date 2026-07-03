package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.response.TransactionResponse;
import com.example.finance_app.bank.enums.ExternalCallStatus;
import com.example.finance_app.bank.enums.TransactionStatus;
import com.example.finance_app.bank.enums.TransactionType;
import com.example.finance_app.bank.exception.AccountNotFoundException;
import com.example.finance_app.bank.exception.ExchangeRateNotFoundException;
import com.example.finance_app.bank.exception.InsufficientFundsException;
import com.example.finance_app.bank.exception.SameCurrencyExchangeException;
import com.example.finance_app.bank.model.Account;
import com.example.finance_app.bank.model.ExchangeRate;
import com.example.finance_app.bank.model.Transaction;
import com.example.finance_app.bank.repository.AccountRepository;
import com.example.finance_app.bank.repository.ExchangeRateRepository;
import com.example.finance_app.bank.repository.TransactionRepository;
import com.example.finance_app.bank.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Inner service — owns ALL database writes for money operations.
 *
 * WHY a separate class from MoneyOperationService?
 * Spring's @Transactional works via a proxy. If MoneyOperationService called
 * its own @Transactional methods internally, the proxy would be bypassed and
 * the transaction annotations would have no effect. Having two beans ensures
 * every call goes through the proxy.
 *
 * Concurrency protections implemented here:
 *  1. REPEATABLE_READ isolation — consistent reads within a transaction, prevents dirty reads.
 *  2. PESSIMISTIC_WRITE (SELECT FOR UPDATE) — row-level lock prevents concurrent balance changes.
 *  3. Deadlock prevention — always acquire account locks in ascending UUID order.
 *  4. Post-lock balance check — balance is checked only AFTER acquiring the lock (prevents TOCTOU).
 *  5. REQUIRES_NEW for failed transactions — rolls back in its own independent commit so the
 *     audit record survives even when the outer transaction rolls back.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MoneyTransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final UserRepository userRepository;

    // ─── DEPOSIT ─────────────────────────────────────────────────────────────

    // PROTECTION #1: REPEATABLE_READ — no phantom reads; consistent balance across the transaction.
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse executeDeposit(UUID accountId, UUID userId, BigDecimal amount,
                                              String description, String idempotencyKey) {

        // PROTECTION #2: SELECT FOR UPDATE — locks this account row.
        // No other transaction can modify the balance until this one commits or rolls back.
        Account account = lockAccount(accountId);

        BigDecimal balanceBefore = account.getBalance();
        BigDecimal balanceAfter  = balanceBefore.add(amount);

        account.setBalance(balanceAfter);
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .account(account)
                .user(userRepository.getReferenceById(userId))
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .currency(account.getCurrency())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .correlationId(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .externalCallStatus(ExternalCallStatus.SKIPPED) // no external call for deposits
                .build();

        return toResponse(transactionRepository.save(tx));
    }

    // ─── WITHDRAW ────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public TransactionResponse executeWithdraw(UUID accountId, UUID userId, BigDecimal amount,
                                               String description, String idempotencyKey,
                                               ExternalCallStatus externalCallStatus) {

        // PROTECTION #2: Pessimistic lock acquired BEFORE reading the balance.
        Account account = lockAccount(accountId);

        BigDecimal balanceBefore = account.getBalance();

        // PROTECTION #4: Balance check AFTER acquiring the lock.
        // Without this ordering, two concurrent withdrawals could both read the same balance,
        // both pass the check, and both debit — resulting in a negative balance (TOCTOU race).
        if (balanceBefore.compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountId, amount, balanceBefore);
        }

        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        account.setBalance(balanceAfter);
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .account(account)
                .user(userRepository.getReferenceById(userId))
                .type(TransactionType.WITHDRAWAL)
                .amount(amount)
                .currency(account.getCurrency())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .correlationId(UUID.randomUUID())
                .idempotencyKey(idempotencyKey)
                .externalCallStatus(externalCallStatus)
                .build();

        return toResponse(transactionRepository.save(tx));
    }

    // ─── EXCHANGE ────────────────────────────────────────────────────────────

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public List<TransactionResponse> executeExchange(UUID fromAccountId, UUID toAccountId,
                                                     UUID userId, BigDecimal amount,
                                                     String description, String idempotencyKey,
                                                     ExternalCallStatus externalCallStatus) {

        // PROTECTION #3: DEADLOCK PREVENTION — always acquire locks in ascending UUID order.
        //
        // Without ordering: Thread A locks account X → waits for Y.
        //                   Thread B locks account Y → waits for X.  → DEADLOCK.
        // With ordering:    Both threads lock the lower UUID first → no circular wait.
        List<UUID> lockOrder = Stream.of(fromAccountId, toAccountId).sorted().toList();

        Account first  = lockAccount(lockOrder.get(0));
        Account second = lockAccount(lockOrder.get(1));

        Account fromAccount = first.getId().equals(fromAccountId) ? first : second;
        Account toAccount   = first.getId().equals(toAccountId)   ? first : second;

        // Defense-in-depth: same-currency guard (also checked in orchestrator before entering this tx)
        if (fromAccount.getCurrency() == toAccount.getCurrency()) {
            throw new SameCurrencyExchangeException(fromAccount.getCurrency());
        }

        // Fetch the latest applicable exchange rate for this currency pair
        ExchangeRate rate = exchangeRateRepository
                .findTopByFromCurrencyAndToCurrencyOrderByEffectiveFromDesc(
                        fromAccount.getCurrency(), toAccount.getCurrency())
                .orElseThrow(() -> new ExchangeRateNotFoundException(
                        fromAccount.getCurrency(), toAccount.getCurrency()));

        BigDecimal fromBalanceBefore = fromAccount.getBalance();

        // PROTECTION #4: Balance check after lock
        if (fromBalanceBefore.compareTo(amount) < 0) {
            throw new InsufficientFundsException(fromAccountId, amount, fromBalanceBefore);
        }

        // Convert: multiply source amount by rate, round to 4 decimal places (HALF_EVEN = banker's rounding)
        BigDecimal convertedAmount = amount.multiply(rate.getRate()).setScale(4, RoundingMode.HALF_EVEN);

        BigDecimal fromBalanceAfter = fromBalanceBefore.subtract(amount);
        BigDecimal toBalanceBefore  = toAccount.getBalance();
        BigDecimal toBalanceAfter   = toBalanceBefore.add(convertedAmount);

        fromAccount.setBalance(fromBalanceAfter);
        toAccount.setBalance(toBalanceAfter);
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // Both legs share the same correlationId so they can be matched in transaction history
        UUID correlationId = UUID.randomUUID();

        Transaction outTx = Transaction.builder()
                .account(fromAccount)
                .user(userRepository.getReferenceById(userId))
                .type(TransactionType.EXCHANGE_OUT)
                .amount(amount)
                .currency(fromAccount.getCurrency())
                .balanceBefore(fromBalanceBefore)
                .balanceAfter(fromBalanceAfter)
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .externalCallStatus(externalCallStatus) // external call done for EXCHANGE_OUT
                .build();

        Transaction inTx = Transaction.builder()
                .account(toAccount)
                .user(userRepository.getReferenceById(userId))
                .type(TransactionType.EXCHANGE_IN)
                .amount(convertedAmount)
                .currency(toAccount.getCurrency())
                .balanceBefore(toBalanceBefore)
                .balanceAfter(toBalanceAfter)
                .status(TransactionStatus.SUCCESS)
                .description(description)
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .externalCallStatus(ExternalCallStatus.SKIPPED) // EXCHANGE_IN never does the external call
                .build();

        transactionRepository.save(outTx);
        transactionRepository.save(inTx);

        log.info("Exchange completed: {} {} → {} {} (rate={}), correlationId={}",
                amount, fromAccount.getCurrency(), convertedAmount, toAccount.getCurrency(),
                rate.getRate(), correlationId);

        return List.of(toResponse(outTx), toResponse(inTx));
    }

    // ─── SAVE FAILED TRANSACTION ─────────────────────────────────────────────

    // PROTECTION #5: REQUIRES_NEW — opens a brand-new, independent database transaction.
    //
    // When a withdrawal or exchange fails (e.g. InsufficientFunds), the caller's transaction
    // rolls back (balance unchanged). Without REQUIRES_NEW, this rollback would also discard
    // the FAILED transaction record — leaving no audit trail.
    //
    // REQUIRES_NEW commits this record independently, so the failed attempt is always persisted.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TransactionResponse saveFailedTransaction(UUID accountId, UUID userId,
                                                     TransactionType type, BigDecimal amount,
                                                     String failureReason, String idempotencyKey,
                                                     UUID correlationId,
                                                     ExternalCallStatus externalCallStatus) {

        // Load account fresh — this runs in its own new transaction so we get a clean entity
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        // balanceBefore == balanceAfter: no change occurred (operation failed)
        BigDecimal currentBalance = account.getBalance();

        Transaction tx = Transaction.builder()
                .account(account)
                .user(userRepository.getReferenceById(userId))
                .type(type)
                .amount(amount)
                .currency(account.getCurrency())
                .balanceBefore(currentBalance)
                .balanceAfter(currentBalance)
                .status(TransactionStatus.FAILED)
                .failureReason(failureReason)
                .correlationId(correlationId)
                .idempotencyKey(idempotencyKey)
                .externalCallStatus(externalCallStatus)
                .build();

        return toResponse(transactionRepository.save(tx));
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    // PROTECTION #2 (implementation detail): issues SELECT ... FOR UPDATE via @Lock annotation.
    // Hibernate translates this to "SELECT * FROM accounts WHERE id = ? FOR UPDATE".
    // Postgres holds the row lock until the enclosing transaction commits or rolls back.
    private Account lockAccount(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .accountId(tx.getAccount().getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .status(tx.getStatus())
                .description(tx.getDescription())
                .failureReason(tx.getFailureReason())
                .correlationId(tx.getCorrelationId())
                .idempotencyKey(tx.getIdempotencyKey())
                .externalCallStatus(tx.getExternalCallStatus())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
