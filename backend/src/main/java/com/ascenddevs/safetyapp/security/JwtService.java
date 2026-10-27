package com.ascenddevs.safetyapp.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Signed session tokens (JWT, HS256), built by hand on HMAC-SHA256.
 *
 * A token is  base64url(header) . base64url(claims) . base64url(HMAC-SHA256(secret, header.claims)).
 * The server keeps no session list: it only has to recompute the signature to know that the token
 * was issued by it and has not been edited. Tokens carry a "scope" so a short-lived token that is only
 * good for finishing the second login step can never be used as a full session token.
 *
 * Scopes: "access" (full session), "mfa" (password was right, second factor still needed),
 * "setup" (finish enrolling the authenticator app).
 */
public final class JwtService {

    public record Claims(String subject, String scope, long expiresAtSec) {
    }

    private static final String HEADER = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
    private static final Pattern SUB = Pattern.compile("\"sub\":\"([^\"]+)\"");
    private static final Pattern SCOPE = Pattern.compile("\"scope\":\"([a-z]+)\"");
    private static final Pattern EXP = Pattern.compile("\"exp\":(\\d+)");

    private final byte[] key;
    private final LongSupplier clockMs;

    public JwtService(String secret, LongSupplier clockMs) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("The JWT secret must be at least 32 characters long.");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.clockMs = clockMs;
    }

    public String issue(String subject, String scope, long ttlSeconds) {
        long iat = clockMs.getAsLong() / 1000;
        String claims = "{\"sub\":\"" + subject + "\",\"scope\":\"" + scope + "\",\"iat\":" + iat
                + ",\"exp\":" + (iat + ttlSeconds) + "}";
        String signingInput = HEADER + "." + b64(claims.getBytes(StandardCharsets.UTF_8));
        return signingInput + "." + b64(hmac(signingInput));
    }

    /** Returns the claims if the token is genuine and not expired, otherwise null. */
    public Claims verify(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !HEADER.equals(parts[0])) {
            return null; // wrong algorithm, e.g. "alg":"none"
        }
        byte[] expected = hmac(parts[0] + "." + parts[1]);
        byte[] given;
        try {
            given = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!MessageDigest.isEqual(expected, given)) {
            return null; // edited token or wrong key
        }
        String json;
        try {
            json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Matcher sub = SUB.matcher(json), scope = SCOPE.matcher(json), exp = EXP.matcher(json);
        if (!sub.find() || !scope.find() || !exp.find()) return null;
        long expSec = Long.parseLong(exp.group(1));
        if (clockMs.getAsLong() / 1000 >= expSec) return null; // expired
        return new Claims(sub.group(1), scope.group(1), expSec);
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
