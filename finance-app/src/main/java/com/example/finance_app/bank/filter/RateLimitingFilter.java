package com.example.finance_app.bank.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies a per-user token-bucket rate limit to money-mutation endpoints
 * (deposit, withdraw, transfer, exchange).
 *
 * Ordering: runs after Spring Security (order -100) so only authenticated
 * requests consume tokens. Unauthenticated requests pass through — Spring
 * Security returns 401 before they reach a controller anyway.
 *
 * In-memory buckets are keyed by username. For a multi-instance deployment
 * replace the ConcurrentHashMap with a distributed store (e.g. Redis via
 * bucket4j-redis).
 */
@Component
@Order(10)
public class RateLimitingFilter extends OncePerRequestFilter {

    @Value("${app.rate-limit.capacity:20}")
    private int capacity;

    @Value("${app.rate-limit.refill-per-minute:20}")
    private int refillPerMinute;

    // One bucket per authenticated user — created lazily on first request
    private final Map<String, Bucket> userBuckets = new ConcurrentHashMap<>();

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> RATE_LIMITED_PATTERNS = List.of(
        "/api/v1/accounts/*/deposit",
        "/api/v1/accounts/*/withdraw",
        "/api/v1/accounts/*/transfer",
        "/api/v1/accounts/*/exchange"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws IOException, ServletException {

        if (!isMoneyEndpoint(request)) {
            chain.doFilter(request, response);
            return;
        }

        String username = extractUsername(request);
        if (username == null) {
            // No valid Basic Auth header — Spring Security will return 401
            chain.doFilter(request, response);
            return;
        }

        Bucket bucket = userBuckets.computeIfAbsent(username, this::createBucket);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(rateLimitErrorJson());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private boolean isMoneyEndpoint(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return false;
        String uri = request.getRequestURI();
        return RATE_LIMITED_PATTERNS.stream().anyMatch(p -> pathMatcher.match(p, uri));
    }

    /** Decodes the Basic Auth header and returns the username, or null if absent/invalid. */
    private String extractUsername(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Basic ")) return null;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring(6)));
            int colon = decoded.indexOf(':');
            return colon > 0 ? decoded.substring(0, colon) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Creates a new greedy-refill token bucket for the given user. */
    private Bucket createBucket(String username) {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(refillPerMinute, Duration.ofMinutes(1))
                .build())
            .build();
    }

    /** Matches the ApiErrorResponse JSON shape used by GlobalExceptionHandler. */
    private String rateLimitErrorJson() {
        return String.format(
            "{\"error\":{\"code\":\"RATE_LIMIT_EXCEEDED\","
            + "\"message\":\"Too many requests. Please slow down and try again.\","
            + "\"timestamp\":\"%s\"}}",
            LocalDateTime.now()
        );
    }
}
