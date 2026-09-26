-- AI-Powered Personal Safety App — Database Schema (PostgreSQL)
-- Team: Ascend Devs

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- for gen_random_uuid()

-- ============================================================
-- USERS
-- ============================================================
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    device_secret   VARCHAR(255) NOT NULL,      -- per-device HMAC signing key, issued at registration
    role            VARCHAR(20)  NOT NULL DEFAULT 'USER', -- USER, RESPONDER, ANALYST
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- TRUSTED CONTACTS
-- ============================================================
CREATE TABLE trusted_contacts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    phone       VARCHAR(20),
    email       VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- SAFETY SESSIONS  (one row per "Safety Mode" activation)
-- ============================================================
CREATE TABLE safety_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, DISMISSED, ESCALATED, ENDED
    started_at  TIMESTAMP NOT NULL DEFAULT now(),
    ended_at    TIMESTAMP
);

-- ============================================================
-- LOCATION PINGS (for live GPS trail while a session is active)
-- ============================================================
CREATE TABLE location_pings (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES safety_sessions(id) ON DELETE CASCADE,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    recorded_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- ALERTS  (signed, verifiable SOS events)
-- ============================================================
CREATE TABLE alerts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id            UUID NOT NULL REFERENCES safety_sessions(id) ON DELETE CASCADE,
    user_id               UUID NOT NULL REFERENCES users(id),
    latitude              DOUBLE PRECISION,
    longitude             DOUBLE PRECISION,
    distress_confidence   DOUBLE PRECISION,   -- from CNN distress-sound model
    tone_confidence       DOUBLE PRECISION,   -- from voice check-in tone model
    payload               TEXT NOT NULL,      -- canonical JSON payload that was signed
    nonce                 VARCHAR(64) NOT NULL UNIQUE,
    signature             VARCHAR(255) NOT NULL,
    signed_timestamp      BIGINT NOT NULL,    -- epoch millis, part of the signed payload
    verification_status   VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- VERIFIED, REJECTED_REPLAY, REJECTED_INVALID, REJECTED_STALE
    created_at            TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_alerts_nonce ON alerts(nonce);
CREATE INDEX idx_alerts_user ON alerts(user_id);
CREATE INDEX idx_sessions_user ON safety_sessions(user_id);
CREATE INDEX idx_pings_session ON location_pings(session_id);

-- ============================================================
-- AUDIT LOG  (explainability / investigator trail)
-- ============================================================
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id    UUID REFERENCES alerts(id) ON DELETE CASCADE,
    action      VARCHAR(50) NOT NULL,   -- e.g. DISTRESS_FLAGGED, CHECKIN_TRIGGERED, ESCALATED, VERIFIED, REJECTED_REPLAY
    detail      TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_alert ON audit_log(alert_id);
