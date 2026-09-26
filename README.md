# AI-Powered Personal Safety App

Audio Distress Detection & Tamper-Proof SOS Alerts
**Team: Ascend Devs** — Jigisha Nag (IT-07) · Subhayoni Chandra (IT-15) · Samriddhi Sengupta (IT-21) · Ritwika Banerjee (IT-28)

A web-based personal safety platform. A voluntary, one-tap "Safety Mode" listens
for short bursts of ambient audio, uses a CNN to flag possible distress sounds,
confirms genuine danger through an interactive voice check-in (filtering out
false alarms like laughter or traffic), and — if confirmed — sends an SOS alert
that is cryptographically signed and replay-resistant, going well beyond
conventional login/password security.

## Repo layout

```
backend/         Spring Boot core system — sessions, alerts, security algorithm
ml-service/      Python FastAPI microservice — distress detection + voice-tone models
frontend/        Clickable HTML prototype + notes for the real React build
docs/            DB schema, API contract
```

## Architecture at a glance

```
 React frontend
      │  REST (JSON)
      ▼
 Spring Boot backend  ──internal call──►  FastAPI ML service
  (PostgreSQL)                             (PyTorch/scikit-learn)
      │
      ▼
 AlertSigningService (HMAC-SHA256 + nonce/timestamp)
 AdaptiveRateLimiter  (sliding window, risk-driven)
```

The security algorithm — `AlertSigningService` + `AdaptiveRateLimiter` in
`backend/.../security/` — is pure, dependency-free Java with full unit test
coverage in `backend/src/test/.../security/`. This is the part of the system
that's fully hand-built and fully explainable; the ML pieces sit in front of
it as a detection layer, not as the security guarantee itself.

## Getting started

### Backend
```bash
cd backend
mvn spring-boot:run
```
Requires PostgreSQL running locally with a `safety_app` database (see
`docs/DB_SCHEMA.sql` — run it once to create tables; `ddl-auto: validate` in
`application.yml` expects the schema to already exist).

### Run the security unit tests
```bash
cd backend
mvn test
```

### ML service
```bash
cd ml-service
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```
Runs immediately with a placeholder heuristic (see `PLACEHOLDER` flag in
`app/main.py`) so the rest of the system is demoable before training
finishes. To train real models: `python scripts/download_datasets.py` (needs
a Kaggle API key — see the script's docstring), then
`python scripts/train_distress_model.py`.

### Frontend
Just open `frontend/prototype.html` in a browser — no build step. See
`frontend/README.md` for how to wire it to the real backend.

## Software Engineering Model: V-Model

Chosen because requirements are fixed and well-understood up front, and the
grading rubric requires demonstrable black-box **and** white-box test
coverage — the V-Model pairs every build phase with a matching test phase:

| Development phase | Testing phase |
|---|---|
| Requirement analysis | Acceptance testing (black-box) |
| System design | System testing (black-box) |
| Module/architecture design | Integration testing |
| Implementation | Unit testing (white-box) |

## SDG Mapping

- **SDG 5 — Gender Equality:** low-effort, always-available help-seeking for
  users, especially women, in public spaces or unfamiliar transport.
- **SDG 16 — Peace, Justice & Strong Institutions:** tamper-resistant
  emergency-response infrastructure — alerts cannot be forged, altered, or replayed.

## Testing strategy

- **Black-box:** feed known calm / distress / check-in audio fixtures and
  crafted or replayed alert payloads to the API; assert correct classification,
  correct escalation/dismissal, and correct rejection of forged/replayed alerts —
  no knowledge of internals required.
- **White-box:** `AlertSigningServiceTest` and `AdaptiveRateLimiterTest` exercise
  every branch of the security layer directly (valid, replayed, tampered, stale,
  wrong-secret, per-key isolation, window expiry).

## Honest limitations (say this out loud in the demo/viva)

The distress and tone models are trained on public datasets (Kaggle scream
dataset, RAVDESS) at a scope appropriate for a semester project — expect
85–92% accuracy on curated test clips, in line with published benchmarks, not
production-grade real-world robustness. This is exactly why the system uses a
**two-step design** (sound detection *and* a voice check-in) rather than
trusting a single ML call — the architecture is built to compensate for the
model's limitations, not to hide them.
