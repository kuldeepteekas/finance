package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.request.DepositRequest;
import com.example.finance_app.bank.dto.request.WithdrawRequest;
import com.example.finance_app.bank.dto.response.TransactionResponse;
import com.example.finance_app.bank.enums.AccountStatus;
import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.enums.ExternalCallStatus;
import com.example.finance_app.bank.enums.TransactionStatus;
import com.example.finance_app.bank.enums.TransactionType;
import com.example.finance_app.bank.exception.AccountNotFoundException;
import com.example.finance_app.bank.exception.InsufficientFundsException;
import com.example.finance_app.bank.model.Account;
import com.example.finance_app.bank.model.IdempotencyKey;
import com.example.finance_app.bank.model.User;
import com.example.finance_app.bank.repository.AccountRepository;
import com.example.finance_app.bank.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MoneyOperationServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock UserRepository userRepository;
    @Mock MoneyTransactionService moneyTransactionService;
    @Mock ExternalAuditService externalAuditService;
    @Mock IdempotencyService idempotencyService;
    @InjectMocks MoneyOperationService moneyOperationService;

    private final UUID userId    = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final String idemKey = UUID.randomUUID().toString();

    private User user;
    private Account account;
    private TransactionResponse successResponse;

    @BeforeEach
    void setUp() {
        user = User.builder().id(userId).username("alice").email("a@a.com").password("x").build();
        account = Account.builder()
                .id(accountId).user(user).currency(Currency.EUR)
                .balance(new BigDecimal("500.00")).status(AccountStatus.ACTIVE).build();
        successResponse = TransactionResponse.builder()
                .id(UUID.randomUUID()).accountId(accountId)
                .type(TransactionType.DEPOSIT).amount(new BigDecimal("100.00"))
                .currency(Currency.EUR).balanceBefore(new BigDecimal("500.00"))
                .balanceAfter(new BigDecimal("600.00")).status(TransactionStatus.SUCCESS)
                .correlationId(UUID.randomUUID()).externalCallStatus(ExternalCallStatus.SKIPPED)
                .createdAt(LocalDateTime.now()).build();
    }

    // ─── DEPOSIT ─────────────────────────────────────────────────────────────

    @Test
    void deposit_success_returnsTransactionResponse() {
        DepositRequest request = mock(DepositRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("100.00"));

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(moneyTransactionService.executeDeposit(accountId, userId, new BigDecimal("100.00"), null, idemKey))
                .thenReturn(successResponse);
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        TransactionResponse result = moneyOperationService.deposit(userId, accountId, request, idemKey);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(result.getType()).isEqualTo(TransactionType.DEPOSIT);
        verify(idempotencyService).save(eq(userId), eq(idemKey), eq(201), any(), any());
    }

    @Test
    void deposit_duplicateKey_replaysWithoutCallingService() {
        DepositRequest request = mock(DepositRequest.class);
        IdempotencyKey cached = IdempotencyKey.builder()
                .key(userId + ":" + idemKey).user(user)
                .responseBody("{\"id\":\"abc\"}").responseStatus(201)
                .expiresAt(LocalDateTime.now().plusHours(1)).build();

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.of(cached));
        when(idempotencyService.deserialize(cached.getResponseBody())).thenReturn(successResponse);

        TransactionResponse result = moneyOperationService.deposit(userId, accountId, request, idemKey);

        assertThat(result).isEqualTo(successResponse);
        // No DB transaction should be started — service must NOT be called
        verifyNoInteractions(moneyTransactionService);
    }

    @Test
    void deposit_accountNotFound_throwsAccountNotFoundException() {
        DepositRequest request = mock(DepositRequest.class);
        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moneyOperationService.deposit(userId, accountId, request, idemKey))
                .isInstanceOf(AccountNotFoundException.class);
    }

    // ─── WITHDRAW ────────────────────────────────────────────────────────────

    @Test
    void withdraw_success_callsExternalAuditBeforeTransaction() {
        WithdrawRequest request = mock(WithdrawRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("100.00"));

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(externalAuditService.audit(any(), any(), any(), any(), any()))
                .thenReturn(ExternalCallStatus.SUCCESS);
        TransactionResponse withdrawResponse = TransactionResponse.builder()
                .id(UUID.randomUUID()).accountId(accountId).type(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("100.00")).currency(Currency.EUR)
                .balanceBefore(new BigDecimal("500.00")).balanceAfter(new BigDecimal("400.00"))
                .status(TransactionStatus.SUCCESS).correlationId(UUID.randomUUID())
                .externalCallStatus(ExternalCallStatus.SUCCESS).createdAt(LocalDateTime.now()).build();
        when(moneyTransactionService.executeWithdraw(accountId, userId, new BigDecimal("100.00"),
                null, idemKey, ExternalCallStatus.SUCCESS)).thenReturn(withdrawResponse);
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        TransactionResponse result = moneyOperationService.withdraw(userId, accountId, request, idemKey);

        assertThat(result.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        // External audit must be called before the DB transaction
        verify(externalAuditService).audit(userId, accountId, "WITHDRAWAL", new BigDecimal("100.00"), idemKey);
    }

    @Test
    void withdraw_insufficientFunds_savesFailedTransactionAndRethrows() {
        WithdrawRequest request = mock(WithdrawRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("9999.00"));

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));
        when(externalAuditService.audit(any(), any(), any(), any(), any()))
                .thenReturn(ExternalCallStatus.SUCCESS);
        when(moneyTransactionService.executeWithdraw(any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientFundsException(accountId, new BigDecimal("9999.00"), new BigDecimal("500.00")));

        assertThatThrownBy(() -> moneyOperationService.withdraw(userId, accountId, request, idemKey))
                .isInstanceOf(InsufficientFundsException.class);

        // Verify FAILED transaction was persisted in independent REQUIRES_NEW transaction
        verify(moneyTransactionService).saveFailedTransaction(
                eq(accountId), eq(userId), eq(TransactionType.WITHDRAWAL),
                eq(new BigDecimal("9999.00")), any(), eq(idemKey), any(), eq(ExternalCallStatus.SUCCESS));
    }
}
