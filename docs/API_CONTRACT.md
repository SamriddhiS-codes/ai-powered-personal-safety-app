# API Contract

Two services expose APIs: the **Spring Boot backend** (core system, port `8080`) and the
**Python ML service** (internal only, port `8000`). The frontend only ever talks to the backend;
the backend calls the ML service internally.

---

## 1. Backend API (Spring Boot) — `http://localhost:8080/api`

### Auth
| Method | Endpoint | Description |
|---|---|---|
| POST | `/auth/register` | Create a user account, issues a `device_secret` (HMAC key) |
| POST | `/auth/login` | Returns a short-lived JWT (standard session auth — **not** the security algorithm being graded) |

### Safety Sessions
| Method | Endpoint | Description |
|---|---|---|
| POST | `/sessions/start` | Body `{ "userId": uuid }`. Activates Safety Mode. Returns `sessionId` and `status`. |
| POST | `/sessions/{id}/ping` | Push a GPS coordinate `{ "latitude": .., "longitude": .. }` while the session is open. Returns 204. |
| POST | `/sessions/{id}/audio-chunk` | Multipart upload, part name `file` (16-bit PCM WAV). Scores it via the ML service. If distress is flagged, the session moves to `CHECKIN_PENDING` and the response gives `checkinDeadlineEpochMs`. |
| POST | `/sessions/{id}/checkin-response` | Multipart, part `file` (WAV) is optional: send no file when the countdown runs out. Calm reply in time dismisses; frightened, unclear, late or missing reply raises a signed SOS alert. |
| POST | `/sessions/{id}/end` | Ends the session (user is confirmed safe) |
| GET  | `/sessions/{id}` | Session status + latest location |

### Alerts
| Method | Endpoint | Description |
|---|---|---|
| POST | `/alerts` | Create + **sign** a new SOS alert (see `SignedAlert` below). Called internally when a session escalates. |
| POST | `/alerts/{id}/verify` | Verifies a submitted alert's signature, nonce and timestamp. Used by tests and by any external submission path. |
| GET  | `/alerts` | List alerts (responder/analyst dashboard) — supports `?status=VERIFIED\|REJECTED_REPLAY\|...` |
| GET  | `/alerts/{id}` | Alert detail incl. confidence scores, location, verification status |

### Trusted Contacts
| Method | Endpoint | Description |
|---|---|---|
| GET / POST / DELETE | `/contacts` | Manage a user's trusted contacts |

### Alert payload shape

```json
{
  "sessionId": "uuid",
  "userId": "uuid",
  "latitude": 22.5726,
  "longitude": 88.3639,
  "distressConfidence": 0.87,
  "toneConfidence": 0.81,
  "timestamp": 1732600000000,
  "nonce": "b64-random-16-bytes",
  "signature": "base64-hmac-sha256"
}
```

`signature = HMAC_SHA256(device_secret, payload_json_without_signature + "|" + timestamp + "|" + nonce)`,
matching `AlertSigningService` in the backend (see `/backend/.../security`).

---

## 2. ML Service API (FastAPI) — `http://localhost:8000` (internal)

| Method | Endpoint | Request | Response |
|---|---|---|---|
| POST | `/predict/distress/audio` | multipart `file` (WAV) | `{ "is_distress": bool, "confidence": float }` |
| POST | `/predict/tone/audio` | multipart `file` (WAV) | `{ "tone": "calm" \| "frightened", "confidence": float }` |
| POST | `/predict/distress`, `/predict/tone` | pre-made `mel_spectrogram` (128 x 173); wrong shape returns 422 | same as above |
| GET  | `/health` | — | `{ "status": "ok" }` |

The ML service turns audio into a spectrogram itself (`app/features.py`, the same code used for training),
so the backend just forwards the uploaded WAV.

---

## Testing hooks (black-box / white-box)

- **Black-box:** every endpoint above is a clean input → output contract — feed known
  audio/payload fixtures and assert the response, with zero knowledge of internals.
- **White-box:** `AlertSigningService` and `AdaptiveRateLimiter` are pure, dependency-free
  classes — unit-testable with full branch coverage (see `backend/src/test/.../security`).
