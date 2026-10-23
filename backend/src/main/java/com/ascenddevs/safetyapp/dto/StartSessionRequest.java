package com.ascenddevs.safetyapp.dto;

import java.util.UUID;

/** Request body for POST /api/sessions/start. */
public record StartSessionRequest(UUID userId) {
}
