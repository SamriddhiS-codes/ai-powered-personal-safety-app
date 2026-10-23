package com.ascenddevs.safetyapp.service;

import com.ascenddevs.safetyapp.dto.AlertRequest;
import com.ascenddevs.safetyapp.dto.AudioChunkResponse;
import com.ascenddevs.safetyapp.dto.CheckinResponse;
import com.ascenddevs.safetyapp.entity.Alert;
import com.ascenddevs.safetyapp.entity.LocationPing;
import com.ascenddevs.safetyapp.entity.SafetySession;
import com.ascenddevs.safetyapp.repository.LocationPingRepository;
import com.ascenddevs.safetyapp.repository.SafetySessionRepository;
import com.ascenddevs.safetyapp.repository.UserRepository;
import com.ascenddevs.safetyapp.security.AdaptiveRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates one Safety Mode session:
 *
 *   ACTIVE --(audio chunk flagged as distress)--> CHECKIN_PENDING
 *   CHECKIN_PENDING --(calm reply in time)-------> ACTIVE
 *   CHECKIN_PENDING --(frightened / no reply)----> ESCALATED  (signed SOS alert raised)
 *   any open state --(user ends session)---------> ENDED
 *
 * The decision itself lives in {@link CheckinPolicy}; this class only does the wiring.
 */
@Service
public class SessionService {

    private final SafetySessionRepository sessions;
    private final LocationPingRepository pings;
    private final UserRepository users;
    private final MlClient ml;
    private final AlertService alertService;
    private final PendingCheckinStore pendingStore;
    private final AdaptiveRateLimiter rateLimiter;
    private final int rateLimitBase;
    private final long rateLimitWindowMs;
    private final long countdownMs;
    private final long graceMs;

    // One lock object per session so a double-tapped check-in cannot be processed twice.
    private final ConcurrentHashMap<UUID, Object> checkinLocks = new ConcurrentHashMap<>();

    public SessionService(SafetySessionRepository sessions,
                          LocationPingRepository pings,
                          UserRepository users,
                          MlClient ml,
                          AlertService alertService,
                          PendingCheckinStore pendingStore,
                          AdaptiveRateLimiter rateLimiter,
                          @Value("${app.security.rate-limit.base-limit-per-window:20}") int rateLimitBase,
                          @Value("${app.security.rate-limit.window-ms:60000}") long rateLimitWindowMs,
                          @Value("${app.checkin.countdown-seconds:30}") long countdownSeconds,
                          @Value("${app.checkin.grace-seconds:3}") long graceSeconds) {
        this.sessions = sessions;
        this.pings = pings;
        this.users = users;
        this.ml = ml;
        this.alertService = alertService;
        this.pendingStore = pendingStore;
        this.rateLimiter = rateLimiter;
        this.rateLimitBase = rateLimitBase;
        this.rateLimitWindowMs = rateLimitWindowMs;
        this.countdownMs = countdownSeconds * 1000;
        this.graceMs = graceSeconds * 1000;
    }

    // ------------------------------------------------------------------ lifecycle

    public SafetySession startSession(UUID userId) {
        if (userId == null || !users.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown user");
        }
        return sessions.save(new SafetySession(userId));
    }

    public SafetySession endSession(UUID sessionId) {
        SafetySession session = requireSession(sessionId);
        if (session.getStatus() != SafetySession.Status.ENDED) {
            session.setStatus(SafetySession.Status.ENDED);
            session.setEndedAt(Instant.now());
            session = sessions.save(session);
        }
        pendingStore.take(sessionId);
        checkinLocks.remove(sessionId);
        return session;
    }

    public SafetySession getSession(UUID sessionId) {
        return requireSession(sessionId);
    }

    public Optional<LocationPing> lastLocation(UUID sessionId) {
        return pings.findFirstBySessionIdOrderByRecordedAtDesc(sessionId);
    }

    // ------------------------------------------------------------------ location

