// Per-developer settings come from frontend/app/.env.local (never committed).
// Copy .env.example to .env.local, fill it in, then restart `npm run dev`.
export const API = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
// TEMPORARY: the test user, until login exists.
export const USER_ID = import.meta.env.VITE_USER_ID;
