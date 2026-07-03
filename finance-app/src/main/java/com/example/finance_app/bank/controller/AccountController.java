package com.example.finance_app.bank.controller;

import com.example.finance_app.bank.dto.request.CreateAccountRequest;
import com.example.finance_app.bank.dto.response.AccountResponse;
import com.example.finance_app.bank.security.CustomUserDetails;
import com.example.finance_app.bank.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateAccountRequest request) {

        AccountResponse response = accountService.createAccount(userDetails.getUserId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAccounts(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        return ResponseEntity.ok(accountService.getAccounts(userDetails.getUserId()));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID accountId) {

        return ResponseEntity.ok(accountService.getAccount(accountId, userDetails.getUserId()));
    }
}
