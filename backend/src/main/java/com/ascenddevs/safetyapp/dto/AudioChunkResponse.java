package com.ascenddevs.safetyapp.dto;

/**
 * Result of scoring one ambient audio chunk.
 * When checkinRequired is true the client must record the user's spoken reply and
 * send it to /checkin-response before checkinDeadlineEpochMs.
 */
public record AudioChunkResponse(
        boolean distressDetected,
        double distressConfidence,
        boolean checkinRequired,
        Long checkinDeadlineEpochMs,
        String sessionStatus
) {
}
