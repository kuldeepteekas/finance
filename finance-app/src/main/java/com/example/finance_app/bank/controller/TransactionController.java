package com.example.finance_app.bank.controller;

import com.example.finance_app.bank.dto.request.DepositRequest;
import com.example.finance_app.bank.dto.request.ExchangeRequest;
import com.example.finance_app.bank.dto.request.TransferRequest;
import com.example.finance_app.bank.dto.request.WithdrawRequest;
import com.example.finance_app.bank.dto.response.TransactionPageResponse;
import com.example.finance_app.bank.dto.response.TransactionResponse;
import com.example.finance_app.bank.security.CustomUserDetails;
import com.example.finance_app.bank.service.MoneyOperationService;
import com.example.finance_app.bank.service.TransactionHistoryService;
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
    private final TransactionHistoryService transactionHistoryService;

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

    // GET /api/v1/accounts/{accountId}/transactions?cursor=...&size=20
    // cursor is absent on the first request; pass nextCursor from the previous response to page forward.
    @GetMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionPageResponse> getTransactions(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID accountId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {

        return ResponseEntity.ok(transactionHistoryService
                .getTransactions(accountId, userDetails.getUserId(), cursor, size));
    }

    // POST /api/v1/accounts/{accountId}/transfer
    // Same-currency only. {accountId} is the source account; toAccountId is in the request body.
    // Header: Idempotency-Key: <uuid>
    @PostMapping("/{accountId}/transfer")
    public ResponseEntity<List<TransactionResponse>> transfer(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable UUID accountId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        List<TransactionResponse> responses = moneyOperationService
                .transfer(userDetails.getUserId(), accountId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(responses);
    }

    // POST /api/v1/accounts/{accountId}/exchange
    // Cross-currency only. {accountId} is the source (from) account; toAccountId is in the request body.
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
