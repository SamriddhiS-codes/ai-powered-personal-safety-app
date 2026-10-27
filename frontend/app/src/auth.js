// Holds the login session (token + user) in memory and in localStorage, and lets screens subscribe to changes.
const KEY = "safety.session";
const listeners = new Set();

function load() {
  try {
    const s = JSON.parse(localStorage.getItem(KEY) || "null");
    return s && s.expiresAtMs > Date.now() ? s : null;
  } catch {
    return null;
  }
}

let session = load();

export const getSession = () => session;
export const subscribe = (fn) => {
  listeners.add(fn);
  return () => listeners.delete(fn);
};

function emit() {
  listeners.forEach((fn) => fn());
}

export function setSession(s) {
  session = s;
  try {
    localStorage.setItem(KEY, JSON.stringify(s));
  } catch {
    /* storage can be blocked; the session then lasts until the page is closed */
  }
  emit();
}

export function logout() {
  session = null;
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* ignore */
  }
  emit();
}
