package com.ascenddevs.safetyapp.repository;

import com.ascenddevs.safetyapp.entity.LocationPing;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocationPingRepository extends JpaRepository<LocationPing, UUID> {
    Optional<LocationPing> findFirstBySessionIdOrderByRecordedAtDesc(UUID sessionId);

    /** Newest pings across all of one user's sessions (used when the current session has none yet). */
    @Query("select p from LocationPing p where p.sessionId in "
            + "(select s.id from SafetySession s where s.userId = :userId) order by p.recordedAt desc")
    List<LocationPing> findRecentForUser(@Param("userId") UUID userId, Pageable pageable);
}
