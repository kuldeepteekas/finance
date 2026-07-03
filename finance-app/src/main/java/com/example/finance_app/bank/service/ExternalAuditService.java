package com.example.finance_app.bank.service;

import com.example.finance_app.bank.enums.ExternalCallStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalAuditService {

    private final RestClient auditRestClient;

    // Called BEFORE opening the DB transaction — keeps the lock window (SELECT FOR UPDATE → COMMIT)
    // as short as possible. External HTTP latency would otherwise hold the row lock for seconds.
    //
    // This is a best-effort audit: if the call fails, we record FAILED status and proceed.
    // The money operation is not blocked by audit failures.
    public ExternalCallStatus audit(UUID userId, UUID accountId, String operation,
                                    BigDecimal amount, String idempotencyKey) {
        try {
            Map<String, Object> payload = Map.of(
                    "userId", userId.toString(),
                    "accountId", accountId.toString(),
                    "operation", operation,
                    "amount", amount,
                    "idempotencyKey", idempotencyKey,
                    "timestamp", LocalDateTime.now().toString()
            );

            auditRestClient.post()
                    .uri("/audit/events")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("External audit succeeded for accountId={}, operation={}", accountId, operation);
            return ExternalCallStatus.SUCCESS;

        } catch (Exception e) {
            // Connection failure, timeout, 4xx/5xx — all treated the same: record FAILED, proceed.
            log.warn("External audit call failed for accountId={}, operation={}: {}",
                    accountId, operation, e.getMessage());
            return ExternalCallStatus.FAILED;
        }
    }
}
