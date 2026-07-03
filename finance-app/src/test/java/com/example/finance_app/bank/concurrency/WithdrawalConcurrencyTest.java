package com.example.finance_app.bank.concurrency;

import com.example.finance_app.bank.enums.AccountStatus;
import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.enums.ExternalCallStatus;
import com.example.finance_app.bank.exception.InsufficientFundsException;
import com.example.finance_app.bank.model.Account;
import com.example.finance_app.bank.model.User;
import com.example.finance_app.bank.repository.AccountRepository;
import com.example.finance_app.bank.repository.TransactionRepository;
import com.example.finance_app.bank.repository.UserRepository;
import com.example.finance_app.bank.service.MoneyTransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the pessimistic lock (SELECT FOR UPDATE) prevents concurrent withdrawals
 * from driving the balance negative.
 *
 * Requires a running PostgreSQL instance (uses the application's datasource).
 * Run with: mvn test -Dtest=WithdrawalConcurrencyTest
 */
@SpringBootTest
class WithdrawalConcurrencyTest {

    @Autowired MoneyTransactionService moneyTransactionService;
    @Autowired AccountRepository accountRepository;
    @Autowired UserRepository userRepository;
    @Autowired TransactionRepository transactionRepository;

    private User testUser;
    private Account testAccount;

    @AfterEach
    void cleanup() {
        if (testAccount != null) {
            transactionRepository.deleteByAccount_Id(testAccount.getId());
            accountRepository.delete(testAccount);
        }
        if (testUser != null) {
            userRepository.delete(testUser);
        }
    }

    @Test
    void concurrentWithdrawals_balanceNeverGoesNegative() throws InterruptedException {
        // Setup: account with 1000 EUR, 10 threads each try to withdraw 200
        // Only 5 can succeed (5 × 200 = 1000); the other 5 must fail with InsufficientFunds
        testUser = userRepository.save(User.builder()
                .username("concurrency-test-" + UUID.randomUUID())
                .email("ct-" + UUID.randomUUID() + "@test.com")
                .password("$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy")
                .build());

        testAccount = accountRepository.save(Account.builder()
                .user(testUser)
                .accountName("Concurrency Test Account")
                .currency(Currency.EUR)
                .balance(new BigDecimal("1000.00"))
                .status(AccountStatus.ACTIVE)
                .build());

        int threadCount = 10;
        BigDecimal withdrawAmount = new BigDecimal("200.00");

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);   // all threads wait here before firing
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount    = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new ArrayList<>();

        UUID accountId = testAccount.getId();
        UUID userId    = testUser.getId();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // hold until all threads are ready
                    moneyTransactionService.executeWithdraw(
                            accountId, userId, withdrawAmount,
                            "concurrency test", UUID.randomUUID().toString(), // unique key per thread
                            ExternalCallStatus.SKIPPED);
                    successCount.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    failCount.incrementAndGet();
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startGate.countDown(); // fire all threads at once
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);

        executor.shutdown();

        assertThat(completed).as("All threads should finish within 30s").isTrue();
        assertThat(unexpectedErrors).as("No unexpected errors").isEmpty();

        // Exactly 5 should succeed (5 × 200 = 1000), exactly 5 should fail
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        // Final balance must be exactly 0 — never negative
        Account finalState = accountRepository.findById(accountId).orElseThrow();
        assertThat(finalState.getBalance())
                .as("Balance must not go negative")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
