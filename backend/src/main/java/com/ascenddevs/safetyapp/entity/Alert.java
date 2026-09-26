package com.ascenddevs.safetyapp.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    private Double latitude;
    private Double longitude;

    @Column(name = "distress_confidence")
    private Double distressConfidence;

    @Column(name = "tone_confidence")
    private Double toneConfidence;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, unique = true)
    private String nonce;

    @Column(nullable = false)
    private String signature;

    @Column(name = "signed_timestamp", nullable = false)
    private long signedTimestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Alert() {
        // JPA
    }

    public Alert(UUID sessionId, UUID userId, Double latitude, Double longitude,
                 Double distressConfidence, Double toneConfidence,
                 String payload, String nonce, String signature, long signedTimestamp) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.distressConfidence = distressConfidence;
        this.toneConfidence = toneConfidence;
        this.payload = payload;
        this.nonce = nonce;
        this.signature = signature;
        this.signedTimestamp = signedTimestamp;
    }

    // --- getters / setters ---

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getUserId() { return userId; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getDistressConfidence() { return distressConfidence; }
    public Double getToneConfidence() { return toneConfidence; }
    public String getPayload() { return payload; }
    public String getNonce() { return nonce; }
    public String getSignature() { return signature; }
    public long getSignedTimestamp() { return signedTimestamp; }
    public VerificationStatus getVerificationStatus() { return verificationStatus; }
    public void setVerificationStatus(VerificationStatus status) { this.verificationStatus = status; }
    public Instant getCreatedAt() { return createdAt; }

    public enum VerificationStatus {
        PENDING, VERIFIED, REJECTED_REPLAY, REJECTED_INVALID_SIGNATURE, REJECTED_STALE
    }
}
