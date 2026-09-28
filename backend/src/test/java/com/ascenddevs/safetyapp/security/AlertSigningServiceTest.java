package com.ascenddevs.safetyapp.security;
 
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
 
/**
 * White-box tests: exercise every branch of AlertSigningService directly,
 * with full knowledge of its internals (nonce store, timestamp tolerance).
 */
class AlertSigningServiceTest {
 
    private static final String SECRET = "device-secret-123";
 
    @Test
    void validAlert_isVerified() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert alert = service.sign(SECRET, "{\"lat\":22.57,\"lng\":88.36}");
 
        AlertSigningService.VerificationResult result = service.verify(SECRET, alert);
 
        assertEquals(AlertSigningService.VerificationResult.VERIFIED, result);
    }
 
    @Test
    void replayedAlert_isRejected() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert alert = service.sign(SECRET, "{\"lat\":22.57,\"lng\":88.36}");
 
        AlertSigningService.VerificationResult first = service.verify(SECRET, alert);
        AlertSigningService.VerificationResult replay = service.verify(SECRET, alert);
 
        assertEquals(AlertSigningService.VerificationResult.VERIFIED, first);
        assertEquals(AlertSigningService.VerificationResult.REJECTED_REPLAY, replay);
    }
 
    @Test
    void tamperedPayload_isRejected() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert alert = service.sign(SECRET, "{\"lat\":22.57,\"lng\":88.36}");
 
        // Attacker changes the location after signing, keeping the old signature.
        SignedAlert tampered = new SignedAlert("{\"lat\":0.0,\"lng\":0.0}",
                alert.timestamp(), alert.nonce(), alert.signature());
 
        assertEquals(AlertSigningService.VerificationResult.REJECTED_INVALID_SIGNATURE,
                service.verify(SECRET, tampered));
    }
 
    @Test
    void wrongDeviceSecret_isRejected() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert alert = service.sign(SECRET, "{\"lat\":22.57,\"lng\":88.36}");
 
        assertEquals(AlertSigningService.VerificationResult.REJECTED_INVALID_SIGNATURE,
                service.verify("wrong-secret", alert));
    }
 
    @Test
    void staleTimestamp_isRejected() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert freshlySigned = service.sign(SECRET, "{\"lat\":22.57,\"lng\":88.36}");
 
        // Simulate an alert timestamped 10 minutes in the past (beyond the 5-minute tolerance).
        long staleTimestamp = System.currentTimeMillis() - java.util.concurrent.TimeUnit.MINUTES.toMillis(10);
        SignedAlert stale = new SignedAlert(freshlySigned.payload(), staleTimestamp,
                freshlySigned.nonce(), freshlySigned.signature());
 
        assertEquals(AlertSigningService.VerificationResult.REJECTED_STALE,
                service.verify(SECRET, stale));
    }
 
    @Test
    void forgedAlert_doesNotBurnTheRealNonce() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert real = service.sign(SECRET, "{\"lat\":22.57,\"lng\":88.36}");
 
        // Attacker copies the nonce and timestamp but cannot produce a valid signature.
        SignedAlert forged = new SignedAlert("{\"lat\":0.0,\"lng\":0.0}",
                real.timestamp(), real.nonce(), "not-a-real-signature");
 
        assertEquals(AlertSigningService.VerificationResult.REJECTED_INVALID_SIGNATURE,
                service.verify(SECRET, forged));
        // The genuine alert must still go through.
        assertEquals(AlertSigningService.VerificationResult.VERIFIED,
                service.verify(SECRET, real));
    }
 
    @Test
    void isSignatureValid_hasNoSideEffects() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert alert = service.sign(SECRET, "{\"lat\":22.57,\"lng\":88.36}");
 
        assertTrue(service.isSignatureValid(SECRET, alert));
        assertTrue(service.isSignatureValid(SECRET, alert));
        assertEquals(0, service.trackedNonceCount());
        assertEquals(AlertSigningService.VerificationResult.VERIFIED,
                service.verify(SECRET, alert));
    }
 
    @Test
    void nullSignature_isRejectedWithoutThrowing() {
        AlertSigningService service = new AlertSigningService();
        SignedAlert real = service.sign(SECRET, "{\"lat\":22.57,\"lng\":88.36}");
        SignedAlert broken = new SignedAlert(real.payload(), real.timestamp(), real.nonce(), null);
 
        assertEquals(AlertSigningService.VerificationResult.REJECTED_INVALID_SIGNATURE,
                service.verify(SECRET, broken));
    }
 
    @Test
    void expiredNonces_areEvicted_andOldReplaysStayRejected() {
        long[] now = {1_000_000_000_000L};
        AlertSigningService service = new AlertSigningService(() -> now[0]);
        SignedAlert alert = service.sign(SECRET, "{\"lat\":22.57,\"lng\":88.36}");
 
        assertEquals(AlertSigningService.VerificationResult.VERIFIED, service.verify(SECRET, alert));
        assertEquals(1, service.trackedNonceCount());
 
        // Six minutes later the nonce is outside the 5-minute window...
        now[0] += java.util.concurrent.TimeUnit.MINUTES.toMillis(6);
 
        // ...replaying the old alert is still rejected (as stale), and the nonce is forgotten.
        assertEquals(AlertSigningService.VerificationResult.REJECTED_STALE, service.verify(SECRET, alert));
        assertEquals(0, service.trackedNonceCount());
    }
}