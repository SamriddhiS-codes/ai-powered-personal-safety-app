package com.ascenddevs.safetyapp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthValidatorTest {

    @Test
    void acceptsGoodSignUp() {
        assertNull(AuthValidator.validateSignUp("Asha", "asha@example.com", "goodpass123"));
    }

    @Test
    void rejectsBadDetails() {
        assertNotNull(AuthValidator.validateSignUp("", "asha@example.com", "goodpass123"));
        assertNotNull(AuthValidator.validateSignUp("Asha", "not-an-email", "goodpass123"));
        assertNotNull(AuthValidator.validateSignUp("Asha", "asha@example.com", "short1"));
        assertNotNull(AuthValidator.validateSignUp("Asha", "asha@example.com", "onlyletters"));
        assertNotNull(AuthValidator.validateSignUp("Asha", "asha@example.com", "12345678"));
    }

    @Test
    void normalizesEmail() {
        assertEquals("asha@example.com", AuthValidator.normalizeEmail("  Asha@Example.COM "));
    }
}
