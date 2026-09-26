# Frontend

`prototype.html` is a standalone, no-build clickable prototype of the core flow —
open it directly in a browser, no npm install needed. Use it as the visual
target when building the real React app.

Screens covered: Home (Safety Mode toggle) → Listening → Voice check-in
(with countdown) → Alert raised (map + signature/verification card) →
Responder dashboard (verified alerts + a rejected-replay example).

## Wiring it to the real backend

Replace the in-browser JS state machine with real calls to the backend API
(see `../docs/API_CONTRACT.md`):

- `Activate` button → `POST /api/sessions/start`
- Listening screen → stream short audio chunks to `POST /api/sessions/{id}/audio-chunk`
- Check-in countdown → record response, `POST /api/sessions/{id}/checkin-response`
- Alert screen → render the real response from `POST /api/alerts`
- Dashboard → `GET /api/alerts?status=VERIFIED` and `?status=REJECTED_REPLAY`
- Location → `navigator.geolocation.watchPosition(...)`, pushed via `POST /api/sessions/{id}/ping`

## Suggested real stack

React + TypeScript, reusing the same color tokens and layout from the
prototype's `<style>` block so the transition from prototype to production
build doesn't require a redesign.
