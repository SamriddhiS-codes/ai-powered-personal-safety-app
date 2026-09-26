package com.ascenddevs.safetyapp.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * White-box tests for the sliding-window, risk-adaptive rate limiter.
 */
class AdaptiveRateLimiterTest {

    @Test
    void lowRiskUser_getsFullBaseLimit() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter();
        String key = "user-1";

        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.allowRequest(key, 0.0, 10, 60_000),
                    "request " + i + " should be allowed at zero risk");
        }
        assertFalse(limiter.allowRequest(key, 0.0, 10, 60_000),
                "11th request should exceed the base limit");
    }

    @Test
    void highRiskUser_getsThrottledHarder() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter();
        String key = "user-2";

        // riskScore 0.8 on a base limit of 10 -> effective limit = round(10 * 0.2) = 2
        assertTrue(limiter.allowRequest(key, 0.8, 10, 60_000));
        assertTrue(limiter.allowRequest(key, 0.8, 10, 60_000));
        assertFalse(limiter.allowRequest(key, 0.8, 10, 60_000),
                "3rd request should be throttled under high risk");
    }

    @Test
    void maxRisk_stillAllowsAtLeastOneRequest() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter();
        String key = "user-3";

        assertTrue(limiter.allowRequest(key, 1.0, 10, 60_000),
                "even at maximum risk, one request should get through (fail-safe, not fail-closed)");
        assertFalse(limiter.allowRequest(key, 1.0, 10, 60_000));
    }

    @Test
    void windowExpiry_resetsCount() throws InterruptedException {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter();
        String key = "user-4";
        long shortWindowMs = 100;

        assertTrue(limiter.allowRequest(key, 0.0, 1, shortWindowMs));
        assertFalse(limiter.allowRequest(key, 0.0, 1, shortWindowMs));

        Thread.sleep(shortWindowMs + 50);

        assertTrue(limiter.allowRequest(key, 0.0, 1, shortWindowMs),
                "request should be allowed again once the window has passed");
    }

    @Test
    void differentKeys_areTrackedIndependently() {
        AdaptiveRateLimiter limiter = new AdaptiveRateLimiter();

        assertTrue(limiter.allowRequest("user-A", 0.0, 1, 60_000));
        assertFalse(limiter.allowRequest("user-A", 0.0, 1, 60_000));
        assertTrue(limiter.allowRequest("user-B", 0.0, 1, 60_000),
                "a different key must not be affected by user-A's limit");
    }
}
