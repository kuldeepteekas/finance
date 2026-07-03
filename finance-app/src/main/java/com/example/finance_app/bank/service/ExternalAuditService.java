package com.example.finance_app.bank.service;

import com.example.finance_app.bank.enums.ExternalCallStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalAuditService {

    private final RestClient auditRestClient;

    private static final Random RANDOM = new Random();

    // Delays used to simulate real-world external service behaviour via httpbin.org/delay/{n}.
    // Client timeout is 3 s, so:
    //   0 s → immediate success          (60 % of calls)
    //   2 s → slow but within timeout    (25 % of calls)
    //  10 s → exceeds timeout → TIMED_OUT (15 % of calls)
    private static final int[] DELAYS_SECONDS  = { 0, 0, 0, 0, 0, 0, 2, 2, 2, 10 };

    // Called BEFORE opening the DB transaction — keeps the lock window (SELECT FOR UPDATE → COMMIT)
    // as short as possible. External HTTP latency would otherwise hold the row lock for seconds.
    //
    // Best-effort: the money operation proceeds regardless of audit outcome.
    // We wait up to the configured read-timeout (3 s), then give up and record TIMED_OUT.
    public ExternalCallStatus audit(UUID userId, UUID accountId, String operation,
                                    BigDecimal amount, String idempotencyKey) {

        int delaySec = DELAYS_SECONDS[RANDOM.nextInt(DELAYS_SECONDS.length)];
        String uri   = "/delay/" + delaySec;

        log.info("External audit → POST httpbin.org{}  accountId={} operation={} (expected delay={}s)",
                uri, accountId, operation, delaySec);

        try {
            Map<String, Object> payload = Map.of(
                    "userId",         userId.toString(),
                    "accountId",      accountId.toString(),
                    "operation",      operation,
                    "amount",         amount,
                    "idempotencyKey", idempotencyKey,
                    "timestamp",      LocalDateTime.now().toString()
            );

            auditRestClient.post()
                    .uri(uri)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("External audit SUCCESS  accountId={} operation={} delay={}s",
                    accountId, operation, delaySec);
            return ExternalCallStatus.SUCCESS;

        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof HttpTimeoutException || isTimeoutCause(cause)) {
                log.warn("External audit TIMED OUT accountId={} operation={} (delay={}s exceeded client timeout) — proceeding with debit",
                        accountId, operation, delaySec);
                return ExternalCallStatus.TIMED_OUT;
            }
            log.warn("External audit FAILED (connection error) accountId={} operation={}: {}",
                    accountId, operation, e.getMessage());
            return ExternalCallStatus.FAILED;

        } catch (Exception e) {
            log.warn("External audit FAILED accountId={} operation={}: {}",
                    accountId, operation, e.getMessage());
            return ExternalCallStatus.FAILED;
        }
    }

    private boolean isTimeoutCause(Throwable t) {
        if (t == null) return false;
        if (t instanceof java.net.SocketTimeoutException) return true;
        if (t.getClass().getName().contains("Timeout")) return true;
        return isTimeoutCause(t.getCause());
    }
}
