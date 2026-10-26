package com.ascenddevs.safetyapp.security;

import com.ascenddevs.safetyapp.security.AlertSigningService.VerificationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Black-box style checks of the replay/tamper demo: each attack must be rejected for the right reason. */
class AlertTamperTest {

    private static final String SECRET = "device-secret-123";
    private static final String PAYLOAD = "{\"latitude\":22.57,\"longitude\":88.36}";

    private SignedAlert signed(AlertSigningService service) {
        return service.sign(SECRET, PAYLOAD);
    }

    @Test
    void untouchedAlert_isVerifiedOnce_thenReportedAsReplay() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert alert = signed(service);

        assertEquals(VerificationResult.VERIFIED, service.verify(SECRET, AlertTamper.apply(alert, AlertTamper.Mode.NONE)));
        assertEquals(VerificationResult.REJECTED_REPLAY, service.verify(SECRET, AlertTamper.apply(alert, AlertTamper.Mode.NONE)));
    }

    @Test
    void changedLocation_isRejected_andDoesNotBurnTheRealNonce() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert alert = signed(service);
        SignedAlert forged = AlertTamper.apply(alert, AlertTamper.Mode.PAYLOAD);

        assertNotEquals(alert.payload(), forged.payload());
        assertTrue(forged.payload().contains("\"latitude\":0.0"));
        assertEquals(VerificationResult.REJECTED_INVALID_SIGNATURE, service.verify(SECRET, forged));
        assertEquals(VerificationResult.VERIFIED, service.verify(SECRET, alert));
    }

    @Test
    void payloadWithoutLatitude_isStillChanged() {
        SignedAlert alert = new SignedAlert("{\"note\":\"x\"}", 1L, "n", "sig");
        assertNotEquals(alert.payload(), AlertTamper.apply(alert, AlertTamper.Mode.PAYLOAD).payload());
    }

    @Test
    void corruptedSignature_isRejected() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert alert = signed(service);
        SignedAlert forged = AlertTamper.apply(alert, AlertTamper.Mode.SIGNATURE);

        assertNotEquals(alert.signature(), forged.signature());
        assertEquals(VerificationResult.REJECTED_INVALID_SIGNATURE, service.verify(SECRET, forged));
    }

    @Test
    void shiftedTimestamp_failsTheSignatureCheck() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert forged = AlertTamper.apply(signed(service), AlertTamper.Mode.TIMESTAMP);
        assertEquals(VerificationResult.REJECTED_INVALID_SIGNATURE, service.verify(SECRET, forged));
    }

    @Test
    void oldAlert_isRejectedAsStale() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert forged = AlertTamper.apply(signed(service), AlertTamper.Mode.STALE);
        assertEquals(VerificationResult.REJECTED_STALE, service.verify(SECRET, forged));
    }

    @Test
    void parse_acceptsKnownModes_andRejectsUnknown() {
        assertEquals(AlertTamper.Mode.NONE, AlertTamper.Mode.parse(null));
        assertEquals(AlertTamper.Mode.NONE, AlertTamper.Mode.parse(" "));
        assertEquals(AlertTamper.Mode.PAYLOAD, AlertTamper.Mode.parse("payload"));
        assertEquals(AlertTamper.Mode.STALE, AlertTamper.Mode.parse("Stale"));
        assertThrows(IllegalArgumentException.class, () -> AlertTamper.Mode.parse("bogus"));
    }
}
