package com.ascenddevs.safetyapp.repository;

import com.ascenddevs.safetyapp.entity.TrustedContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrustedContactRepository extends JpaRepository<TrustedContact, UUID> {
    List<TrustedContact> findByUserIdOrderByCreatedAtAsc(UUID userId);
    long countByUserId(UUID userId);
}
