package com.ascenddevs.safetyapp.security;

/**
 * Test/demo hook: simulates an attacker modifying a signed alert in transit, so the
 * verification logic can be shown rejecting it. Pure functions, no state.
 */
public final class AlertTamper {

    private static final long ONE_MINUTE_MS = 60_000L;
    private static final long TEN_MINUTES_MS = 10 * 60_000L;

    private AlertTamper() {
    }

    public enum Mode {
        NONE,       // submit the alert exactly as stored
        PAYLOAD,    // change the location inside the signed payload
        SIGNATURE,  // corrupt the signature
        TIMESTAMP,  // shift the timestamp by one minute (still fresh, but no longer what was signed)
        STALE;      // pretend the alert is ten minutes old

        public static Mode parse(String value) {
            if (value == null || value.isBlank()) {
                return NONE;
            }
            try {
                return valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("tamper must be one of: none, payload, signature, timestamp, stale");
            }
        }
    }

    public static SignedAlert apply(SignedAlert alert, Mode mode) {
        return switch (mode) {
            case NONE -> alert;
            case PAYLOAD -> new SignedAlert(alterPayload(alert.payload()), alert.timestamp(), alert.nonce(), alert.signature());
            case SIGNATURE -> new SignedAlert(alert.payload(), alert.timestamp(), alert.nonce(), alterSignature(alert.signature()));
            case TIMESTAMP -> new SignedAlert(alert.payload(), alert.timestamp() + ONE_MINUTE_MS, alert.nonce(), alert.signature());
            case STALE -> new SignedAlert(alert.payload(), alert.timestamp() - TEN_MINUTES_MS, alert.nonce(), alert.signature());
        };
    }

    private static String alterPayload(String payload) {
        String changed = payload.replaceFirst("\"latitude\":[^,}]+", "\"latitude\":0.0");
        return changed.equals(payload) ? payload + " " : changed;
    }

    private static String alterSignature(String signature) {
        if (signature == null || signature.isEmpty()) {
            return "x";
        }
        char last = signature.charAt(signature.length() - 1);
        char replacement = (last == 'A') ? 'B' : 'A';
        return signature.substring(0, signature.length() - 1) + replacement;
    }
}
