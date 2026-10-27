package com.ascenddevs.safetyapp.security;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET = "a-test-secret-that-is-at-least-32-chars!";
    private final AtomicLong now = new AtomicLong(1_700_000_000_000L);
    private final JwtService jwt = new JwtService(SECRET, now::get);

    @Test
    void roundTrip() {
        String token = jwt.issue("user-1", "access", 3600);
        JwtService.Claims c = jwt.verify(token);
        assertNotNull(c);
        assertEquals("user-1", c.subject());
        assertEquals("access", c.scope());
    }

    @Test
    void rejectsExpiredToken() {
        String token = jwt.issue("user-1", "mfa", 300);
        now.addAndGet(301_000);
        assertNull(jwt.verify(token));
    }

    @Test
    void rejectsEditedClaims() {
        String token = jwt.issue("user-1", "mfa", 300);
        String[] p = token.split("\\.");
        String evil = new String(Base64.getUrlDecoder().decode(p[1])).replace("\"mfa\"", "\"access\"");
        String forged = p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(evil.getBytes()) + "." + p[2];
        assertNull(jwt.verify(forged));
    }

    @Test
    void rejectsWrongKey() {
        JwtService other = new JwtService("another-secret-that-is-also-32-chars!!", now::get);
        assertNull(jwt.verify(other.issue("user-1", "access", 3600)));
    }

    @Test
    void rejectsAlgNoneAndGarbage() {
        String none = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes());
        String claims = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"user-1\",\"scope\":\"access\",\"iat\":1,\"exp\":9999999999}".getBytes());
        assertNull(jwt.verify(none + "." + claims + "."));
        assertNull(jwt.verify("not-a-token"));
        assertNull(jwt.verify(null));
    }

    @Test
    void refusesShortSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("short", now::get));
    }
}
