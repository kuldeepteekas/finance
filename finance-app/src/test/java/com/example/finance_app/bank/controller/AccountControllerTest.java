package com.example.finance_app.bank.controller;

import com.example.finance_app.bank.dto.response.AccountResponse;
import com.example.finance_app.bank.enums.AccountStatus;
import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.exception.AccountNotFoundException;
import com.example.finance_app.bank.model.User;
import com.example.finance_app.bank.security.CustomUserDetails;
import com.example.finance_app.bank.security.CustomUserDetailsService;
import com.example.finance_app.bank.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class AccountControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AccountService accountService;
    // Required to satisfy SecurityConfig which depends on CustomUserDetailsService
    @MockitoBean CustomUserDetailsService customUserDetailsService;

    private CustomUserDetails mockUserDetails;
    private final UUID userId    = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(userId).username("alice").email("alice@test.com").password("ignored").build();
        mockUserDetails = new CustomUserDetails(user);
    }

    @Test
    void createAccount_validRequest_returns201() throws Exception {
        AccountResponse response = AccountResponse.builder()
                .id(accountId).accountName("My EUR").currency(Currency.EUR)
                .balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();

        when(accountService.createAccount(any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/accounts")
                        .with(user(mockUserDetails))         // inject CustomUserDetails principal
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"EUR\",\"accountName\":\"My EUR\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.balance").value(0));
    }

    @Test
    void createAccount_missingCurrency_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .with(user(mockUserDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))                      // currency is @NotNull → validation fails
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void getAccount_exists_returns200() throws Exception {
        AccountResponse response = AccountResponse.builder()
                .id(accountId).currency(Currency.USD)
                .balance(new BigDecimal("250.00")).status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();

        when(accountService.getAccount(accountId, userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .with(user(mockUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balance").value(250.00));
    }

    @Test
    void getAccount_notFound_returns404() throws Exception {
        when(accountService.getAccount(accountId, userId))
                .thenThrow(new AccountNotFoundException(accountId));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                        .with(user(mockUserDetails)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void getAccounts_returnsAll() throws Exception {
        AccountResponse a1 = AccountResponse.builder().id(UUID.randomUUID())
                .currency(Currency.EUR).balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();
        AccountResponse a2 = AccountResponse.builder().id(UUID.randomUUID())
                .currency(Currency.USD).balance(BigDecimal.ZERO).status(AccountStatus.ACTIVE)
                .createdAt(LocalDateTime.now()).build();

        when(accountService.getAccounts(userId)).thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/api/v1/accounts").with(user(mockUserDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getAccount_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)) // no .with(user(...))
                .andExpect(status().isUnauthorized());
    }
}
