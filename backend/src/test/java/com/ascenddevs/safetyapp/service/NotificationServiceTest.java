package com.ascenddevs.safetyapp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NotificationServiceTest {

    @Test
    void masksEmailAddresses() {
        assertEquals("a***@gmail.com", NotificationService.mask("asha@gmail.com"));
        assertEquals("x@y.com", NotificationService.mask("x@y.com"));
    }
}
