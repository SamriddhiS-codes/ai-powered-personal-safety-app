package com.ascenddevs.safetyapp.service;
 
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.stereotype.Service;

import com.ascenddevs.safetyapp.dto.AlertRequest;
import com.ascenddevs.safetyapp.entity.Alert;
import com.ascenddevs.safetyapp.entity.User;
import com.ascenddevs.safetyapp.repository.AlertRepository;
import com.ascenddevs.safetyapp.repository.UserRepository;
import com.ascenddevs.safetyapp.security.AlertSigningService;
import com.ascenddevs.safetyapp.security.SignedAlert;
import com.fasterxml.jackson.databind.ObjectMapper;
 
@Service
public class AlertService {
 
    private final AlertRepository alertRepository;
    private final UserRepository userRepository;
    private final AlertSigningService signingService;
    private final ObjectMapper objectMapper = new ObjectMapper();
 
    public AlertService(AlertRepository alertRepository, UserRepository userRepository,
                         AlertSigningService signingService) {
        this.alertRepository = alertRepository;
        this.userRepository = userRepository;
        this.signingService = signingService;
    }
 
    /**
     * Raises and signs a new SOS alert, then checks the signature round-trip
     * against the same device secret before persisting.
     *
     * This deliberately uses the side-effect-free signature check and NOT verify():
     * verify() records the nonce, which would make the stored alert look like a
     * replay the first time anyone submits it for real verification.
     */
    public Alert raiseAlert(AlertRequest request) {
        User user = userRepository.findById(request.userId())
                .orElseThrow(() -> new NoSuchElementException("Unknown user: " + request.userId()));
 
        String payload = canonicalPayload(request);
        SignedAlert signed = signingService.sign(user.getDeviceSecret(), payload);
 
        Alert alert = new Alert(
                request.sessionId(), request.userId(),
                request.latitude(), request.longitude(),
                request.distressConfidence(), request.toneConfidence(),
                signed.payload(), signed.nonce(), signed.signature(), signed.timestamp()
        );
 
        boolean roundTripOk = signingService.isSignatureValid(user.getDeviceSecret(), signed);
        alert.setVerificationStatus(roundTripOk
                ? Alert.VerificationStatus.VERIFIED
                : Alert.VerificationStatus.REJECTED_INVALID_SIGNATURE);
 
        return alertRepository.save(alert);
    }
 
    /** Re-verifies a previously stored alert — used by black-box replay/tamper tests. */
    public AlertSigningService.VerificationResult reVerify(Alert alert, String deviceSecret) {
        SignedAlert signedAlert = new SignedAlert(
                alert.getPayload(), alert.getSignedTimestamp(), alert.getNonce(), alert.getSignature());
        return signingService.verify(deviceSecret, signedAlert);
    }
 
    private String canonicalPayload(AlertRequest request) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("sessionId", request.sessionId());
        ordered.put("userId", request.userId());
        ordered.put("latitude", request.latitude());
        ordered.put("longitude", request.longitude());
        ordered.put("distressConfidence", request.distressConfidence());
        ordered.put("toneConfidence", request.toneConfidence());
        try {
            return objectMapper.writeValueAsString(ordered);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize alert payload", e);
        }
    }
 
    private Alert.VerificationStatus mapResult(AlertSigningService.VerificationResult result) {
        return switch (result) {
            case VERIFIED -> Alert.VerificationStatus.VERIFIED;
            case REJECTED_REPLAY -> Alert.VerificationStatus.REJECTED_REPLAY;
            case REJECTED_INVALID_SIGNATURE -> Alert.VerificationStatus.REJECTED_INVALID_SIGNATURE;
            case REJECTED_STALE -> Alert.VerificationStatus.REJECTED_STALE;
        };
    }
}