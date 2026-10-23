package com.ascenddevs.safetyapp.controller;

import com.ascenddevs.safetyapp.dto.AudioChunkResponse;
import com.ascenddevs.safetyapp.dto.CheckinResponse;
import com.ascenddevs.safetyapp.dto.PingRequest;
import com.ascenddevs.safetyapp.dto.SessionResponse;
import com.ascenddevs.safetyapp.dto.StartSessionRequest;
import com.ascenddevs.safetyapp.entity.SafetySession;
import com.ascenddevs.safetyapp.service.SessionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.UUID;

/** Safety Mode session endpoints. See docs/API_CONTRACT.md. */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/start")
    public ResponseEntity<SessionResponse> start(@RequestBody StartSessionRequest request) {
        SafetySession session = sessionService.startSession(request.userId());
        return ResponseEntity.ok(SessionResponse.from(session, null));
    }

    @PostMapping("/{id}/ping")
    public ResponseEntity<Void> ping(@PathVariable UUID id, @RequestBody PingRequest request) {
        sessionService.recordPing(id, request.latitude(), request.longitude());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/audio-chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioChunkResponse> audioChunk(@PathVariable UUID id,
                                                         @RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(sessionService.analyseAudioChunk(id, readBytes(file)));
    }

    /** The "file" part is optional: sending none means the user did not reply before the countdown ended. */
    @PostMapping(value = "/{id}/checkin-response", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CheckinResponse> checkinResponse(
            @PathVariable UUID id,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        byte[] audio = (file == null) ? null : readBytes(file);
        return ResponseEntity.ok(sessionService.handleCheckin(id, audio));
    }

    @PostMapping("/{id}/end")
    public ResponseEntity<SessionResponse> end(@PathVariable UUID id) {
        SafetySession session = sessionService.endSession(id);
        return ResponseEntity.ok(SessionResponse.from(session, sessionService.lastLocation(id).orElse(null)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SessionResponse> get(@PathVariable UUID id) {
        SafetySession session = sessionService.getSession(id);
        return ResponseEntity.ok(SessionResponse.from(session, sessionService.lastLocation(id).orElse(null)));
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded audio", e);
        }
    }
}
