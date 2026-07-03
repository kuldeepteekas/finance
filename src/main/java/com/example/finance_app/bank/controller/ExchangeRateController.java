package com.example.finance_app.bank.controller;

import com.example.finance_app.bank.dto.response.ExchangeRateResponse;
import com.example.finance_app.bank.enums.Currency;
import com.example.finance_app.bank.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/exchange-rates")
@RequiredArgsConstructor
public class ExchangeRateController {

    private final ExchangeRateService exchangeRateService;

    // GET /api/v1/exchange-rates?from=EUR&to=USD  → single pair rate
    // GET /api/v1/exchange-rates?from=EUR          → all rates from EUR
    @GetMapping
    public ResponseEntity<?> getRates(
            @RequestParam Currency from,
            @RequestParam(required = false) Currency to) {

        if (to != null) {
            ExchangeRateResponse rate = exchangeRateService.getRate(from, to);
            return ResponseEntity.ok(rate);
        }

        List<ExchangeRateResponse> rates = exchangeRateService.getRatesFrom(from);
        return ResponseEntity.ok(rates);
    }
}
