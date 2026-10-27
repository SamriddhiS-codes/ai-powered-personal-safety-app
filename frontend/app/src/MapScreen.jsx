import { useEffect, useRef, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import { distanceKm, nearestPolice } from "./police";

const FALLBACK = { lat: 22.5726, lon: 88.3639 };
const LAST_KEY = "safety.lastpos";

// The last real position is remembered, so the map can appear at once instead of waiting for a fresh location fix.
function lastKnown() {
  try {
    const p = JSON.parse(localStorage.getItem(LAST_KEY) || "null");
    return p && typeof p.lat === "number" && typeof p.lon === "number" ? p : null;
  } catch {
    return null;
  }
}

// Standard OpenStreetMap tiles (no key needed).
const TILES = "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png";

const dot = (cls) =>
  L.divIcon({
    className: "",
    html: `<span class="pin ${cls}"></span>`,
    iconSize: [18, 18],
    iconAnchor: [9, 9],
  });
const meIcon = dot("me");
const policeIcon = dot("police");

function Recenter({ pos, tick }) {
  const map = useMap();
  useEffect(() => {
    map.setView([pos.lat, pos.lon], Math.max(map.getZoom(), 15));
  }, [tick, map]); // eslint-disable-line react-hooks/exhaustive-deps
  return null;
}

export default function MapScreen() {
  const [initial] = useState(lastKnown);
  const [pos, setPos] = useState(initial ?? FALLBACK);
  const [located, setLocated] = useState(!!initial); // false until a real position (or a refusal) is known
  const [usingFallback, setUsingFallback] = useState(false);
  const [stations, setStations] = useState([]);
  const [status, setStatus] = useState("loading");
  const [tick, setTick] = useState(0);
  const [retry, setRetry] = useState(0);
  const lastFetch = useRef(null);
  const gotFix = useRef(false);

  useEffect(() => {
    if (!navigator.geolocation) {
      setUsingFallback(true);
      setLocated(true);
      return;
    }
    const id = navigator.geolocation.watchPosition(
      (p) => {
        const here = { lat: p.coords.latitude, lon: p.coords.longitude };
        setUsingFallback(false);
        setPos(here);
        setLocated(true);
        if (!gotFix.current) {
          gotFix.current = true;
          setTick((t) => t + 1); // move the map to the real position once, without fighting the user's panning
        }
        try {
          localStorage.setItem(LAST_KEY, JSON.stringify(here));
        } catch {
          /* ignore */
        }
      },
      () => {
        setUsingFallback(true);
        setLocated(true);
      },
      // A laptop has no GPS, so asking for high accuracy only makes the first fix slower.
      { enableHighAccuracy: false, maximumAge: 30000, timeout: 10000 }
    );
    return () => navigator.geolocation.clearWatch(id);
  }, []);

  // Only re-search when you have moved more than 500 m.
  useEffect(() => {
    if (!located) return; // wait for the real position, so only one search is made
    if (lastFetch.current && distanceKm(lastFetch.current, pos) < 0.5) return;
    lastFetch.current = pos;
    setStatus("loading");
    nearestPolice(pos)
      .then((s) => {
        setStations(s);
        setStatus("ok");
      })
      .catch(() => setStatus("error"));
  }, [pos, retry, located]);

  return (
    <div>
      <div className="mapwrap">
        <MapContainer center={[pos.lat, pos.lon]} zoom={15} className="map" zoomControl={false}>
          <TileLayer
            attribution="&copy; OpenStreetMap contributors"
            url={TILES}
            maxZoom={19}
            keepBuffer={4}
          />
          <Recenter pos={pos} tick={tick} />
          <Marker position={[pos.lat, pos.lon]} icon={meIcon}>
            <Popup>You are here</Popup>
          </Marker>
          {stations.map((s) => (
            <Marker key={s.id} position={[s.lat, s.lon]} icon={policeIcon}>
              <Popup>
                {s.name}
                <br />
                {s.km.toFixed(1)} km away
              </Popup>
            </Marker>
          ))}
        </MapContainer>
        <button className="recenter" onClick={() => setTick((t) => t + 1)}>
          Recenter
        </button>
      </div>

      <section className="panel">
        {!located && <p className="muted">Finding your exact location…</p>}
        {usingFallback && (
          <p className="note">
            Location is off, so this is a demo position. Allow location in your browser for real results.
          </p>
        )}
        <h2>Nearest police stations</h2>
        {status === "loading" && (
          <>
            <p className="muted">Searching OpenStreetMap… the first search can take a few seconds.</p>
            <ul className="stations">
              {[0, 1, 2].map((i) => (
                <li key={i} className="skel" aria-hidden="true" />
              ))}
            </ul>
          </>
        )}
        {status === "error" && (
          <p className="note">
            Couldn’t load stations. The map servers may be busy.{" "}
            <button
              className="linkbtn"
              onClick={() => {
                lastFetch.current = null;
                setRetry((n) => n + 1);
              }}
            >
              Try again
            </button>
          </p>
        )}
        {status === "ok" && stations.length === 0 && (
          <p className="muted">
            No stations found within 20 km on OpenStreetMap. Coverage can be incomplete, so call 112 if you need help.
          </p>
        )}
        <ul className="stations">
          {stations.map((s) => (
            <li key={s.id}>
              <div>
                <strong>{s.name}</strong>
                <span className="muted">{s.km.toFixed(1)} km away</span>
              </div>
              <div className="actions">
                <a
                  href={`https://www.google.com/maps/dir/?api=1&destination=${s.lat},${s.lon}&travelmode=walking`}
                  target="_blank"
                  rel="noreferrer"
                >
                  Directions
                </a>
                {s.phone && <a href={`tel:${s.phone.split(";")[0].replace(/[^\d+]/g, "")}`}>Call</a>}
              </div>
            </li>
          ))}
        </ul>
        <p className="fine">
          Station data comes from OpenStreetMap and may be incomplete. This app does not contact the police for you.
        </p>
        <a className="sos112" href="tel:112">
          Call emergency number 112
        </a>
      </section>
    </div>
  );
}