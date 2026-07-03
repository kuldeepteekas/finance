package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.response.TransactionPageResponse;
import com.example.finance_app.bank.enums.AccountStatus;
import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.enums.ExternalCallStatus;
import com.example.finance_app.bank.enums.TransactionStatus;
import com.example.finance_app.bank.enums.TransactionType;
import com.example.finance_app.bank.exception.AccountNotFoundException;
import com.example.finance_app.bank.model.Account;
import com.example.finance_app.bank.model.Transaction;
import com.example.finance_app.bank.model.User;
import com.example.finance_app.bank.repository.AccountRepository;
import com.example.finance_app.bank.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cursor-based pagination logic in TransactionHistoryService.
 *
 * The cursor is an opaque base64 string (internal detail), so we only verify
 * its presence/absence — not its content.
 */
@ExtendWith(MockitoExtension.class)
class TransactionHistoryServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock TransactionRepository transactionRepository;
    @InjectMocks TransactionHistoryService transactionHistoryService;

    private final UUID userId    = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private Account account;

    @BeforeEach
    void setUp() {
        User user = User.builder().id(userId).username("alice").email("a@a.com").password("x").build();
        account = Account.builder()
                .id(accountId).user(user).currency(Currency.EUR)
                .balance(new BigDecimal("500.00")).status(AccountStatus.ACTIVE).build();
    }

    // ─── OWNERSHIP CHECK ─────────────────────────────────────────────────────

    @Test
    void getTransactions_accountNotOwnedByUser_throwsAccountNotFoundException() {
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionHistoryService.getTransactions(accountId, userId, null, 5))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(accountId.toString());
    }

    // ─── FIRST PAGE — NO CURSOR ───────────────────────────────────────────────

    @Test
    void getTransactions_fewerResultsThanPageSize_noNextCursor() {
        // 3 results for a page of 5 → no next page
        List<Transaction> rows = makeTransactions(3);
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(transactionRepository.findFirstPage(eq(accountId), eq(userId), any())).thenReturn(rows);

        TransactionPageResponse response = transactionHistoryService.getTransactions(accountId, userId, null, 5);

        assertThat(response.getTransactions()).hasSize(3);
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void getTransactions_exactlyPageSizeResults_noNextCursor() {
        // 5 results for a page of 5 → we fetch 6 (pageSize+1) and got 5 → no next page
        List<Transaction> rows = makeTransactions(5);
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(transactionRepository.findFirstPage(eq(accountId), eq(userId), any())).thenReturn(rows);

        TransactionPageResponse response = transactionHistoryService.getTransactions(accountId, userId, null, 5);

        assertThat(response.getTransactions()).hasSize(5);
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    void getTransactions_moreResultsThanPageSize_hasNextCursorAndPageTrimmed() {
        // We return pageSize+1 rows → service knows there are more → sets nextCursor
        int pageSize = 5;
        List<Transaction> rows = makeTransactions(pageSize + 1); // 6 rows
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(transactionRepository.findFirstPage(eq(accountId), eq(userId), any())).thenReturn(rows);

        TransactionPageResponse response = transactionHistoryService.getTransactions(accountId, userId, null, pageSize);

        // Page must be trimmed to exactly pageSize (the +1 is just the lookahead)
        assertThat(response.getTransactions()).hasSize(pageSize);
        assertThat(response.getNextCursor()).isNotNull().isNotBlank();
    }

    @Test
    void getTransactions_emptyAccount_returnsEmptyPageAndNoNextCursor() {
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(transactionRepository.findFirstPage(eq(accountId), eq(userId), any())).thenReturn(List.of());

        TransactionPageResponse response = transactionHistoryService.getTransactions(accountId, userId, null, 5);

        assertThat(response.getTransactions()).isEmpty();
        assertThat(response.getNextCursor()).isNull();
    }

    // ─── PAGE SIZE RESOLUTION ─────────────────────────────────────────────────

    @Test
    void getTransactions_nullPageSize_usesDefaultOf20() {
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(transactionRepository.findFirstPage(eq(accountId), eq(userId), any())).thenReturn(List.of());

        transactionHistoryService.getTransactions(accountId, userId, null, null);

        // Default is 20 → pageable must request 21 (20+1 for lookahead)
        verify(transactionRepository).findFirstPage(
                eq(accountId), eq(userId),
                argThat(p -> p.getPageSize() == 21));
    }

    @Test
    void getTransactions_zeroPageSize_usesDefaultOf20() {
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(transactionRepository.findFirstPage(eq(accountId), eq(userId), any())).thenReturn(List.of());

        transactionHistoryService.getTransactions(accountId, userId, null, 0);

        verify(transactionRepository).findFirstPage(
                eq(accountId), eq(userId),
                argThat(p -> p.getPageSize() == 21));
    }

    @Test
    void getTransactions_pageSizeExceedsMax_clampedAt100() {
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(transactionRepository.findFirstPage(eq(accountId), eq(userId), any())).thenReturn(List.of());

        transactionHistoryService.getTransactions(accountId, userId, null, 1000);

        // MAX_PAGE_SIZE = 100 → pageable must request 101 (100+1 for lookahead)
        verify(transactionRepository).findFirstPage(
                eq(accountId), eq(userId),
                argThat(p -> p.getPageSize() == 101));
    }

    @Test
    void getTransactions_requestedPageSizeRespected_whenBelowMax() {
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(transactionRepository.findFirstPage(eq(accountId), eq(userId), any())).thenReturn(List.of());

        transactionHistoryService.getTransactions(accountId, userId, null, 7);

        // 7 is below MAX → should use 7+1=8 as pageable
        verify(transactionRepository).findFirstPage(
                eq(accountId), eq(userId),
                argThat(p -> p.getPageSize() == 8));
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    /** Builds N minimal Transaction objects that toResponse() can map without NPE. */
    private List<Transaction> makeTransactions(int count) {
        List<Transaction> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(Transaction.builder()
                    .id(UUID.randomUUID())
                    .account(account)
                    .type(TransactionType.DEPOSIT)
                    .amount(new BigDecimal("100.00"))
                    .currency(Currency.EUR)
                    .balanceBefore(new BigDecimal("0.00"))
                    .balanceAfter(new BigDecimal("100.00"))
                    .status(TransactionStatus.SUCCESS)
                    .correlationId(UUID.randomUUID())
                    .externalCallStatus(ExternalCallStatus.SKIPPED)
                    .createdAt(LocalDateTime.now().minusDays(count - i))
                    .build());
        }
        return list;
    }
}
