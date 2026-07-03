package com.example.finance_app.bank.service;

import com.example.finance_app.bank.dto.response.TransactionPageResponse;
import com.example.finance_app.bank.dto.response.TransactionResponse;
import com.example.finance_app.bank.exception.AccountNotFoundException;
import com.example.finance_app.bank.model.Transaction;
import com.example.finance_app.bank.repository.AccountRepository;
import com.example.finance_app.bank.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    // Hard cap — clients can request fewer but never more
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final String CURSOR_DELIMITER = "|";

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;

    @Transactional(readOnly = true)
    public TransactionPageResponse getTransactions(UUID accountId, UUID userId,
                                                   String cursor, Integer size) {
        // Ownership check — 404 if account doesn't belong to this user
        accountRepository.findByIdAndUser_Id(accountId, userId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));

        int pageSize = resolvePageSize(size);
        // Fetch one extra record to detect whether a next page exists
        PageRequest pageable = PageRequest.of(0, pageSize + 1);

        List<Transaction> rows;
        if (cursor == null || cursor.isBlank()) {
            rows = transactionRepository.findFirstPage(accountId, userId, pageable);
        } else {
            CursorParts parts = decodeCursor(cursor);
            rows = transactionRepository.findNextPage(
                    accountId, userId, parts.createdAt(), parts.id(), pageable);
        }

        // If we got pageSize+1 rows, there's another page — build nextCursor from the last item
        // in the actual page (index pageSize-1), NOT from the extra item (index pageSize).
        boolean hasNextPage = rows.size() > pageSize;
        List<Transaction> page = hasNextPage ? rows.subList(0, pageSize) : rows;

        String nextCursor = hasNextPage
                ? encodeCursor(page.get(page.size() - 1))
                : null;

        return TransactionPageResponse.builder()
                .transactions(page.stream().map(this::toResponse).toList())
                .nextCursor(nextCursor)
                .build();
    }

    // ─── Cursor encoding / decoding ──────────────────────────────────────────

    // Cursor format (before base64): "{createdAt ISO}|{uuid}"
    // Opaque to the client — they treat it as a black box and pass it back verbatim.
    private String encodeCursor(Transaction last) {
        String raw = last.getCreatedAt().toString() + CURSOR_DELIMITER + last.getId().toString();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private CursorParts decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            int split = raw.lastIndexOf(CURSOR_DELIMITER);
            if (split < 0) throw new IllegalArgumentException("malformed cursor");
            LocalDateTime createdAt = LocalDateTime.parse(raw.substring(0, split));
            UUID id = UUID.fromString(raw.substring(split + 1));
            return new CursorParts(createdAt, id);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor value");
        }
    }

    private record CursorParts(LocalDateTime createdAt, UUID id) {}

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private int resolvePageSize(Integer requested) {
        if (requested == null || requested <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(requested, MAX_PAGE_SIZE);
    }

    private TransactionResponse toResponse(Transaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .accountId(tx.getAccount().getId())
                .type(tx.getType())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .balanceBefore(tx.getBalanceBefore())
                .balanceAfter(tx.getBalanceAfter())
                .status(tx.getStatus())
                .description(tx.getDescription())
                .failureReason(tx.getFailureReason())
                .correlationId(tx.getCorrelationId())
                .counterpartyAccountId(tx.getCounterpartyAccountId())
                .idempotencyKey(tx.getIdempotencyKey())
                .externalCallStatus(tx.getExternalCallStatus())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