    public void recordPing(UUID sessionId, Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "latitude must be in [-90, 90] and longitude in [-180, 180]");
        }
        requireOpenSession(sessionId);
        pings.save(new LocationPing(sessionId, latitude, longitude));
    }

    // ------------------------------------------------------------------ audio scoring

    public AudioChunkResponse analyseAudioChunk(UUID sessionId, byte[] audio) {
        SafetySession session = requireOpenSession(sessionId);
        requireAudio(audio);

        if (!rateLimiter.allowRequest(session.getUserId().toString(), 0.0, rateLimitBase, rateLimitWindowMs)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many audio chunks, slow down");
        }

        // A check-in is already running: don't start another one, just report its deadline.
        if (session.getStatus() == SafetySession.Status.CHECKIN_PENDING) {
            Optional<PendingCheckinStore.Pending> existing = pendingStore.peek(sessionId);
            return new AudioChunkResponse(
                    true,
                    existing.map(PendingCheckinStore.Pending::distressConfidence).orElse(0.0),
                    true,
                    existing.map(PendingCheckinStore.Pending::deadlineMs).orElse(null),
                    session.getStatus().name());
        }

        MlClient.DistressResult result;
        try {
            result = ml.predictDistress(audio);
        } catch (MlServiceException e) {
            // Do NOT guess: an unavailable model must not start a check-in or an alert.
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Distress model unavailable", e);
        }

        if (!result.isDistress()) {
            return new AudioChunkResponse(false, result.confidence(), false, null, session.getStatus().name());
        }

        long deadline = System.currentTimeMillis() + countdownMs;
        pendingStore.put(sessionId, new PendingCheckinStore.Pending(result.confidence(), deadline));
        session.setStatus(SafetySession.Status.CHECKIN_PENDING);
        sessions.save(session);
        return new AudioChunkResponse(true, result.confidence(), true, deadline, session.getStatus().name());
    }

    // ------------------------------------------------------------------ check-in

    /**
     * @param audio the user's spoken reply as WAV, or null/empty if they did not reply
     *              (the client calls this with no file when its countdown reaches zero)
     */
    public CheckinResponse handleCheckin(UUID sessionId, byte[] audio) {
        Object lock = checkinLocks.computeIfAbsent(sessionId, id -> new Object());
        synchronized (lock) {
            return processCheckin(sessionId, audio);
        }
    }

    private CheckinResponse processCheckin(UUID sessionId, byte[] audio) {
        // Re-read inside the lock so a second, simultaneous request sees the first one's result.
        SafetySession session = requireSession(sessionId);
        if (session.getStatus() != SafetySession.Status.CHECKIN_PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No check-in is waiting for this session");
        }

        // If the entry is missing (backend restarted mid-countdown) we cannot prove the reply
        // was in time, so it counts as unanswered and escalates.
        PendingCheckinStore.Pending pending = pendingStore.take(sessionId).orElse(null);
        boolean inTime = pending != null
                && CheckinPolicy.isInTime(System.currentTimeMillis(), pending.deadlineMs(), graceMs);
        boolean hasAudio = audio != null && audio.length > 0;

        String tone = null;
        Double toneConfidence = null;
        if (inTime && hasAudio) {
            try {
                MlClient.ToneResult result = ml.predictTone(audio);
                tone = result.tone();
                toneConfidence = result.confidence();
            } catch (MlServiceException e) {
                // Fail safe: tone stays null, so the policy escalates instead of dismissing.
            }
        }

        CheckinPolicy.Decision decision = CheckinPolicy.decide(inTime && hasAudio, tone);

        if (!decision.escalates()) {
            session.setStatus(SafetySession.Status.ACTIVE);
            sessions.save(session);
            return new CheckinResponse(decision.name(), false, tone, toneConfidence, null, session.getStatus().name());
        }

        Optional<LocationPing> last = pings.findFirstBySessionIdOrderByRecordedAtDesc(sessionId);
        Alert alert = alertService.raiseAlert(new AlertRequest(
                sessionId,
                session.getUserId(),
                last.map(LocationPing::getLatitude).orElse(null),
                last.map(LocationPing::getLongitude).orElse(null),
                pending == null ? null : pending.distressConfidence(),
                toneConfidence));
        session.setStatus(SafetySession.Status.ESCALATED);
        sessions.save(session);
        return new CheckinResponse(decision.name(), true, tone, toneConfidence, alert.getId(), session.getStatus().name());
    }

    // ------------------------------------------------------------------ helpers

    private SafetySession requireSession(UUID sessionId) {
        return sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    private SafetySession requireOpenSession(UUID sessionId) {
        SafetySession session = requireSession(sessionId);
        if (session.getStatus() != SafetySession.Status.ACTIVE
                && session.getStatus() != SafetySession.Status.CHECKIN_PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Session is " + session.getStatus() + ", not accepting new data");
        }
        return session;
    }

    private void requireAudio(byte[] audio) {
        if (audio == null || audio.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Audio file is required");
        }
    }
}
