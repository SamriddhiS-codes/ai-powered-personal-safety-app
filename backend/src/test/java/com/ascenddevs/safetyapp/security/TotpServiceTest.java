package com.ascenddevs.safetyapp.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {

    // RFC 6238 test secret: ASCII "12345678901234567890", as Base32.
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesRfc6238TestVectors() {
        // The RFC lists 8-digit codes; the last 6 digits are the 6-digit code.
        assertEquals("287082", TotpService.code(RFC_SECRET, 59L / 30));
        assertEquals("081804", TotpService.code(RFC_SECRET, 1111111109L / 30));
        assertEquals("050471", TotpService.code(RFC_SECRET, 1111111111L / 30));
        assertEquals("005924", TotpService.code(RFC_SECRET, 1234567890L / 30));
    }

    @Test
    void base32RoundTrips() {
        byte[] data = "hello totp!".getBytes();
        assertArrayEquals(data, TotpService.base32Decode(TotpService.base32Encode(data)));
    }

    @Test
    void acceptsCurrentCodeAndSmallClockDrift() {
        long now = 1_700_000_000_000L;
        long step = TotpService.stepAt(now);
        assertEquals(step, TotpService.verify(RFC_SECRET, TotpService.code(RFC_SECRET, step), now, -1));
        assertEquals(step - 1, TotpService.verify(RFC_SECRET, TotpService.code(RFC_SECRET, step - 1), now, -1));
        assertEquals(-1, TotpService.verify(RFC_SECRET, TotpService.code(RFC_SECRET, step - 3), now, -1));
    }

    @Test
    void rejectsReuseOfTheSameCode() {
        long now = 1_700_000_000_000L;
        long step = TotpService.stepAt(now);
        String code = TotpService.code(RFC_SECRET, step);
        long first = TotpService.verify(RFC_SECRET, code, now, -1);
        assertEquals(step, first);
        assertEquals(-1, TotpService.verify(RFC_SECRET, code, now, first));
    }

    @Test
    void rejectsMalformedCodes() {
        long now = 1_700_000_000_000L;
        assertEquals(-1, TotpService.verify(RFC_SECRET, "12345", now, -1));
        assertEquals(-1, TotpService.verify(RFC_SECRET, "abcdef", now, -1));
        assertEquals(-1, TotpService.verify(RFC_SECRET, null, now, -1));
    }

    @Test
    void newSecretIsUsable() {
        String secret = TotpService.newSecret();
        assertEquals(32, secret.length());
        assertEquals(6, TotpService.code(secret, 1).length());
    }
}
