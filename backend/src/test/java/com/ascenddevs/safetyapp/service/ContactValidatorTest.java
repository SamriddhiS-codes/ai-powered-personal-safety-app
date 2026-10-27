package com.ascenddevs.safetyapp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContactValidatorTest {

    @Test
    void acceptsPhoneOnlyAndEmailOnly() {
        assertNull(ContactValidator.validate("Asha", "+91 98765-43210", null));
        assertNull(ContactValidator.validate("Asha", "", "asha@example.com"));
    }

    @Test
    void rejectsMissingNameOrNoWayToReach() {
        assertNotNull(ContactValidator.validate(" ", "9876543210", null));
        assertNotNull(ContactValidator.validate("Asha", null, null));
        assertNotNull(ContactValidator.validate("Asha", " ", " "));
    }

    @Test
    void rejectsBadPhoneAndBadEmail() {
        assertNotNull(ContactValidator.validate("Asha", "12ab34", null));
        assertNotNull(ContactValidator.validate("Asha", "123", null));
        assertNotNull(ContactValidator.validate("Asha", null, "not-an-email"));
    }

    @Test
    void cleansPhoneFormatting() {
        assertEquals("+919876543210", ContactValidator.cleanPhone("+91 98765-43210"));
        assertEquals("9876543210", ContactValidator.cleanPhone("(98765) 43210"));
    }
}
