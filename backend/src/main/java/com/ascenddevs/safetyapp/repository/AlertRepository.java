package com.ascenddevs.safetyapp.repository;

import com.ascenddevs.safetyapp.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findByVerificationStatus(Alert.VerificationStatus status);
    List<Alert> findByUserId(UUID userId);
    boolean existsByNonce(String nonce);
}
