package com.ascenddevs.safetyapp.security;
 
import org.springframework.stereotype.Service;
 
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
 
/**
 * Core security algorithm for the project (goes beyond login/password auth).
 *
 * Every SOS alert is signed with HMAC-SHA256 using a secret unique to the
 * user's registered device. Verification checks three independent things:
 *
 *   1. Timestamp freshness  — is this alert recent, not a stale/future-dated forgery?
 *   2. Signature validity   — was this payload produced by someone holding the device secret?
 *   3. Nonce uniqueness     — has this exact alert been submitted before? (replay protection)
 *
 * All three must pass for an alert to be marked VERIFIED. The checks run in that
 * order on purpose: the nonce is only recorded AFTER the signature is proven valid,
 * so a forger who guesses or copies a nonce cannot "burn" it and block the real alert.
 * This is intentionally
 * a small, fully hand-written, fully explainable mechanism — every line here is
 * something the team can walk through and defend, rather than an opaque library call.
 */
@Service
public class AlertSigningService {
 
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TIMESTAMP_TOLERANCE_MS = TimeUnit.MINUTES.toMillis(5);
 
    // In-memory nonce store keyed by nonce -> timestamp it was first seen.
    // For production this would move to Redis so it survives restarts and
    // works across multiple backend instances; kept in-memory here so the
    // logic is trivially unit-testable without external infrastructure.
    private final Map<String, Long> seenNonces = new ConcurrentHashMap<>();
 
    private final SecureRandom secureRandom = new SecureRandom();
    private final LongSupplier clock;
 
    public AlertSigningService() {
        this(System::currentTimeMillis);
    }
 
    /** Package-private so tests can control time (stale alerts, nonce expiry). */
    AlertSigningService(LongSupplier clock) {
        this.clock = clock;
    }
 
    /** Signs a payload with the given device secret, producing a ready-to-send SignedAlert. */
    public SignedAlert sign(String deviceSecret, String payload) {
        long timestamp = clock.getAsLong();
        String nonce = generateNonce();
        String signature = computeHmac(deviceSecret, canonicalString(payload, timestamp, nonce));
        return new SignedAlert(payload, timestamp, nonce, signature);
    }
 
    /**
     * Checks ONLY that the signature matches the payload, timestamp and nonce.
     * Has no side effects: it does not check freshness and does not record the nonce.
     */
    public boolean isSignatureValid(String deviceSecret, SignedAlert alert) {
        if (alert.signature() == null || alert.nonce() == null || alert.payload() == null) {
            return false;
        }
        String expectedSignature = computeHmac(
                deviceSecret, canonicalString(alert.payload(), alert.timestamp(), alert.nonce()));
        return constantTimeEquals(expectedSignature, alert.signature());
    }
 
    /**
     * Verifies a submitted alert. Returns a {@link VerificationResult} describing
     * exactly which check failed, so the audit trail / responder dashboard can
     * show *why* an alert was rejected, not just that it was.
     */
    public VerificationResult verify(String deviceSecret, SignedAlert alert) {
        long now = clock.getAsLong();
        evictExpiredNonces(now);
 
        // 1. Freshness
        if (Math.abs(now - alert.timestamp()) > TIMESTAMP_TOLERANCE_MS) {
            return VerificationResult.REJECTED_STALE;
        }
 
        // 2. Signature - before the nonce is touched, so forged alerts cannot burn real nonces.
        if (!isSignatureValid(deviceSecret, alert)) {
            return VerificationResult.REJECTED_INVALID_SIGNATURE;
        }
 
        // 3. Nonce uniqueness. putIfAbsent is atomic: if two requests race on the
        //    same nonce, only one wins.
        Long firstSeen = seenNonces.putIfAbsent(alert.nonce(), alert.timestamp());
        if (firstSeen != null) {
            return VerificationResult.REJECTED_REPLAY;
        }
 
        return VerificationResult.VERIFIED;
    }
 
    /**
     * Drops nonces whose alert timestamp has fallen outside the tolerance window.
     * Safe because such an alert would be rejected as STALE anyway, so remembering
     * its nonce adds nothing - and this keeps the map from growing forever.
     */
    private void evictExpiredNonces(long now) {
        seenNonces.values().removeIf(ts -> Math.abs(now - ts) > TIMESTAMP_TOLERANCE_MS);
    }
 
    /** For tests: how many nonces are currently remembered. */
    int trackedNonceCount() {
        return seenNonces.size();
    }
 
    private String canonicalString(String payload, long timestamp, String nonce) {
        return payload + "|" + timestamp + "|" + nonce;
    }
 
    private String computeHmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC signature", e);
        }
    }
 
    private String generateNonce() {
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
 
    /** Constant-time comparison so signature checking doesn't leak timing information. */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
 
    public enum VerificationResult {
        VERIFIED,
        REJECTED_STALE,
        REJECTED_REPLAY,
        REJECTED_INVALID_SIGNATURE
    }
}