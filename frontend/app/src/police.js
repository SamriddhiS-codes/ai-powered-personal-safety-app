const OVERPASS = "https://overpass-api.de/api/interpreter";

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

async function query(pos, radius) {
  const q = `[out:json][timeout:20];(node["amenity"="police"](around:${radius},${pos.lat},${pos.lon});way["amenity"="police"](around:${radius},${pos.lat},${pos.lon}););out center tags;`;
  const res = await fetch(OVERPASS, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: "data=" + encodeURIComponent(q),
  });
  if (!res.ok) throw new Error("Overpass " + res.status);
  const json = await res.json();
  return json.elements
    .map((e) => ({
      id: e.type + e.id,
      name: e.tags?.name || "Police station",
      lat: e.lat ?? e.center?.lat,
      lon: e.lon ?? e.center?.lon,
      phone: e.tags?.phone || e.tags?.["contact:phone"] || null,
    }))
    .filter((s) => s.lat != null && s.lon != null);
}

// Widens the search (3 km, 8 km, 20 km) until something is found.
export async function nearestPolice(pos) {
  for (const radius of [3000, 8000, 20000]) {
    const found = await query(pos, radius);
    if (found.length) {
      return found
        .map((s) => ({ ...s, km: distanceKm(pos, s) }))
        .sort((a, b) => a.km - b.km)
        .slice(0, 5);
    }
  }
  return [];
}