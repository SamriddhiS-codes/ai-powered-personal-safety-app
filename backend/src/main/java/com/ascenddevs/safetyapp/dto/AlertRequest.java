package com.ascenddevs.safetyapp.dto;

import java.util.UUID;

/** Incoming request to raise a new SOS alert for an active safety session. */
public record AlertRequest(
        UUID sessionId,
        UUID userId,
        Double latitude,
        Double longitude,
        Double distressConfidence,
        Double toneConfidence
) {
}
