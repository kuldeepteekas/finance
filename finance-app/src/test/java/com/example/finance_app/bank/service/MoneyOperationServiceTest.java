package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.request.DepositRequest;
import com.example.finance_app.bank.dto.request.ExchangeRequest;
import com.example.finance_app.bank.dto.request.TransferRequest;
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
import java.util.List;
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

    private final UUID userId      = UUID.randomUUID();
    private final UUID accountId   = UUID.randomUUID();
    private final UUID toAccountId = UUID.randomUUID();
    private final String idemKey   = UUID.randomUUID().toString();

    private User user;
    private Account fromAccount;
    private Account toAccount;
    private TransactionResponse depositResponse;
    private TransactionResponse withdrawResponse;
    private List<TransactionResponse> transferResponses;
    private List<TransactionResponse> exchangeResponses;

    @BeforeEach
    void setUp() {
        user = User.builder().id(userId).username("alice").email("a@a.com").password("x").build();

        fromAccount = Account.builder()
                .id(accountId).user(user).currency(Currency.EUR)
                .balance(new BigDecimal("500.00")).status(AccountStatus.ACTIVE).build();

        toAccount = Account.builder()
                .id(toAccountId).user(user).currency(Currency.USD)
                .balance(new BigDecimal("200.00")).status(AccountStatus.ACTIVE).build();

        depositResponse = TransactionResponse.builder()
                .id(UUID.randomUUID()).accountId(accountId).type(TransactionType.DEPOSIT)
                .amount(new BigDecimal("100.00")).currency(Currency.EUR)
                .balanceBefore(new BigDecimal("500.00")).balanceAfter(new BigDecimal("600.00"))
                .status(TransactionStatus.SUCCESS).correlationId(UUID.randomUUID())
                .externalCallStatus(ExternalCallStatus.SKIPPED).createdAt(LocalDateTime.now()).build();

        withdrawResponse = TransactionResponse.builder()
                .id(UUID.randomUUID()).accountId(accountId).type(TransactionType.WITHDRAWAL)
                .amount(new BigDecimal("100.00")).currency(Currency.EUR)
                .balanceBefore(new BigDecimal("500.00")).balanceAfter(new BigDecimal("400.00"))
                .status(TransactionStatus.SUCCESS).correlationId(UUID.randomUUID())
                .externalCallStatus(ExternalCallStatus.SUCCESS).createdAt(LocalDateTime.now()).build();

        transferResponses = List.of(
                TransactionResponse.builder()
                        .id(UUID.randomUUID()).accountId(accountId).type(TransactionType.TRANSFER_OUT)
                        .amount(new BigDecimal("100.00")).currency(Currency.EUR)
                        .balanceBefore(new BigDecimal("500.00")).balanceAfter(new BigDecimal("400.00"))
                        .status(TransactionStatus.SUCCESS).correlationId(UUID.randomUUID())
                        .externalCallStatus(ExternalCallStatus.SUCCESS).createdAt(LocalDateTime.now()).build(),
                TransactionResponse.builder()
                        .id(UUID.randomUUID()).accountId(toAccountId).type(TransactionType.TRANSFER_IN)
                        .amount(new BigDecimal("100.00")).currency(Currency.EUR)
                        .balanceBefore(new BigDecimal("200.00")).balanceAfter(new BigDecimal("300.00"))
                        .status(TransactionStatus.SUCCESS).correlationId(UUID.randomUUID())
                        .externalCallStatus(ExternalCallStatus.SKIPPED).createdAt(LocalDateTime.now()).build()
        );

        exchangeResponses = List.of(
                TransactionResponse.builder()
                        .id(UUID.randomUUID()).accountId(accountId).type(TransactionType.EXCHANGE_OUT)
                        .amount(new BigDecimal("100.00")).currency(Currency.EUR)
                        .balanceBefore(new BigDecimal("500.00")).balanceAfter(new BigDecimal("400.00"))
                        .status(TransactionStatus.SUCCESS).correlationId(UUID.randomUUID())
                        .externalCallStatus(ExternalCallStatus.SUCCESS).createdAt(LocalDateTime.now()).build(),
                TransactionResponse.builder()
                        .id(UUID.randomUUID()).accountId(toAccountId).type(TransactionType.EXCHANGE_IN)
                        .amount(new BigDecimal("108.00")).currency(Currency.USD)
                        .balanceBefore(new BigDecimal("200.00")).balanceAfter(new BigDecimal("308.00"))
                        .status(TransactionStatus.SUCCESS).correlationId(UUID.randomUUID())
                        .externalCallStatus(ExternalCallStatus.SKIPPED).createdAt(LocalDateTime.now()).build()
        );
    }

    // ─── DEPOSIT ─────────────────────────────────────────────────────────────

    @Test
    void deposit_success_returnsTransactionResponseAndStoresIdempotencyKey() {
        DepositRequest request = mock(DepositRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("100.00"));

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(fromAccount));
        when(moneyTransactionService.executeDeposit(accountId, userId, new BigDecimal("100.00"), null, idemKey))
                .thenReturn(depositResponse);
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        TransactionResponse result = moneyOperationService.deposit(userId, accountId, request, idemKey);

        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(result.getType()).isEqualTo(TransactionType.DEPOSIT);
        // Idempotency key must be persisted so duplicate requests are replayed
        verify(idempotencyService).save(eq(userId), eq(idemKey), eq(201), any(), any());
    }

    @Test
    void deposit_duplicateKey_replaysWithoutCallingService() {
        DepositRequest request = mock(DepositRequest.class);
        IdempotencyKey cached = IdempotencyKey.builder()
                .key(userId + ":" + idemKey).user(user).responseBody("{\"id\":\"abc\"}")
                .responseStatus(201).expiresAt(LocalDateTime.now().plusHours(1)).build();

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.of(cached));
        when(idempotencyService.deserialize(cached.getResponseBody())).thenReturn(depositResponse);

        TransactionResponse result = moneyOperationService.deposit(userId, accountId, request, idemKey);

        assertThat(result).isEqualTo(depositResponse);
        // Critical: the inner service must NOT be called on a replay
        verifyNoInteractions(moneyTransactionService);
    }

    @Test
    void deposit_accountNotFound_throwsAccountNotFoundException() {
        DepositRequest request = mock(DepositRequest.class);
        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moneyOperationService.deposit(userId, accountId, request, idemKey))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(moneyTransactionService);
    }

    // ─── WITHDRAW ────────────────────────────────────────────────────────────

    @Test
    void withdraw_success_callsExternalAuditBeforeDbTransaction() {
        WithdrawRequest request = mock(WithdrawRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("100.00"));

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(fromAccount));
        when(externalAuditService.audit(any(), any(), any(), any(), any()))
                .thenReturn(ExternalCallStatus.SUCCESS);
        when(moneyTransactionService.executeWithdraw(accountId, userId, new BigDecimal("100.00"),
                null, idemKey, ExternalCallStatus.SUCCESS)).thenReturn(withdrawResponse);
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        TransactionResponse result = moneyOperationService.withdraw(userId, accountId, request, idemKey);

        assertThat(result.getType()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(result.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        // The external audit call must happen — it's a core requirement
        verify(externalAuditService).audit(userId, accountId, "WITHDRAWAL", new BigDecimal("100.00"), idemKey);
    }

    @Test
    void withdraw_duplicateKey_replaysWithoutCallingService() {
        WithdrawRequest request = mock(WithdrawRequest.class);
        IdempotencyKey cached = IdempotencyKey.builder()
                .key(userId + ":" + idemKey).user(user).responseBody("{\"id\":\"abc\"}")
                .responseStatus(201).expiresAt(LocalDateTime.now().plusHours(1)).build();

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.of(cached));
        when(idempotencyService.deserialize(cached.getResponseBody())).thenReturn(withdrawResponse);

        TransactionResponse result = moneyOperationService.withdraw(userId, accountId, request, idemKey);

        assertThat(result).isEqualTo(withdrawResponse);
        verifyNoInteractions(moneyTransactionService, externalAuditService);
    }

    @Test
    void withdraw_insufficientFunds_savesFailedTransactionAndRethrows() {
        WithdrawRequest request = mock(WithdrawRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("9999.00"));

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(fromAccount));
        when(externalAuditService.audit(any(), any(), any(), any(), any()))
                .thenReturn(ExternalCallStatus.SUCCESS);
        when(moneyTransactionService.executeWithdraw(any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientFundsException(accountId, new BigDecimal("9999.00"), new BigDecimal("500.00")));

        assertThatThrownBy(() -> moneyOperationService.withdraw(userId, accountId, request, idemKey))
                .isInstanceOf(InsufficientFundsException.class);

        // The failed attempt must be persisted in its own REQUIRES_NEW transaction
        verify(moneyTransactionService).saveFailedTransaction(
                eq(accountId), eq(userId), eq(TransactionType.WITHDRAWAL),
                eq(new BigDecimal("9999.00")), any(), eq(idemKey), any(UUID.class), eq(ExternalCallStatus.SUCCESS));
    }

    @Test
    void withdraw_accountNotFound_throwsBeforeExternalAudit() {
        WithdrawRequest request = mock(WithdrawRequest.class);
        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moneyOperationService.withdraw(userId, accountId, request, idemKey))
                .isInstanceOf(AccountNotFoundException.class);
        // External audit must NOT be called if ownership check fails
        verifyNoInteractions(externalAuditService, moneyTransactionService);
    }

    // ─── TRANSFER ────────────────────────────────────────────────────────────

    @Test
    void transfer_success_returnsTwoTransactionResponses() {
        TransferRequest request = mock(TransferRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(request.getToAccountId()).thenReturn(toAccountId);

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdAndUser_Id(toAccountId, userId)).thenReturn(Optional.of(toAccount));
        when(externalAuditService.audit(any(), any(), any(), any(), any()))
                .thenReturn(ExternalCallStatus.SUCCESS);
        when(moneyTransactionService.executeTransfer(accountId, toAccountId, userId,
                new BigDecimal("100.00"), null, idemKey, ExternalCallStatus.SUCCESS))
                .thenReturn(transferResponses);
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        List<TransactionResponse> result = moneyOperationService.transfer(userId, accountId, request, idemKey);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getType()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(result.get(1).getType()).isEqualTo(TransactionType.TRANSFER_IN);
        verify(externalAuditService).audit(userId, accountId, "TRANSFER", new BigDecimal("100.00"), idemKey);
        verify(idempotencyService).saveList(eq(userId), eq(idemKey), eq(201), any(), any());
    }

    @Test
    void transfer_duplicateKey_replaysWithoutCallingService() {
        TransferRequest request = mock(TransferRequest.class);
        IdempotencyKey cached = IdempotencyKey.builder()
                .key(userId + ":" + idemKey).user(user).responseBody("[{},{\"id\":\"b\"}]")
                .responseStatus(201).expiresAt(LocalDateTime.now().plusHours(1)).build();

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.of(cached));
        when(idempotencyService.deserializeList(cached.getResponseBody())).thenReturn(transferResponses);

        List<TransactionResponse> result = moneyOperationService.transfer(userId, accountId, request, idemKey);

        assertThat(result).isEqualTo(transferResponses);
        verifyNoInteractions(moneyTransactionService, externalAuditService);
    }

    @Test
    void transfer_insufficientFunds_savesFailedTransactionAndRethrows() {
        TransferRequest request = mock(TransferRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("9999.00"));
        when(request.getToAccountId()).thenReturn(toAccountId);

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdAndUser_Id(toAccountId, userId)).thenReturn(Optional.of(toAccount));
        when(externalAuditService.audit(any(), any(), any(), any(), any()))
                .thenReturn(ExternalCallStatus.SUCCESS);
        when(moneyTransactionService.executeTransfer(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientFundsException(accountId, new BigDecimal("9999.00"), new BigDecimal("500.00")));

        assertThatThrownBy(() -> moneyOperationService.transfer(userId, accountId, request, idemKey))
                .isInstanceOf(InsufficientFundsException.class);

        verify(moneyTransactionService).saveFailedTransaction(
                eq(accountId), eq(userId), eq(TransactionType.TRANSFER_OUT),
                eq(new BigDecimal("9999.00")), any(), eq(idemKey), any(UUID.class), eq(ExternalCallStatus.SUCCESS));
    }

    @Test
    void transfer_toAccountNotOwnedByUser_throwsAccountNotFoundException() {
        TransferRequest request = mock(TransferRequest.class);
        when(request.getToAccountId()).thenReturn(toAccountId);

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdAndUser_Id(toAccountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moneyOperationService.transfer(userId, accountId, request, idemKey))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(externalAuditService, moneyTransactionService);
    }

    // ─── EXCHANGE ────────────────────────────────────────────────────────────

    @Test
    void exchange_success_returnsTwoTransactionResponses() {
        ExchangeRequest request = mock(ExchangeRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("100.00"));
        when(request.getToAccountId()).thenReturn(toAccountId);

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdAndUser_Id(toAccountId, userId)).thenReturn(Optional.of(toAccount));
        when(externalAuditService.audit(any(), any(), any(), any(), any()))
                .thenReturn(ExternalCallStatus.SUCCESS);
        when(moneyTransactionService.executeExchange(accountId, toAccountId, userId,
                new BigDecimal("100.00"), null, idemKey, ExternalCallStatus.SUCCESS))
                .thenReturn(exchangeResponses);
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        List<TransactionResponse> result = moneyOperationService.exchange(userId, accountId, request, idemKey);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getType()).isEqualTo(TransactionType.EXCHANGE_OUT);
        assertThat(result.get(1).getType()).isEqualTo(TransactionType.EXCHANGE_IN);
        verify(externalAuditService).audit(userId, accountId, "EXCHANGE", new BigDecimal("100.00"), idemKey);
        verify(idempotencyService).saveList(eq(userId), eq(idemKey), eq(201), any(), any());
    }

    @Test
    void exchange_duplicateKey_replaysWithoutCallingService() {
        ExchangeRequest request = mock(ExchangeRequest.class);
        IdempotencyKey cached = IdempotencyKey.builder()
                .key(userId + ":" + idemKey).user(user).responseBody("[{},{\"id\":\"b\"}]")
                .responseStatus(201).expiresAt(LocalDateTime.now().plusHours(1)).build();

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.of(cached));
        when(idempotencyService.deserializeList(cached.getResponseBody())).thenReturn(exchangeResponses);

        List<TransactionResponse> result = moneyOperationService.exchange(userId, accountId, request, idemKey);

        assertThat(result).isEqualTo(exchangeResponses);
        verifyNoInteractions(moneyTransactionService, externalAuditService);
    }

    @Test
    void exchange_insufficientFunds_savesFailedTransactionAndRethrows() {
        ExchangeRequest request = mock(ExchangeRequest.class);
        when(request.getAmount()).thenReturn(new BigDecimal("9999.00"));
        when(request.getToAccountId()).thenReturn(toAccountId);

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdAndUser_Id(toAccountId, userId)).thenReturn(Optional.of(toAccount));
        when(externalAuditService.audit(any(), any(), any(), any(), any()))
                .thenReturn(ExternalCallStatus.SUCCESS);
        when(moneyTransactionService.executeExchange(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new InsufficientFundsException(accountId, new BigDecimal("9999.00"), new BigDecimal("500.00")));

        assertThatThrownBy(() -> moneyOperationService.exchange(userId, accountId, request, idemKey))
                .isInstanceOf(InsufficientFundsException.class);

        verify(moneyTransactionService).saveFailedTransaction(
                eq(accountId), eq(userId), eq(TransactionType.EXCHANGE_OUT),
                eq(new BigDecimal("9999.00")), any(), eq(idemKey), any(UUID.class), eq(ExternalCallStatus.SUCCESS));
    }

    @Test
    void exchange_toAccountNotOwnedByUser_throwsAccountNotFoundException() {
        ExchangeRequest request = mock(ExchangeRequest.class);
        when(request.getToAccountId()).thenReturn(toAccountId);

        when(idempotencyService.findExistingKey(userId, idemKey)).thenReturn(Optional.empty());
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByIdAndUser_Id(toAccountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> moneyOperationService.exchange(userId, accountId, request, idemKey))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(externalAuditService, moneyTransactionService);
    }
}
