import { useState } from "react";
import * as api from "./api";
import { setSession } from "./auth";
import "./Auth.css";

function strength(pw) {
  let n = 0;
  if (pw.length >= 8) n++;
  if (pw.length >= 12) n++;
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) n++;
  if (/\d/.test(pw) && /[^A-Za-z0-9]/.test(pw)) n++;
  return n; // 0..4
}
const WORDS = ["Too weak", "Weak", "Okay", "Good", "Strong"];

// Six-digit authenticator code. Submits by itself as soon as the sixth digit is typed.
function CodeBox({ onDone, busy }) {
  const [v, setV] = useState("");
  return (
    <input
      className="code"
      value={v}
      inputMode="numeric"
      autoComplete="one-time-code"
      autoFocus
      maxLength={6}
      placeholder="000000"
      disabled={busy}
      aria-label="6-digit code"
      onChange={(e) => {
        const d = e.target.value.replace(/\D/g, "").slice(0, 6);
        setV(d);
        if (d.length === 6) {
          onDone(d);
          setTimeout(() => setV(""), 300);
        }
      }}
    />
  );
}

export default function AuthScreen() {
  const [tab, setTab] = useState("login"); // login | register
  const [step, setStep] = useState("creds"); // creds | code | setup
  const [f, setF] = useState({ name: "", email: "", password: "" });
  const [show, setShow] = useState(false);
  const [token, setToken] = useState(null); // mfa token (login) 
  const [setup, setSetup] = useState(null); // authenticator setup info
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);
  const [copied, setCopied] = useState(false);

  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const score = strength(f.password);

  async function run(fn) {
    setBusy(true);
    setErr("");
    try {
      await fn();
    } catch (e) {
      setErr(e.message);
    } finally {
      setBusy(false);
    }
  }

  const finish = (s) => setSession(s);

  function submitCreds(e) {
    e.preventDefault();
    run(async () => {
      if (tab === "register") {
        setSetup(await api.register(f.name, f.email, f.password));
        setStep("setup");
      } else {
        const r = await api.login(f.email, f.password);
        if (r.status === "MFA_REQUIRED") {
          setToken(r.mfaToken);
          setStep("code");
        } else {
          setSetup(r.setup);
          setStep("setup");
        }
      }
    });
  }

  const submitLoginCode = (code) => run(async () => finish(await api.loginVerify(token, code)));
  const submitSetupCode = (code) => run(async () => finish(await api.confirmSetup(setup.setupToken, code)));

  function back() {
    setStep("creds");
    setErr("");
    setToken(null);
    setSetup(null);
    setF({ ...f, password: "" });
  }

  function copyKey() {
    navigator.clipboard?.writeText(setup.setupKey).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }

  return (
    <div className="auth">
      <div className="auth-hero">
        <span className="auth-logo">🛡️</span>
        <h2>{step === "creds" ? (tab === "login" ? "Welcome back" : "Create your account") : "Two-step verification"}</h2>
        <p className="muted">
          {step === "creds" && "Your safety, protected by a password and a one-time code."}
          {step === "code" && "Open your authenticator app and enter the 6-digit code for Safety App."}
          {step === "setup" && "One-time setup: link your authenticator app, then enter the code it shows."}
        </p>
      </div>

      {step === "creds" && (
        <>
          <div className="seg">
            <button className={tab === "login" ? "on" : ""} onClick={() => { setTab("login"); setErr(""); }}>
              Sign in
            </button>
            <button className={tab === "register" ? "on" : ""} onClick={() => { setTab("register"); setErr(""); }}>
              Sign up
            </button>
          </div>
          <form className="aform" onSubmit={submitCreds}>
            {tab === "register" && (
              <input placeholder="Full name" value={f.name} onChange={set("name")} required maxLength={100} autoComplete="name" />
            )}
            <input type="email" placeholder="Email" value={f.email} onChange={set("email")} required autoComplete="email" />
            <div className="pw">
              <input
                type={show ? "text" : "password"}
                placeholder="Password"
                value={f.password}
                onChange={set("password")}
                required
                autoComplete={tab === "login" ? "current-password" : "new-password"}
              />
              <button type="button" onClick={() => setShow(!show)} aria-label="Show or hide password">
                {show ? "Hide" : "Show"}
              </button>
            </div>
            {tab === "register" && (
              <div className="meter2" aria-live="polite">
                <div className="bars">
                  {[1, 2, 3, 4].map((i) => (
                    <i key={i} className={i <= score ? "on s" + score : ""} />
                  ))}
                </div>
                <span>{f.password ? WORDS[score] : "Use 8+ characters with letters and numbers"}</span>
              </div>
            )}
            <button className="btn solid" disabled={busy}>
              {busy ? "Please wait…" : tab === "login" ? "Continue" : "Create account"}
            </button>
          </form>
        </>
      )}

      {step === "code" && (
        <div className="astep">
          <CodeBox onDone={submitLoginCode} busy={busy} />
          <small className="muted">The code changes every 30 seconds and works only once.</small>
          <button className="link" onClick={back}>
            ← Use a different account
          </button>
        </div>
      )}

      {step === "setup" && setup && (
        <div className="astep">
          <ol className="how">
            <li>Install an authenticator app (Google Authenticator, Microsoft Authenticator or Authy).</li>
            <li>Add an account, choose “Enter a setup key”, and type this key:</li>
          </ol>
          <div className="key">
            <code>{setup.setupKey.match(/.{1,4}/g).join(" ")}</code>
            <button type="button" onClick={copyKey}>
              {copied ? "Copied" : "Copy"}
            </button>
          </div>
          <a className="link" href={setup.otpauthUri}>
            On your phone? Open in authenticator app
          </a>
          <p className="muted">3. Enter the 6-digit code the app shows:</p>
          <CodeBox onDone={submitSetupCode} busy={busy} />
          <button className="link" onClick={back}>
            ← Back
          </button>
        </div>
      )}

      {err && <p className="note">{err}</p>}

      <p className="fine2">🔒 Passwords are stored as bcrypt hashes. Sessions use signed HS256 tokens. Five wrong attempts lock the account for 15 minutes.</p>
    </div>
  );
}
