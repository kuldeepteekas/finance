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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock UserRepository userRepository;
    @InjectMocks AccountService accountService;

    private final UUID userId    = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @Test
    void createAccount_success_returnsResponse() {
        CreateAccountRequest request = mock(CreateAccountRequest.class);
        when(request.getAccountName()).thenReturn("My EUR account");
        when(request.getCurrency()).thenReturn(Currency.EUR);

        User userProxy = User.builder().id(userId).username("alice").email("a@a.com").password("x").build();
        when(userRepository.getReferenceById(userId)).thenReturn(userProxy);

        Account saved = Account.builder()
                .id(accountId)
                .user(userProxy)
                .accountName("My EUR account")
                .currency(Currency.EUR)
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build();
        when(accountRepository.save(any())).thenReturn(saved);

        AccountResponse response = accountService.createAccount(userId, request);

        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getCurrency()).isEqualTo(Currency.EUR);
        assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void getAccount_exists_returnsResponse() {
        User user = User.builder().id(userId).username("alice").email("a@a.com").password("x").build();
        Account account = Account.builder()
                .id(accountId).user(user).currency(Currency.USD)
                .balance(new BigDecimal("250.00")).status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getAccount(accountId, userId);

        assertThat(response.getId()).isEqualTo(accountId);
        assertThat(response.getCurrency()).isEqualTo(Currency.USD);
    }

    @Test
    void getAccount_notFound_throwsAccountNotFoundException() {
        when(accountRepository.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getAccount(accountId, userId))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(accountId.toString());
    }

    @Test
    void getAccounts_noAccounts_returnsEmptyList() {
        when(accountRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        List<AccountResponse> result = accountService.getAccounts(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void getAccounts_multipleAccounts_returnsAll() {
        User user = User.builder().id(userId).username("alice").email("a@a.com").password("x").build();
        Account a1 = Account.builder().id(UUID.randomUUID()).user(user)
                .currency(Currency.EUR).balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();
        Account a2 = Account.builder().id(UUID.randomUUID()).user(user)
                .currency(Currency.USD).balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE).build();

        when(accountRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of(a1, a2));

        List<AccountResponse> result = accountService.getAccounts(userId);

        assertThat(result).hasSize(2);
    }
}
