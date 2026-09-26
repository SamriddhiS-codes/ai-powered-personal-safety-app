package com.ascenddevs.safetyapp.repository;

import com.ascenddevs.safetyapp.entity.SafetySession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SafetySessionRepository extends JpaRepository<SafetySession, UUID> {
}
