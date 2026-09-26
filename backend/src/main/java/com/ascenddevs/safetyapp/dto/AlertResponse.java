package com.ascenddevs.safetyapp.dto;

import com.ascenddevs.safetyapp.entity.Alert;

import java.util.UUID;

public record AlertResponse(
        UUID id,
        Double latitude,
        Double longitude,
        Double distressConfidence,
        Double toneConfidence,
        String verificationStatus,
        long signedTimestamp
) {
    public static AlertResponse from(Alert alert) {
        return new AlertResponse(
                alert.getId(),
                alert.getLatitude(),
                alert.getLongitude(),
                alert.getDistressConfidence(),
                alert.getToneConfidence(),
                alert.getVerificationStatus().name(),
                alert.getSignedTimestamp()
        );
    }
}
