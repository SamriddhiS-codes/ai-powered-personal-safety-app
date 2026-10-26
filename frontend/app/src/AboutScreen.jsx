import "./About.css";

const STEPS = [
  {
    icon: "🎙️",
    title: "Listens only when you ask",
    text: "One tap starts Safety Mode. It checks short 4-second clips, and there is no always-on listening.",
  },
  {
    icon: "🧠",
    title: "AI spots distress sounds",
    text: "A CNN reads each clip as a spectrogram and scores how much it sounds like a scream. At 60% or more, a check-in starts.",
  },
  {
    icon: "🗣️",
    title: "“Are you okay?” check-in",
    text: "A 30-second countdown begins. A calm reply dismisses it as a false alarm, such as laughter or loud banter.",
  },
  {
    icon: "🎚️",
    title: "Voice tone decides",
    text: "A second model judges whether the reply sounds calm or frightened. A frightened, unclear, late or missing reply raises the SOS.",
  },
  {
    icon: "🔏",
    title: "The alert is signed",
    text: "Each alert carries an HMAC-SHA256 signature, a one-time nonce and a timestamp, so it cannot be forged, edited or replayed.",
  },
  {
    icon: "✅",
    title: "Responders can verify it",
    text: "In the Alerts tab, the verifier accepts a genuine alert once and rejects copies, edited alerts and old alerts.",
  },
];

const LIMITS = [
  "A spoken \"I'm fine\" cancels the check-in directly, because the tone model proved unreliable on real microphones. Tone is checked only on the \"Send voice for tone check\" button and when the countdown ends. A coerced \"I'm fine\" is a known limitation.",
  "The tone model is about 75% accurate on speakers it never heard (acted English speech). It is a proof of concept, which is why you can always cancel during the countdown.",
  "Real distress in Bengali or Hindi, in a noisy street, will sound different from the training data.",
  "Used nonces are kept in the server's memory. A production system would use a shared store such as Redis.",
  "Audio clips are analysed and not saved. The optional spoken-word detection uses your browser's speech recognition, which may send audio to the browser vendor.",
  "This app supports, and does not replace, calling 112.",
];

const STACK = ["React", "Spring Boot", "PostgreSQL", "FastAPI", "PyTorch", "librosa", "HMAC-SHA256"];

export default function AboutScreen() {
  return (
    <div className="about">
      <section className="about-hero">
        <h2>How it keeps you safe</h2>
        <p>
          Passive distress detection, a voice check-in to cut false alarms, and alerts that can be proven
          genuine.
        </p>
      </section>

      <section>
        <h3>From sound to verified SOS</h3>
        <ol className="steps">
          {STEPS.map((s) => (
            <li key={s.title} className="step">
              <span className="step-icon">{s.icon}</span>
              <div>
                <strong>{s.title}</strong>
                <p>{s.text}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section className="card">
        <h3>Honest limits</h3>
        <ul className="limits">
          {LIMITS.map((l) => (
            <li key={l}>{l}</li>
          ))}
        </ul>
      </section>

      <section>
        <h3>Built with</h3>
        <div className="chips">
          {STACK.map((t) => (
            <span key={t} className="chip">
              {t}
            </span>
          ))}
          <span className="chip sdg">SDG 5 · Gender Equality</span>
          <span className="chip sdg">SDG 16 · Strong Institutions</span>
        </div>
      </section>
    </div>
  );
}
