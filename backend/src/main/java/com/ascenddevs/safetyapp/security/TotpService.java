package com.ascenddevs.safetyapp.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Time-based one-time passwords (TOTP, RFC 6238), built by hand on HMAC-SHA1 (RFC 4226).
 * This is the second login factor: the 6-digit code that an authenticator app shows.
 *
 * How it works: the server and the phone share a secret. Both compute
 *   code = truncate(HMAC-SHA1(secret, floor(unixTime / 30)))  (6 digits)
 * so the code changes every 30 seconds and the secret itself is never sent at login.
 * A code is accepted only once: the time step that was used is remembered, so a code
 * that was shoulder-surfed or intercepted cannot be replayed.
 */
public final class TotpService {

    public static final int STEP_SECONDS = 30;
    public static final int DIGITS = 6;
    /** Accept the previous and next 30-second step too, to tolerate small clock drift. */
    public static final int DRIFT_STEPS = 1;

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpService() {
    }

    /** A new random 160-bit shared secret, as the Base32 text authenticator apps expect. */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** otpauth:// link that authenticator apps understand (also usable as a QR code). */
    public static String otpauthUri(String issuer, String account, String secret) {
        return "otpauth://totp/" + enc(issuer) + ":" + enc(account)
                + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    public static long stepAt(long epochMs) {
        return Math.floorDiv(epochMs, STEP_SECONDS * 1000L);
    }

    /** The code for one time step. */
    public static String code(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(base32Secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = h[h.length - 1] & 0x0f; // dynamic truncation
            int binary = ((h[offset] & 0x7f) << 24) | ((h[offset + 1] & 0xff) << 16)
                    | ((h[offset + 2] & 0xff) << 8) | (h[offset + 3] & 0xff);
            int mod = 1;
            for (int i = 0; i < DIGITS; i++) mod *= 10;
            return String.format("%0" + DIGITS + "d", binary % mod);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 is not available", e);
        }
    }

    /**
     * Checks a submitted code.
     *
     * @param lastUsedStep the step of the last accepted code (use -1 if none), so a code cannot be reused
     * @return the matching time step, or -1 if the code is wrong or was already used
     */
    public static long verify(String base32Secret, String submitted, long nowMs, long lastUsedStep) {
        if (submitted == null || !submitted.matches("\\d{" + DIGITS + "}")) {
            return -1;
        }
        long now = stepAt(nowMs);
        long matched = -1;
        for (long s = now - DRIFT_STEPS; s <= now + DRIFT_STEPS; s++) {
            boolean same = MessageDigest.isEqual(
                    code(base32Secret, s).getBytes(StandardCharsets.UTF_8),
                    submitted.getBytes(StandardCharsets.UTF_8)); // constant-time compare
            if (same && s > lastUsedStep) {
                matched = s;
            }
        }
        return matched;
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] base32Decode(String text) {
        String s = text.replace(" ", "").replace("=", "").toUpperCase();
        byte[] out = new byte[s.length() * 5 / 8];
        int buffer = 0, bits = 0, idx = 0;
        for (char c : s.toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Not a Base32 character: " + c);
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out[idx++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
