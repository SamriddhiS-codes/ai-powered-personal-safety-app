import { API, USER_ID } from "./config";

async function call(path, opts) {
  const r = await fetch(API + path, opts);
  if (!r.ok) throw new Error(`${r.status} ${(await r.text()).slice(0, 120)}`);
  return r.status === 204 ? null : r.json();
}

function requireUser() {
  if (!USER_ID) {
    throw new Error("VITE_USER_ID is not set. Copy .env.example to .env.local, add your user id, and restart npm run dev.");
  }
  return USER_ID;
}

const json = (body) => ({
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const startSession = async () => call("/sessions/start", json({ userId: requireUser() }));
export const ping = (sid, lat, lon) =>
  call(`/sessions/${sid}/ping`, json({ latitude: lat, longitude: lon }));
export const endSession = (sid) => call(`/sessions/${sid}/end`, { method: "POST" });
export const getAlert = (id) => call(`/alerts/${id}`);

export function audioChunk(sid, wav) {
  const fd = new FormData();
  fd.append("file", wav, "chunk.wav");
  return call(`/sessions/${sid}/audio-chunk`, { method: "POST", body: fd });
}

// No file means "no reply before the countdown ended".
export function checkin(sid, wav) {
  const fd = new FormData();
  if (wav) fd.append("file", wav, "reply.wav");
  else fd.append("note", "none");
  return call(`/sessions/${sid}/checkin-response`, { method: "POST", body: fd });
}

// A pressed button involves no AI, so no confidence scores are sent (they show as n/a).
export const manualAlert = async (sid, pos) =>
  call(
    "/alerts",
    json({
      sessionId: sid,
      userId: requireUser(),
      latitude: pos ? pos.lat : null,
      longitude: pos ? pos.lon : null,
      distressConfidence: null,
      toneConfidence: null,
    })
  );
