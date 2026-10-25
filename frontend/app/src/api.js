import { API, USER_ID } from "./config";

async function call(path, opts) {
  const r = await fetch(API + path, opts);
  if (!r.ok) throw new Error(`${r.status} ${(await r.text()).slice(0, 120)}`);
  return r.status === 204 ? null : r.json();
}

const json = (body) => ({
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});

export const startSession = () => call("/sessions/start", json({ userId: USER_ID }));
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

export const manualAlert = (sid, pos) =>
  call(
    "/alerts",
    json({
      sessionId: sid,
      userId: USER_ID,
      latitude: pos ? pos.lat : null,
      longitude: pos ? pos.lon : null,
      distressConfidence: 1,
      toneConfidence: 1,
    })
  );