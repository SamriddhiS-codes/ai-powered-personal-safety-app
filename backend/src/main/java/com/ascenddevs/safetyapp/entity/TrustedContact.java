package com.ascenddevs.safetyapp.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A person the user trusts to be told when an SOS is raised (table: trusted_contacts). */
@Entity
@Table(name = "trusted_contacts")
public class TrustedContact {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;

    private String phone;

    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TrustedContact() {
        // JPA
    }

    public TrustedContact(UUID userId, String name, String phone, String email) {
        this.userId = userId;
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public Instant getCreatedAt() { return createdAt; }
}
