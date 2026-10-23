package com.ascenddevs.safetyapp.service;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Thin boundary to the Python ML service. An interface so the rest of the backend never depends on HTTP. */
public interface MlClient {

    DistressResult predictDistress(byte[] wavAudio);

    ToneResult predictTone(byte[] wavAudio);

    record DistressResult(@JsonProperty("is_distress") boolean isDistress, double confidence) {
    }

    record ToneResult(String tone, double confidence) {
    }
}
