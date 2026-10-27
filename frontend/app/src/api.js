import { API, USER_ID } from "./config";
import { getSession, logout } from "./auth";

async function call(path, opts = {}) {
  const token = getSession()?.token;
  const headers = { ...(opts.headers || {}), ...(token ? { Authorization: "Bearer " + token } : {}) };
  const r = await fetch(API + path, { ...opts, headers });
  if (!r.ok) {
    const text = await r.text();
    let message = text;
    try {
      message = JSON.parse(text).message || text; // the backend explains itself in a "message" field
    } catch {
      /* not JSON */
    }
    // A rejected token on a normal call means the login has expired: send the person back to sign in.
    if (r.status === 401 && token) logout();
    const err = new Error(String(message).slice(0, 160) || "Request failed (" + r.status + ")");
    err.status = r.status;
    throw err;
  }
  return r.status === 204 ? null : r.json();
}

function requireUser() {
  const id = getSession()?.userId || USER_ID;
  if (!id) throw new Error("You are not signed in.");
  return id;
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
export const listAlerts = () => call("/alerts");
// tamper: none | payload | signature | timestamp | stale (simulates an edited alert for demos)
export const verifyAlert = (id, tamper = "none") =>
  call(`/alerts/${id}/verify?tamper=${tamper}`, { method: "POST" });

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

// A pressed button involves no AI. But if a scream was already scored (the check-in was running),
// that score is sent too, so the alert shows as AI-detected instead of Manual.
export const manualAlert = async (sid, pos, distress = null) =>
  call(
    "/alerts",
    json({
      sessionId: sid,
      userId: requireUser(),
      latitude: pos ? pos.lat : null,
      longitude: pos ? pos.lon : null,
      distressConfidence: distress,
      toneConfidence: null,
    })
  );

// Trusted contacts
export const listContacts = () => call(`/contacts?userId=${requireUser()}`);
export const addContact = (name, phone, email) =>
  call("/contacts", json({ userId: requireUser(), name, phone: phone || null, email: email || null }));
export const deleteContact = (id) => call(`/contacts/${id}?userId=${requireUser()}`, { method: "DELETE" });

// Sign-up and login (public endpoints; see AuthService on the backend)
export const register = (name, email, password) => call("/auth/register", json({ name, email, password }));
export const confirmSetup = (token, code) => call("/auth/register/confirm", json({ token, code }));
export const login = (email, password) => call("/auth/login", json({ email, password }));
export const loginVerify = (token, code) => call("/auth/login/verify", json({ token, code }));
