package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.request.CreateAccountRequest;
import com.example.finance_app.bank.dto.response.AccountResponse;
import com.example.finance_app.bank.enums.AccountStatus;
import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.exception.AccountNotFoundException;
import com.example.finance_app.bank.model.Account;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock UserRepository userRepository;
    @InjectMocks AccountService accountService;

    private final UUID userId    = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().id(userId).username("alice").email("a@a.com").password("x").build();
    }

    // ─── CREATE ACCOUNT ───────────────────────────────────────────────────────

    @Test
    void createAccount_success_returnsFullyPopulatedResponse() {
        CreateAccountRequest request = mock(CreateAccountRequest.class);
        when(request.getAccountName()).thenReturn("My EUR Account");
        when(request.getCurrency()).thenReturn(Currency.EUR);

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        // nextAccountNumber() is a native sequence query — must be mocked
        when(accountRepository.nextAccountNumber()).thenReturn(1000000001L);

        Account saved = Account.builder()
                .id(accountId)
                .accountNumber("1000000001")
                .user(user)
                .accountName("My EUR Account")
                .currency(Currency.EUR)
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build();
        when(accountRepository.save(any())).thenReturn(saved);

        AccountResponse response = accountService.createAccount(userId, request);

        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getAccountNumber()).isEqualTo("1000000001");
        assertThat(response.getAccountName()).isEqualTo("My EUR Account");
        assertThat(response.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void createAccount_differentCurrencies_eachReturnCorrectCurrency() {
        for (Currency currency : new Currency[]{Currency.USD, Currency.SEK, Currency.GBP, Currency.VND}) {
            CreateAccountRequest request = mock(CreateAccountRequest.class);
            when(request.getAccountName()).thenReturn("Test");
            when(request.getCurrency()).thenReturn(currency);

            when(userRepository.getReferenceById(userId)).thenReturn(user);
            when(accountRepository.nextAccountNumber()).thenReturn(1000000002L);

            Account saved = Account.builder()
                    .id(UUID.randomUUID()).accountNumber("1000000002")
                    .user(user).accountName("Test").currency(currency)
                    .balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();
            when(accountRepository.save(any())).thenReturn(saved);

            AccountResponse response = accountService.createAccount(userId, request);
            assertThat(response.getCurrency()).isEqualTo(currency);
        }
    }

    @Test
    void createAccount_newAccount_balanceIsAlwaysZero() {
        CreateAccountRequest request = mock(CreateAccountRequest.class);
        when(request.getAccountName()).thenReturn("Savings");
        when(request.getCurrency()).thenReturn(Currency.EUR);

        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(accountRepository.nextAccountNumber()).thenReturn(1000000003L);

        Account saved = Account.builder()
                .id(accountId).accountNumber("1000000003").user(user)
                .accountName("Savings").currency(Currency.EUR)
                .balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();
        when(accountRepository.save(any())).thenReturn(saved);

        AccountResponse response = accountService.createAccount(userId, request);

        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    // ─── GET ACCOUNT ─────────────────────────────────────────────────────────

    @Test
    void getAccount_ownedByUser_returnsResponse() {
        Account account = Account.builder()
                .id(accountId).accountNumber("1000000004").user(user)
                .currency(Currency.USD).balance(new BigDecimal("250.00"))
                .status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getAccount(accountId, userId);

        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getAccountNumber()).isEqualTo("1000000004");
        assertThat(response.getCurrency()).isEqualTo(Currency.USD);
        assertThat(response.getBalance()).isEqualByComparingTo("250.00");
    }

    @Test
    void getAccount_notOwnedByUser_throwsAccountNotFoundException() {
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(accountId, userId))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(accountId.toString());
    }

    @Test
    void getAccount_wrongUser_throwsAccountNotFoundException() {
        // Simulate a different user trying to access alice's account
        UUID differentUserId = UUID.randomUUID();
        when(accountRepository.findByIdAndUser_Id(accountId, differentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(accountId, differentUserId))
                .isInstanceOf(AccountNotFoundException.class);
        // Verify ownership query was called — NOT a query without the user filter
        verify(accountRepository).findByIdAndUser_Id(accountId, differentUserId);
        verify(accountRepository, never()).findById(any());
    }

    // ─── GET ALL ACCOUNTS ─────────────────────────────────────────────────────

    @Test
    void getAccounts_noAccounts_returnsEmptyList() {
        when(accountRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<AccountResponse> result = accountService.getAccounts(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void getAccounts_multipleAccounts_returnsAllMapped() {
        Account a1 = Account.builder().id(UUID.randomUUID()).accountNumber("1000000005")
                .user(user).currency(Currency.EUR).balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE).build();
        Account a2 = Account.builder().id(UUID.randomUUID()).accountNumber("1000000006")
                .user(user).currency(Currency.USD).balance(new BigDecimal("100.00"))
                .status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of(a1, a2));

        List<AccountResponse> result = accountService.getAccounts(userId);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AccountResponse::getCurrency)
                .containsExactly(Currency.EUR, Currency.USD);
        assertThat(result).extracting(AccountResponse::getAccountNumber)
                .containsExactly("1000000005", "1000000006");
    }

    @Test
    void getAccounts_accountNumberIncludedInEveryResponse() {
        Account account = Account.builder().id(accountId).accountNumber("1000000007")
                .user(user).currency(Currency.SEK).balance(new BigDecimal("999.00"))
                .status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of(account));

        List<AccountResponse> result = accountService.getAccounts(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAccountNumber()).isEqualTo("1000000007");
    }
}
