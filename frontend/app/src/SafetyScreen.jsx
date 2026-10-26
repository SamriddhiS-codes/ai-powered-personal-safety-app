import { useEffect, useRef, useState } from "react";
import { Recorder } from "./audio";
import * as api from "./api";

const pct = (x) => (x == null ? "n/a" : Math.round(x * 100) + "%");

// Phrases that mean "I'm okay": I'm fine / I am okay / it was nothing / false alarm ...
const OK_PHRASE =
  /\b(i'?m|i am|im)\s+(fine|okay|ok|alright|all right|safe|good)\b|\bit was (nothing|nothin)\b|\bfalse alarm\b/;

// Calls for help during the check-in raise the SOS immediately.
const HELP_PHRASE = /\b(help|save me|leave me alone|let me go)\b/;
const NOT_HELP = /\b(don'?t|do not|no|not|without)\s+(need\s+)?(any\s+)?help\b/;

function Meter({ level }) {
  const db = level.rms > 0 ? Math.max(-60, 20 * Math.log10(level.rms)) : -60;
  const fill = ((db + 60) / 60) * 100;
  const word = db < -45 ? "Quiet" : db < -30 ? "Speaking" : db < -18 ? "Loud" : "Very loud";
  return (
    <div className="meter" aria-label={`Voice level: ${word}`}>
      <div className="meter-track">
        <div className="meter-cover" style={{ width: 100 - fill + "%" }} />
      </div>
      <div className="meter-scale">
        <span>Quiet</span>
        <span>Speaking</span>
        <span>Loud</span>
        <span>Very loud</span>
      </div>
      <div className="meter-read">
        {word} · {Math.round(db)} dB
      </div>
    </div>
  );
}

async function demoFile(name) {
  const r = await fetch("/demo/" + name);
  if (!r.ok || !(r.headers.get("content-type") || "").includes("audio"))
    throw new Error("Demo clip missing: " + name);
  return r.blob();
}

export default function SafetyScreen() {
  const [mode, setMode] = useState("idle"); // idle | listening | checkin | sending | alert
  const [msg, setMsg] = useState("");
  const [why, setWhy] = useState("");
  const [left, setLeft] = useState(0);
  const [level, setLevel] = useState({ rms: 0, peak: 0 });
  const [alert, setAlert] = useState(null);
  const [last, setLast] = useState(null);
  const [micName, setMicName] = useState("");
  const [heardText, setHeardText] = useState("");

  const modeRef = useRef("idle");
  const sid = useRef(null);
  const rec = useRef(null);
  const pos = useRef(null);
  const geo = useRef(null);
  const timers = useRef({});
  const busy = useRef(false);
  const deadline = useRef(0);
  const total = useRef(30000);
  const lastSound = useRef(Date.now());
  const recog = useRef(null);

  const go = (m) => {
    modeRef.current = m;
    if (m !== "checkin") stopWordCheck();
    setMode(m);
  };

  // Listens for "I'm fine" / "help" using the browser's speech recognition.
  function startWordCheck() {
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) {
      setMsg("Word detection needs Chrome or Edge. Use the buttons instead.");
      return;
    }
    stopWordCheck();
    const r = new SR();
    r.lang = "en-IN";
    r.continuous = true;
    r.interimResults = true;
    r.onresult = (e) => {
      const text = Array.from(e.results)
        .map((x) => x[0].transcript)
        .join(" ")
        .toLowerCase()
        .replace(/[’`]/g, "'")
        .replace(/[^a-z\s']/g, " ");
      setHeardText(text.trim());
      if (modeRef.current !== "checkin") return;
      if (HELP_PHRASE.test(text) && !NOT_HELP.test(text)) {
        helpSos(text.trim());
      } else if (OK_PHRASE.test(text)) {
        cancelCheck();
      }
    };
    r.onend = () => {
      // Chrome stops recognition after a while, so restart it while the check-in is open.
      if (modeRef.current === "checkin" && recog.current === r) {
        try {
          r.start();
        } catch (err) {
          /* already running */
        }
      }
    };
    r.onerror = () => {};
    recog.current = r;
    try {
      r.start();
    } catch (err) {
      /* already running */
    }
  }

  function stopWordCheck() {
    const r = recog.current;
    recog.current = null;
    if (r) {
      r.onend = null;
      try {
        r.stop();
      } catch (err) {
        /* not running */
      }
    }
  }

  function stopAll() {
    clearInterval(timers.current.chunk);
    clearInterval(timers.current.ping);
    clearInterval(timers.current.cd);
    stopWordCheck();
    if (geo.current != null) navigator.geolocation.clearWatch(geo.current);
    geo.current = null;
    rec.current?.stop();
    rec.current = null;
  }

  useEffect(() => () => stopAll(), []);

  function watchLocation() {
    if (!navigator.geolocation) return;
    geo.current = navigator.geolocation.watchPosition(
      (p) => (pos.current = { lat: p.coords.latitude, lon: p.coords.longitude }),
      () => {},
      { enableHighAccuracy: true }
    );
    timers.current.ping = setInterval(() => {
      if (pos.current && sid.current)
        api.ping(sid.current, pos.current.lat, pos.current.lon).catch(() => {});
    }, 10000);
  }

  async function start() {
    setMsg("");
    setWhy("");
    setAlert(null);
    setLast(null);
    try {
      rec.current = new Recorder((l) => {
        setLevel(l);
        if (l.rms > 0.003) lastSound.current = Date.now();
      });
      await rec.current.start();
      setMicName(rec.current.label);
      lastSound.current = Date.now();
      const s = await api.startSession();
      sid.current = s.sessionId;
      go("listening");
      watchLocation();
      timers.current.chunk = setInterval(sendChunk, 4000);
    } catch (e) {
      setMsg("Could not start: " + e.message);
      await finish();
    }
  }

  async function sendChunk() {
    if (modeRef.current !== "listening" || busy.current || !rec.current) return;
    const wav = rec.current.take();
    if (!wav) return;
    busy.current = true;
    try {
      const r = await api.audioChunk(sid.current, wav);
      setLast(r.distressConfidence);
      if (r.checkinRequired) beginCheckin(r.checkinDeadlineEpochMs);
    } catch (e) {
      setMsg("Audio check failed: " + e.message);
    } finally {
      busy.current = false;
    }
  }

  async function demoScream() {
    if (modeRef.current !== "listening") return;
    busy.current = true;
    try {
      const r = await api.audioChunk(sid.current, await demoFile("scream.wav"));
      setLast(r.distressConfidence);
      if (r.checkinRequired) beginCheckin(r.checkinDeadlineEpochMs);
      else setMsg("Demo scream scored " + pct(r.distressConfidence) + ", below the check-in threshold.");
    } catch (e) {
      setMsg("Demo failed: " + e.message);
    } finally {
      busy.current = false;
    }
  }

  async function demoReply(name) {
    try {
      await submit(await demoFile(name));
    } catch (e) {
      setMsg("Demo failed: " + e.message);
    }
  }

  function beginCheckin(dl) {
    rec.current?.take(); // drop old audio so the reply starts clean
    deadline.current = dl;
    total.current = Math.max(1000, dl - Date.now());
    setHeardText("");
    go("checkin");
    startWordCheck();
    clearInterval(timers.current.cd);
    timers.current.cd = setInterval(() => {
      const ms = deadline.current - Date.now();
      setLeft(Math.max(0, Math.ceil(ms / 1000)));
      if (ms <= 0) {
        clearInterval(timers.current.cd);
        submit(null);
      }
    }, 250);
  }

  // Tone check on the last 3 seconds of audio (the "Send voice for tone check" button).
  function replyNow() {
    const wav = rec.current?.take(3);
    if (!wav) {
      setMsg("Keep talking a little longer, then tap send.");
      return;
    }
    setMsg("");
    submit(wav);
  }

  async function submit(wav) {
    if (modeRef.current !== "checkin") return;
    go("sending");
    clearInterval(timers.current.cd);
    try {
      const r = await api.checkin(sid.current, wav);
      const judged = r.tone
        ? `Reply judged: ${r.tone} (${pct(r.toneConfidence)}). `
        : "No reply heard. ";
      if (r.escalated) {
        setWhy(judged + "Outcome: " + r.outcome);
        await showAlert(r.alertId);
      } else {
        setMsg(judged + "Glad you're okay. Still listening.");
        rec.current?.take();
        go("listening");
      }
    } catch (e) {
      setMsg("Check-in failed: " + e.message);
      go("listening");
    }
  }

  // Cancels the check-in (spoken "I'm fine" or the button): ends the pending session, starts a fresh one.
  async function cancelCheck() {
    if (modeRef.current !== "checkin") return;
    go("sending");
    clearInterval(timers.current.cd);
    try {
      await api.endSession(sid.current).catch(() => {});
      const s = await api.startSession();
      sid.current = s.sessionId;
      rec.current?.take();
      setMsg("Glad you're okay. Alert cancelled, still listening.");
      go("listening");
    } catch (e) {
      setMsg("Could not cancel: " + e.message);
      go("listening");
    }
  }

  // A spoken call for help during the countdown raises the SOS straight away.
  async function helpSos(heard) {
    if (modeRef.current !== "checkin") return;
    go("sending");
    clearInterval(timers.current.cd);
    try {
      setWhy('You called for help ("' + heard + '"), so the SOS was sent immediately.');
      const a = await api.manualAlert(sid.current, pos.current);
      await showAlert(a.id);
    } catch (e) {
      setMsg("SOS failed: " + e.message);
      go("listening");
    }
  }

  async function manualSos() {
    go("sending");
    clearInterval(timers.current.cd);
    try {
      setWhy("You pressed Send SOS now.");
      const a = await api.manualAlert(sid.current, pos.current);
      await showAlert(a.id);
    } catch (e) {
      setMsg("SOS failed: " + e.message);
      go("listening");
    }
  }

  async function showAlert(id) {
    clearInterval(timers.current.chunk);
    rec.current?.stop();
    rec.current = null;
    go("alert");
    try {
      setAlert(id ? await api.getAlert(id) : null);
    } catch (e) {
      setMsg("Could not load alert: " + e.message);
    }
  }

  async function finish() {
    const id = sid.current;
    stopAll();
    sid.current = null;
    go("idle");
    if (id) {
      try {
        await api.endSession(id);
      } catch (e) {
        /* the session may already be closed after an SOS */
      }
    }
  }

  const ring = Math.min(100, (left * 1000 * 100) / total.current);
  const silent = mode === "listening" && Date.now() - lastSound.current > 8000;

  return (
    <div className="safety">
      {mode === "idle" && (
        <div className="center">
          <p className="muted">Tap once. It listens only while Safety Mode is on.</p>
          <h2>Feeling unsafe?</h2>
          <button className="big" onClick={start}>
            Activate
          </button>
        </div>
      )}

      {mode === "listening" && (
        <div className="center">
          <h2>Safety Mode is on</h2>
          <p className="muted">Microphone: {micName}. Location is shared while active.</p>
          <Meter level={level} />
          {silent && (
            <p className="note">
              No sound from the microphone. Check Windows Settings, Privacy, Microphone, and pick the right input
              device in the browser's site settings.
            </p>
          )}
          <p className="muted">
            {last == null
              ? "Waiting for the first 4-second check…"
              : `Last check: scream likelihood ${pct(last)} (a check-in starts at 60%)`}
          </p>
          <div className="row">
            <button className="btn" onClick={finish}>
              End Safety Mode
            </button>
            <button className="btn sos" onClick={manualSos}>
              Send SOS now
            </button>
          </div>
          <div className="demo">
            <span>Demo mode, for presentations</span>
            <button className="btn" onClick={demoScream}>
              Play demo scream
            </button>
          </div>
        </div>
      )}

      {(mode === "checkin" || mode === "sending") && (
        <div className="center">
          <div className="count">{mode === "sending" ? "…" : left}</div>
          <div className="bar">
            <i style={{ width: ring + "%" }} />
          </div>
          <h2>Are you okay?</h2>
          <p className="muted">
            Say "I'm fine", "I'm okay" or "it was nothing" to cancel. Say "help" to send an SOS at once. If nothing is
            heard before the timer ends, an SOS is raised.
          </p>
          <p className="muted">{heardText ? `Heard: "${heardText}"` : "Listening for your words…"}</p>
          <Meter level={level} />
          <button className="btn calm" disabled={mode === "sending"} onClick={cancelCheck}>
            False alarm, cancel
          </button>
          <div className="row">
            <button className="btn calm" disabled={mode === "sending"} onClick={replyNow}>
              Send voice for tone check
            </button>
            <button className="btn sos" disabled={mode === "sending"} onClick={manualSos}>
              Send SOS now
            </button>
          </div>
          <div className="demo">
            <span>Demo mode, for presentations</span>
            <div className="row">
              <button className="btn calm" disabled={mode === "sending"} onClick={() => demoReply("calm.wav")}>
                Demo calm reply
              </button>
              <button className="btn sos" disabled={mode === "sending"} onClick={() => demoReply("frightened.wav")}>
                Demo frightened reply
              </button>
            </div>
          </div>
        </div>
      )}

      {mode === "alert" && (
        <div className="alertbox">
          <span className="badge">SOS SENT</span>
          <h2>Alert raised and signed</h2>
          {why && <p className="note">{why}</p>}
          {alert ? (
            <div className="card">
              <div className="kv">
                <span>Verification</span>
                <b>{alert.verificationStatus}</b>
              </div>
              <div className="kv">
                <span>Distress confidence</span>
                <b>{pct(alert.distressConfidence)}</b>
              </div>
              <div className="kv">
                <span>Tone confidence</span>
                <b>{pct(alert.toneConfidence)}</b>
              </div>
              <div className="kv">
                <span>Location</span>
                <b>
                  {alert.latitude == null
                    ? "not shared"
                    : `${alert.latitude.toFixed(4)}, ${alert.longitude.toFixed(4)}`}
                </b>
              </div>
            </div>
          ) : (
            <p className="muted">Loading alert details…</p>
          )}
          <a className="sos112" href="tel:112">
            Call emergency number 112
          </a>
          <button className="btn solid" onClick={finish}>
            I'm safe, end session
          </button>
        </div>
      )}

      {msg && <p className="note">{msg}</p>}
    </div>
  );
}