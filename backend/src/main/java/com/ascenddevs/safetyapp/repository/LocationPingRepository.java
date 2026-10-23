package com.ascenddevs.safetyapp.repository;

import com.ascenddevs.safetyapp.entity.LocationPing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LocationPingRepository extends JpaRepository<LocationPing, UUID> {
    Optional<LocationPing> findFirstBySessionIdOrderByRecordedAtDesc(UUID sessionId);
}
