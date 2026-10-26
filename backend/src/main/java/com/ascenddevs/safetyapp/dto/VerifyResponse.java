package com.ascenddevs.safetyapp.dto;

import com.ascenddevs.safetyapp.security.AlertSigningService.VerificationResult;
import com.ascenddevs.safetyapp.security.AlertTamper;

import java.util.UUID;

/** Outcome of re-verifying a stored alert, with a plain-English reason for the dashboard. */
public record VerifyResponse(
        UUID alertId,
        String tamper,
        String result,
        boolean accepted,
        String explanation
) {
    public static VerifyResponse of(UUID alertId, AlertTamper.Mode mode, VerificationResult result) {
        String explanation = switch (result) {
            case VERIFIED -> "Signature, timestamp and nonce are all valid. This alert is authentic.";
            case REJECTED_INVALID_SIGNATURE -> "The signature does not match. The alert was altered, or signed with the wrong key.";
            case REJECTED_REPLAY -> "This nonce was already used, so this is a replay of an alert that was already verified.";
            case REJECTED_STALE -> "The alert's timestamp is outside the 5-minute window, so it cannot be trusted as current.";
        };
        return new VerifyResponse(alertId, mode.name().toLowerCase(), result.name(),
                result == VerificationResult.VERIFIED, explanation);
    }
}
