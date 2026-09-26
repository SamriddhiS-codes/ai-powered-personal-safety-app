package com.ascenddevs.safetyapp.security;

/**
 * A tamper-evident alert: the payload plus everything needed to verify it was
 * produced by the holder of the device secret, exactly once, within a recent
 * time window.
 */
public record SignedAlert(String payload, long timestamp, String nonce, String signature) {
}
