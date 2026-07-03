package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.request.CreateAccountRequest;
import com.example.finance_app.bank.dto.response.AccountResponse;
import com.example.finance_app.bank.enums.AccountStatus;
import com.example.finance_app.bank.exception.AccountNotFoundException;
import com.example.finance_app.bank.model.Account;
import com.example.finance_app.bank.repository.AccountRepository;
import com.example.finance_app.bank.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    @Transactional
    public AccountResponse createAccount(UUID userId, CreateAccountRequest request) {
        String accountNumber = String.valueOf(accountRepository.nextAccountNumber());

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .user(userRepository.getReferenceById(userId)) // proxy — no extra DB query
                .accountName(request.getAccountName())
                .currency(request.getCurrency())
                .balance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build();

        return toResponse(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccounts(UUID userId) {
        return accountRepository.findByUser_IdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID accountId, UUID userId) {
        return accountRepository.findByIdAndUser_Id(accountId, userId)
                .map(this::toResponse)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
