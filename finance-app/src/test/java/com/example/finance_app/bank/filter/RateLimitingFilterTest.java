package com.example.finance_app.bank.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the per-user token-bucket rate limiting filter.
 *
 * Bucket capacity is set to 2 per test instance so we can exhaust it quickly
 * without waiting for real time-based refills.
 */
class RateLimitingFilterTest {

    private static final int TEST_CAPACITY = 2;

    private RateLimitingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter();
        // @Value fields won't be injected outside Spring — set them via reflection
        ReflectionTestUtils.setField(filter, "capacity", TEST_CAPACITY);
        ReflectionTestUtils.setField(filter, "refillPerMinute", TEST_CAPACITY);
    }

    // ─── NON-MONEY ENDPOINTS ─────────────────────────────────────────────────

    @Test
    void nonMoneyEndpoint_get_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/accounts");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, res, (r, rs) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isTrue();
        assertThat(res.getStatus()).isNotEqualTo(429);
    }

    @Test
    void moneyEndpoint_getMethod_notRateLimited() throws Exception {
        // Only POST is rate-limited — GET on a money path is not a money operation
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/accounts/abc/deposit");
        req.addHeader("Authorization", basicAuth("alice", "pass"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, res, (r, rs) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isTrue();
    }

    // ─── NO AUTH HEADER ───────────────────────────────────────────────────────

    @Test
    void moneyEndpoint_noAuthHeader_passesThrough() throws Exception {
        // No auth header → filter lets it through; Spring Security will return 401
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/accounts/abc/deposit");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, res, (r, rs) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isTrue();
    }

    @Test
    void moneyEndpoint_malformedAuthHeader_passesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/accounts/abc/withdraw");
        req.addHeader("Authorization", "Bearer some-token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, res, (r, rs) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isTrue();
    }

    // ─── WITHIN RATE LIMIT ────────────────────────────────────────────────────

    @Test
    void moneyEndpoint_withinRateLimit_allRequestsPassThrough() throws Exception {
        String auth = basicAuth("alice", "pass");

        for (int i = 0; i < TEST_CAPACITY; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/accounts/abc/deposit");
            req.addHeader("Authorization", auth);
            MockHttpServletResponse res = new MockHttpServletResponse();
            AtomicBoolean chainCalled = new AtomicBoolean(false);

            filter.doFilter(req, res, (r, rs) -> chainCalled.set(true));

            assertThat(chainCalled.get()).as("Request %d should pass within limit", i + 1).isTrue();
            assertThat(res.getStatus()).isNotEqualTo(429);
        }
    }

    // ─── RATE LIMIT EXCEEDED ─────────────────────────────────────────────────

    @Test
    void moneyEndpoint_exceedsRateLimit_returns429WithErrorBody() throws Exception {
        String auth = basicAuth("bob", "pass");

        // Exhaust the full capacity
        for (int i = 0; i < TEST_CAPACITY; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/accounts/abc/deposit");
            req.addHeader("Authorization", auth);
            filter.doFilter(req, new MockHttpServletResponse(), (r, rs) -> {});
        }

        // Next request must be rejected
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/accounts/abc/withdraw");
        req.addHeader("Authorization", auth);
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, res, (r, rs) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isFalse();
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getContentType()).contains("application/json");
        assertThat(res.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
    }

    @Test
    void moneyEndpoint_allFourMoneyEndpointsShareSameBucket() throws Exception {
        // deposit + withdraw share the same bucket for a user — mixed endpoints count together
        String auth = basicAuth("carol", "pass");
        String[] endpoints = {"/api/v1/accounts/abc/deposit", "/api/v1/accounts/abc/withdraw"};

        // Make capacity requests (split across endpoints)
        for (int i = 0; i < TEST_CAPACITY; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", endpoints[i % endpoints.length]);
            req.addHeader("Authorization", auth);
            filter.doFilter(req, new MockHttpServletResponse(), (r, rs) -> {});
        }

        // Now any money endpoint should be blocked
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/accounts/abc/transfer");
        req.addHeader("Authorization", auth);
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, (r, rs) -> {});

        assertThat(res.getStatus()).isEqualTo(429);
    }

    // ─── PER-USER ISOLATION ───────────────────────────────────────────────────

    @Test
    void rateLimitIsPerUser_differentUsersHaveIndependentBuckets() throws Exception {
        // Exhaust "alice"'s bucket
        String aliceAuth = basicAuth("alice", "pass");
        for (int i = 0; i < TEST_CAPACITY; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/accounts/abc/deposit");
            req.addHeader("Authorization", aliceAuth);
            filter.doFilter(req, new MockHttpServletResponse(), (r, rs) -> {});
        }

        // "dave" must still have his own fresh bucket — his requests should pass
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/accounts/abc/deposit");
        req.addHeader("Authorization", basicAuth("dave", "pass"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(req, res, (r, rs) -> chainCalled.set(true));

        assertThat(chainCalled.get()).isTrue();
        assertThat(res.getStatus()).isNotEqualTo(429);
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────────

    private String basicAuth(String username, String password) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }
}
