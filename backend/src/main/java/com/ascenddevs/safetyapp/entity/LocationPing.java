package com.ascenddevs.safetyapp.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** One GPS point pushed by the client while a Safety Mode session is active. */
@Entity
@Table(name = "location_pings")
public class LocationPing {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt = Instant.now();

    protected LocationPing() {
        // JPA
    }

    public LocationPing(UUID sessionId, double latitude, double longitude) {
        this.sessionId = sessionId;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public Instant getRecordedAt() { return recordedAt; }
}
