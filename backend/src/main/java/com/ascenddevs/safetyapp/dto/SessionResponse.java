package com.ascenddevs.safetyapp.dto;

import com.ascenddevs.safetyapp.entity.LocationPing;
import com.ascenddevs.safetyapp.entity.SafetySession;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID sessionId,
        UUID userId,
        String status,
        Instant startedAt,
        Instant endedAt,
        Double latitude,
        Double longitude,
        Instant lastLocationAt
) {
    public static SessionResponse from(SafetySession session, LocationPing lastPing) {
        return new SessionResponse(
                session.getId(),
                session.getUserId(),
                session.getStatus().name(),
                session.getStartedAt(),
                session.getEndedAt(),
                lastPing == null ? null : lastPing.getLatitude(),
                lastPing == null ? null : lastPing.getLongitude(),
                lastPing == null ? null : lastPing.getRecordedAt()
        );
    }
}
