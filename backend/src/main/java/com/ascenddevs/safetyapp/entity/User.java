package com.ascenddevs.safetyapp.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // Per-device HMAC key used by AlertSigningService. Issued at registration,
    // never transmitted after that point except to the device itself.
    @Column(name = "device_secret", nullable = false)
    private String deviceSecret;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected User() {
        // JPA
    }

    public User(String name, String email, String passwordHash, String deviceSecret) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.deviceSecret = deviceSecret;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getDeviceSecret() { return deviceSecret; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Instant getCreatedAt() { return createdAt; }

    public enum Role {
        USER, RESPONDER, ANALYST
    }
}
