package com.ascenddevs.safetyapp.dto;

/** Request body for POST /api/sessions/{id}/ping. Boxed so a missing field is detectable. */
public record PingRequest(Double latitude, Double longitude) {
}
