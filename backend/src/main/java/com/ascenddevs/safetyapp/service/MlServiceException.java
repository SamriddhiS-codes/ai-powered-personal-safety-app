package com.ascenddevs.safetyapp.service;

/** The ML service could not be reached or returned something unusable. */
public class MlServiceException extends RuntimeException {
    public MlServiceException(String message) {
        super(message);
    }

    public MlServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
