package com.example.finance_app.bank.exception;

import com.example.finance_app.bank.dto.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 404 — account not found or belongs to a different user
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotFound(AccountNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", ex.getMessage());
    }

    // 422 — insufficient balance (operation well-formed but cannot be fulfilled)
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", ex.getMessage());
    }

    // 400 — no exchange rate on record for the requested currency pair
    @ExceptionHandler(ExchangeRateNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleExchangeRateNotFound(ExchangeRateNotFoundException ex) {
        return build(HttpStatus.BAD_REQUEST, "EXCHANGE_RATE_NOT_FOUND", ex.getMessage());
    }

    // 400 — /exchange called with two accounts that have the same currency
    @ExceptionHandler(SameCurrencyExchangeException.class)
    public ResponseEntity<ApiErrorResponse> handleSameCurrencyExchange(SameCurrencyExchangeException ex) {
        return build(HttpStatus.BAD_REQUEST, "SAME_CURRENCY_EXCHANGE", ex.getMessage());
    }

    // 400 — required header missing (e.g. Idempotency-Key not provided)
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "Required header missing: " + ex.getHeaderName());
    }

    // 400 — cursor value is malformed or tampered
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_INPUT", ex.getMessage());
    }

    // 400 — @Valid annotation failures (e.g. missing currency, name too long)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, "INVALID_INPUT", message);
    }

    // 400 — invalid JSON body or unrecognised enum value (e.g. currency: "XYZ")
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "INVALID_INPUT",
                "Invalid request body or unsupported value");
    }

    // 500 — anything unexpected
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred");
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message));
    }
}
