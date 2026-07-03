package com.example.finance_app.bank.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TransactionPageResponse {

    private List<TransactionResponse> transactions;

    // Opaque base64 cursor — pass as ?cursor= on the next request to get the next page.
    // Null means this is the last page (no more results).
    private String nextCursor;
}
