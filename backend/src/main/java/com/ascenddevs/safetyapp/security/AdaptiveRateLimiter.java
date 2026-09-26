package com.ascenddevs.safetyapp.security;

import org.springframework.stereotype.Component;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Sliding-window rate limiter whose effective limit is a function of a live
 * risk score, not a fixed per-IP/per-user constant. A user behaving normally
 * gets the full base limit; a user whose session looks risky (new device,
 * unusual velocity, repeated failed check-ins) gets throttled harder — without
 * being blocked outright, which would create a denial-of-service against
 * genuine users.
 *
 * riskScore is expected in [0.0, 1.0], where 0.0 = no risk, 1.0 = maximum risk.
 */
@Component
public class AdaptiveRateLimiter {

    private final ConcurrentHashMap<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();

    /**
     * @param key         identifies the caller (userId, sessionId, or IP)
     * @param riskScore   current risk score for this caller, 0.0 (safe) to 1.0 (highest risk)
     * @param baseLimit   requests allowed per window at zero risk
     * @param windowMs    sliding window size in milliseconds
     * @return true if the request is allowed, false if it should be throttled
     */
    public boolean allowRequest(String key, double riskScore, int baseLimit, long windowMs) {
        double clampedRisk = Math.max(0.0, Math.min(1.0, riskScore));
        int effectiveLimit = Math.max(1, (int) Math.round(baseLimit * (1 - clampedRisk)));

        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestLog.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= effectiveLimit) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    /** Clears tracked history for a key — mainly useful for tests. */
    public void reset(String key) {
        requestLog.remove(key);
    }
}
