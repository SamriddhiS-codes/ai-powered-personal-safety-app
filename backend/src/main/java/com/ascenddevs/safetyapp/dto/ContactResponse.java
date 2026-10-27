package com.ascenddevs.safetyapp.dto;

import com.ascenddevs.safetyapp.entity.TrustedContact;

import java.util.UUID;

public record ContactResponse(UUID id, String name, String phone, String email) {
    public static ContactResponse from(TrustedContact c) {
        return new ContactResponse(c.getId(), c.getName(), c.getPhone(), c.getEmail());
    }
}
