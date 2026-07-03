package com.example.finance_app.bank.controller;

import com.example.finance_app.bank.dto.request.DepositRequest;
import com.example.finance_app.bank.dto.request.ExchangeRequest;
import com.example.finance_app.bank.dto.request.WithdrawRequest;
import com.example.finance_app.bank.dto.response.TransactionResponse;
import com.example.finance_app.bank.security.CustomUserDetails;
import com.example.finance_app.bank.service.MoneyOperationService;
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
public class TransactionController {

    private final MoneyOperationService moneyOperationService;

    // POST /api/v1/accounts/{accountId}/deposit
    // Header: Idempotency-Key: <uuid>
    @PostMapping("/{accountId}/deposit")
    public ResponseEntity<TransactionResponse> deposit(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DepositRequest request) {

        TransactionResponse response = moneyOperationService
                .deposit(userDetails.getUserId(), accountId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST /api/v1/accounts/{accountId}/withdraw
    // Header: Idempotency-Key: <uuid>
    @PostMapping("/{accountId}/withdraw")
    public ResponseEntity<TransactionResponse> withdraw(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WithdrawRequest request) {

        TransactionResponse response = moneyOperationService
                .withdraw(userDetails.getUserId(), accountId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // POST /api/v1/accounts/{accountId}/exchange
    // {accountId} is the source (from) account; toAccountId is in the request body.
    // Header: Idempotency-Key: <uuid>
    @PostMapping("/{accountId}/exchange")
    public ResponseEntity<List<TransactionResponse>> exchange(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExchangeRequest request) {

        List<TransactionResponse> responses = moneyOperationService
                .exchange(userDetails.getUserId(), accountId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }
}
