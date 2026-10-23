package com.ascenddevs.safetyapp.dto;

import java.util.UUID;

/**
 * Outcome of the "Are you okay?" check-in.
 * outcome is one of DISMISSED_CALM, ESCALATED_NO_RESPONSE, ESCALATED_FRIGHTENED, ESCALATED_UNCLEAR.
 * alertId is set only when an SOS alert was raised.
 */
public record CheckinResponse(
        String outcome,
        boolean escalated,
        String tone,
        Double toneConfidence,
        UUID alertId,
        String sessionStatus
) {
}
