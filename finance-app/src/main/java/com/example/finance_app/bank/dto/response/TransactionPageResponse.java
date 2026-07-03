package com.example.finance_app.bank.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TransactionPageResponse {

    private List<TransactionResponse> transactions;

    private String nextCursor;
}
