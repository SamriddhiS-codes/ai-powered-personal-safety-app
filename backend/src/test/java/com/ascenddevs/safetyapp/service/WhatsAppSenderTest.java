package com.ascenddevs.safetyapp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WhatsAppSenderTest {

    @Test
    void tenDigitNumberIsTreatedAsIndian() {
        assertEquals("+919876543210", WhatsAppSender.toE164("98765 43210"));
    }

    @Test
    void numberWithPlusKeepsItsCountryCode() {
        assertEquals("+14155238886", WhatsAppSender.toE164("+1 415-523-8886"));
    }

    @Test
    void countryCodeWithoutPlusGetsOne() {
        assertEquals("+919876543210", WhatsAppSender.toE164("919876543210"));
    }

    @Test
    void unusableNumbersAreRejected() {
        assertNull(WhatsAppSender.toE164("abc"));
        assertNull(WhatsAppSender.toE164("12"));
        assertNull(WhatsAppSender.toE164(null));
    }

    @Test
    void maskedPhoneHidesTheMiddleDigits() {
        assertEquals("+91*****10", NotificationService.maskPhone("+919876543210"));
    }
}
