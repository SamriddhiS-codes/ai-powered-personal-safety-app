package com.ascenddevs.safetyapp.service;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers, per session, that a check-in is waiting and when it expires.
 * In memory on purpose (no schema change): if the backend restarts mid-countdown the
 * entry is lost, and SessionService then treats the check-in as unanswered and escalates.
 */
@Component
public class PendingCheckinStore {

    public record Pending(double distressConfidence, long deadlineMs) {
    }

    private final ConcurrentHashMap<UUID, Pending> pending = new ConcurrentHashMap<>();

    public void put(UUID sessionId, Pending value) {
        pending.put(sessionId, value);
    }

    public Optional<Pending> peek(UUID sessionId) {
        return Optional.ofNullable(pending.get(sessionId));
    }

    /** Atomically removes and returns the entry, so only one caller can answer a check-in. */
    public Optional<Pending> take(UUID sessionId) {
        return Optional.ofNullable(pending.remove(sessionId));
    }
}
