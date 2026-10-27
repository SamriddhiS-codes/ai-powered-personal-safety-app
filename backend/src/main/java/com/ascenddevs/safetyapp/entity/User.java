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

    // Second login factor (TOTP). The secret is shared with the user's authenticator app.
    @Column(name = "totp_secret")
    private String totpSecret;

    @Column(name = "totp_enabled", nullable = false)
    private boolean totpEnabled = false;

    // Time step of the last accepted TOTP code, so a code can never be used twice.
    @Column(name = "last_totp_step", nullable = false)
    private long lastTotpStep = -1;

    // Brute-force protection: wrong passwords or codes are counted, and too many lock the account for a while.
    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

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
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public boolean isTotpEnabled() { return totpEnabled; }
    public void setTotpEnabled(boolean totpEnabled) { this.totpEnabled = totpEnabled; }
    public long getLastTotpStep() { return lastTotpStep; }
    public void setLastTotpStep(long lastTotpStep) { this.lastTotpStep = lastTotpStep; }
    public int getFailedAttempts() { return failedAttempts; }
    public void setFailedAttempts(int failedAttempts) { this.failedAttempts = failedAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }

    public enum Role {
        USER, RESPONDER, ANALYST
    }
}
