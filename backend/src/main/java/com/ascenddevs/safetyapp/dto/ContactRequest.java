package com.ascenddevs.safetyapp.dto;

import java.util.UUID;

/** Request to add a trusted contact. At least one of phone or email is required. */
public record ContactRequest(UUID userId, String name, String phone, String email) {
}
