// Several public Overpass (OpenStreetMap) servers. They are asked at the same time and the first
// answer wins, because any single one of them is often slow or busy.
const SERVERS = [
  "https://overpass-api.de/api/interpreter",
  "https://overpass.kumi.systems/api/interpreter",
  "https://overpass.private.coffee/api/interpreter",
];
const REQUEST_TIMEOUT_MS = 25000; // the public servers can be slow; the first one to answer wins

// Police stations rarely move, so answers are remembered per ~1 km square.
const CACHE_KEY = "safety.police.v1";
const CACHE_TTL_MS = 12 * 60 * 60 * 1000;
const memory = new Map();

export function distanceKm(a, b) {
  const R = 6371;
  const rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(b.lat - a.lat);
  const dLon = rad(b.lon - a.lon);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

const cellKey = (pos) => pos.lat.toFixed(2) + "," + pos.lon.toFixed(2);

function cacheGet(pos) {
  const k = cellKey(pos);
  const m = memory.get(k);
  if (m && Date.now() - m.t < CACHE_TTL_MS) return m.list;
  try {
    const e = JSON.parse(localStorage.getItem(CACHE_KEY) || "{}")[k];
    if (e && Date.now() - e.t < CACHE_TTL_MS) {
      memory.set(k, e);
      return e.list;
    }
  } catch {
    /* storage unavailable: just search again */
  }
  return null;
}

function cacheSet(pos, list) {
  const k = cellKey(pos);
  const e = { t: Date.now(), list };
  memory.set(k, e);
  try {
    const all = JSON.parse(localStorage.getItem(CACHE_KEY) || "{}");
    all[k] = e;
    const keys = Object.keys(all);
    if (keys.length > 20) delete all[keys[0]]; // keep it small
    localStorage.setItem(CACHE_KEY, JSON.stringify(all));
  } catch {
    /* ignore */
  }
}

class EmptyAnswer extends Error {}

function toStation(e) {
  return {
    id: e.type + e.id,
    name: e.tags?.name || "Police station",
    lat: e.lat ?? e.center?.lat,
    lon: e.lon ?? e.center?.lon,
    phone: e.tags?.phone || e.tags?.["contact:phone"] || null,
  };
}

async function askOverpass(url, q, signal) {
  const res = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "data=" + encodeURIComponent(q),
    signal,
  });
  if (!res.ok) throw new Error("Overpass " + res.status);
  const json = await res.json();
  return json.elements.map(toStation).filter((s) => s.lat != null && s.lon != null);
}

// Nominatim is OpenStreetMap's search service. It is usually much quicker than Overpass for this question.
async function askNominatim(pos, radius, signal) {
  const dLat = radius / 111000;
  const dLon = radius / (111000 * Math.cos((pos.lat * Math.PI) / 180));
  const box = [pos.lon - dLon, pos.lat + dLat, pos.lon + dLon, pos.lat - dLat].join(",");
  const url = `https://nominatim.openstreetmap.org/search?q=police&format=jsonv2&limit=20&bounded=1&viewbox=${box}`;
  const res = await fetch(url, { signal });
  if (!res.ok) throw new Error("Nominatim " + res.status);
  const rows = await res.json();
  return rows
    .filter((r) => r.category === "amenity" && r.type === "police")
    .map((r) => ({
      id: r.osm_type + r.osm_id,
      name: r.name || r.display_name.split(",")[0] || "Police station",
      lat: parseFloat(r.lat),
      lon: parseFloat(r.lon),
      phone: null,
    }));
}

// Asks Nominatim and every Overpass server at once. The first one that finds stations wins and the rest are cancelled.
async function queryOnce(pos, radius) {
  const q = `[out:json][timeout:20];(node["amenity"="police"](around:${radius},${pos.lat},${pos.lon});way["amenity"="police"](around:${radius},${pos.lat},${pos.lon}););out center tags 40;`;
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), REQUEST_TIMEOUT_MS);
  const contenders = [askNominatim(pos, radius, ctrl.signal), ...SERVERS.map((u) => askOverpass(u, q, ctrl.signal))].map(
    (p) =>
      p.then((list) => {
        if (!list.length) throw new EmptyAnswer(); // an empty answer must not beat a server that has results
        return list;
      })
  );
  try {
    return await Promise.any(contenders);
  } catch (e) {
    // If a server answered properly but found nothing, that is a real "no stations here", not an error.
    if (e instanceof AggregateError && e.errors.some((x) => x instanceof EmptyAnswer)) return [];
    throw e;
  } finally {
    clearTimeout(timer);
    ctrl.abort(); // stop the slower requests
  }
}

async function query(pos, radius) {
  try {
    return await queryOnce(pos, radius);
  } catch (e) {
    console.warn("Police search failed, retrying once:", e);
    return queryOnce(pos, radius);
  }
}

const rank = (list, pos) =>
  list
    .map((s) => ({ ...s, km: distanceKm(pos, s) }))
    .sort((a, b) => a.km - b.km)
    .slice(0, 5);

// Remembered answer first (instant), otherwise a 5 km search, widening to 20 km only if nothing is found.
export async function nearestPolice(pos) {
  const cached = cacheGet(pos);
  if (cached) return rank(cached, pos);
  for (const radius of [5000, 20000]) {
    const found = await query(pos, radius);
    if (found.length) {
      cacheSet(pos, found);
      return rank(found, pos);
    }
  }
  return [];
}
