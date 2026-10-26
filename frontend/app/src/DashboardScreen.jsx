import { useCallback, useEffect, useRef, useState } from "react";
import { MapContainer, Marker, Popup, TileLayer, useMap } from "react-leaflet";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import "./Dashboard.css";
import * as api from "./api";

const FALLBACK = [22.5726, 88.3639];
const WINDOW_MS = 5 * 60 * 1000; // the verifier only accepts alerts this fresh

// What the buttons submit to the verifier. "none" sends the alert exactly as stored.
const TESTS = [
  { mode: "none", label: "Verify this alert" },
  { mode: "none", label: "Send the same alert again (replay)" },
  { mode: "payload", label: "Change the location" },
  { mode: "signature", label: "Corrupt the signature" },
  { mode: "stale", label: "Make it 10 minutes old" },
];

const pct = (x) => (x == null ? "n/a" : Math.round(x * 100) + "%");
const hasPlace = (a) => a.latitude != null && a.longitude != null;
const isManual = (a) => a.distressConfidence == null && a.toneConfidence == null;

function ago(ms, now) {
  const s = Math.max(0, Math.round((now - ms) / 1000));
  if (s < 60) return s + " s ago";
  const m = Math.round(s / 60);
  if (m < 60) return m + " min ago";
  const h = Math.round(m / 60);
  return h < 24 ? h + " h ago" : Math.round(h / 24) + " d ago";
}

const sosIcon = (selected) =>
  L.divIcon({
    className: "",
    html: `<span class="pin sos${selected ? " sel" : ""}"></span>`,
    iconSize: [22, 22],
    iconAnchor: [11, 11],
  });

function FlyTo({ target }) {
  const map = useMap();
  const lat = target ? target[0] : null;
  const lng = target ? target[1] : null;
  useEffect(() => {
    if (lat != null) map.flyTo([lat, lng], Math.max(map.getZoom(), 14), { duration: 0.8 });
  }, [lat, lng, map]);
  return null;
}

export default function DashboardScreen() {
  const [alerts, setAlerts] = useState([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [now, setNow] = useState(Date.now());
  const [busy, setBusy] = useState(false);
  const [log, setLog] = useState([]);
  const logId = useRef(0);

  const load = useCallback(async () => {
    try {
      const list = await api.listAlerts();
      setAlerts([...list].sort((a, b) => b.signedTimestamp - a.signedTimestamp));
      setError(null);
    } catch (e) {
      setError(e.message);
    }
    setLoaded(true);
  }, []);

  // New alerts appear on their own: poll every 5 seconds while this tab is open.
  useEffect(() => {
    load();
    const poll = setInterval(load, 5000);
    const clock = setInterval(() => setNow(Date.now()), 5000);
    return () => {
      clearInterval(poll);
      clearInterval(clock);
    };
  }, [load]);

  // Each alert starts with a clean test log.
  useEffect(() => {
    setLog([]);
  }, [selectedId]);

  const selected = alerts.find((a) => a.id === selectedId) || null;
  const focus = selected || alerts.find(hasPlace) || null;
  const target = focus && hasPlace(focus) ? [focus.latitude, focus.longitude] : null;
  const verifiedCount = alerts.filter((a) => a.verificationStatus === "VERIFIED").length;
  const manualCount = alerts.filter(isManual).length;
  const tooOld = selected ? now - selected.signedTimestamp > WINDOW_MS : false;

  async function run(test) {
    if (!selected) return;
    setBusy(true);
    let entry;
    try {
      const r = await api.verifyAlert(selected.id, test.mode);
      entry = { label: test.label, result: r.result, accepted: r.accepted, explanation: r.explanation };
    } catch (e) {
      entry = { label: test.label, result: "ERROR", accepted: false, explanation: e.message };
    }
    logId.current += 1;
    setLog((prev) => [{ id: logId.current, ...entry }, ...prev].slice(0, 8));
    setBusy(false);
  }

  return (
    <div>
      <div className="mapwrap">
        <MapContainer center={FALLBACK} zoom={12} className="map">
          <TileLayer
            attribution="&copy; OpenStreetMap contributors"
            url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <FlyTo target={target} />
          {alerts.filter(hasPlace).map((a) => (
            <Marker
              key={a.id}
              position={[a.latitude, a.longitude]}
              icon={sosIcon(a.id === selectedId)}
              eventHandlers={{ click: () => setSelectedId(a.id) }}
            >
              <Popup>SOS raised {ago(a.signedTimestamp, now)}</Popup>
            </Marker>
          ))}
        </MapContainer>
      </div>

      <div className="panel">
        <h2>SOS alerts</h2>
        <p className="muted">
          Updates every 5 seconds. Tap an alert to see it on the map and test its verification.
        </p>

        <div className="stats">
          <div>
            <b>{alerts.length}</b>
            <span>Total alerts</span>
          </div>
          <div>
            <b>{verifiedCount}</b>
            <span>Verified</span>
          </div>
          <div>
            <b>{alerts.length - manualCount}</b>
            <span>AI-detected</span>
          </div>
          <div>
            <b>{manualCount}</b>
            <span>Manual</span>
          </div>
        </div>

        {error && <p className="note">Could not load alerts: {error}</p>}
        {loaded && !error && alerts.length === 0 && (
          <p className="muted">No alerts yet. Raise one from the Safety Mode tab.</p>
        )}

        <ul className="alerts">
          {alerts.slice(0, 10).map((a) => (
            <li key={a.id}>
              <button
                className={"alert-item" + (a.id === selectedId ? " on" : "")}
                onClick={() => setSelectedId(a.id)}
              >
                <span className="alert-top">
                  <strong>{isManual(a) ? "Manual SOS" : "AI-detected SOS"}</strong>
                  <span className="muted">{ago(a.signedTimestamp, now)}</span>
                </span>
                <span className="alert-sub">
                  <span className={"vbadge " + (a.verificationStatus === "VERIFIED" ? "ok" : "bad")}>
                    {a.verificationStatus}
                  </span>
                  <span className="muted">
                    {hasPlace(a) ? a.latitude.toFixed(4) + ", " + a.longitude.toFixed(4) : "No location"}
                  </span>
                </span>
                <span className="muted">
                  Voice distress {pct(a.distressConfidence)} · Tone {pct(a.toneConfidence)}
                </span>
              </button>
            </li>
          ))}
        </ul>
        {alerts.length > 10 && <p className="muted">Showing the latest 10 of {alerts.length}.</p>}

        {selected && (
          <section className="card verify">
            <h3>Check this alert</h3>
            <p className="muted">
              Every alert is signed. These buttons submit it to the verifier the way a receiver would,
              including copies and edited versions.
            </p>
            {tooOld && (
              <p className="note">
                This alert is more than 5 minutes old, so even an untouched copy is rejected as stale.
                Raise a fresh SOS to try the tests.
              </p>
            )}
            <div className="tests">
              {TESTS.map((t) => (
                <button key={t.label} className="btn" disabled={busy} onClick={() => run(t)}>
                  {t.label}
                </button>
              ))}
            </div>
            {log.length > 0 && (
              <ul className="log">
                {log.map((l) => (
                  <li key={l.id} className={l.accepted ? "ok" : "bad"}>
                    <strong>{l.label}</strong>
                    <span className="code">
                      {l.accepted ? "ACCEPTED" : "REJECTED"} · {l.result}
                    </span>
                    <span className="muted">{l.explanation}</span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        )}
      </div>
    </div>
  );
}
