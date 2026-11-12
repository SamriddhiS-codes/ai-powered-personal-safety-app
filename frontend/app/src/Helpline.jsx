import { useEffect, useRef, useState } from "react";
import * as Icon from "./Icons";
import "./Helpline.css";

// Scripted (not AI-generated) on purpose: in an emergency every answer must be verified and predictable.
// India helplines - re-check numbers before you ship.
const CALL = {
  112: ["112", "Emergency (police, fire, ambulance)"],
  100: ["100", "Police"],
  1091: ["1091", "Women helpline"],
  181: ["181", "Women helpline (counselling)"],
  1098: ["1098", "Childline"],
  1930: ["1930", "Cyber crime helpline"],
  14416: ["14416", "Tele-MANAS mental health"],
  108: ["108", "Ambulance"],
};
const calls = (...ids) => ids.map((i) => CALL[i]);

const FLOWS = {
  danger: {
    chip: "I'm in danger now",
    keys: ["danger", "help me", "attack", "emergency", "sos"],
    text: "Call 112 right now. If you can't talk, open the Safety tab and tap Activate: it listens and can alert your trusted contacts.",
    calls: calls(112, 100),
  },
  nearby: {
    chip: "Nearest police / hospital",
    keys: ["nearest", "nearby", "near me", "police station", "hospital", "station", "clinic"],
    text: "The Nearby tab shows police stations and hospitals around your current location on a map, with call and directions buttons.",
    action: ["Open Nearby map", "map"],
    calls: calls(100, 108),
  },
  followed: {
    chip: "I'm being followed",
    keys: ["follow", "stalk", "behind me"],
    text: "Stay calm and keep moving. Here's what helps:",
    steps: [
      "Head to a crowded, well-lit place: a shop, petrol pump, or station.",
      "Don't go home directly. Call someone and stay on the line.",
      "Tell a trusted contact your live location.",
      "Ask staff at a shop to call the police.",
    ],
    calls: calls(112, 100),
  },
  cab: {
    chip: "Unsafe cab / auto ride",
    keys: ["cab", "auto", "taxi", "uber", "ola", "driver", "rickshaw"],
    text: "Try to stay in control without confronting the driver:",
    steps: [
      "Share the ride details and number plate with a contact now.",
      "Keep your phone in hand and on a call.",
      "Ask to stop at a busy, lit spot and get out there.",
      "If the route changes or you feel threatened, call 112.",
    ],
    calls: calls(112, 1091),
  },
  harass: {
    chip: "Harassment / being touched",
    keys: ["harass", "touch", "molest", "catcall", "eve tease", "assault", "abuse"],
    text: "It isn't your fault, and you can ask for help. If you can, move away and make noise to draw attention. You can report it any time, even later.",
    calls: calls(1091, 181, 112),
  },
  online: {
    chip: "Online abuse / blackmail",
    keys: ["cyber", "online", "blackmail", "photo", "hack", "morph", "leak"],
    text: "Don't pay or reply. Save screenshots and links, block the account, and report it. You can also file at cybercrime.gov.in.",
    calls: calls(1930, 112),
  },
  mind: {
    chip: "I feel scared / need to talk",
    keys: ["anxious", "panic", "sad", "scared", "talk", "hopeless", "lonely", "stress"],
    text: "I'm glad you reached out. Slow breathing can help for a minute: in for 4, hold for 4, out for 6. A trained counsellor is also available any time, free of charge. If you are in immediate danger, call 112.",
    calls: calls(14416, 112),
  },
  child: {
    chip: "Help for a child",
    keys: ["child", "kid", "minor"],
    text: "Childline is a 24x7 service for any child in trouble. If a child is in immediate danger, call 112.",
    calls: calls(1098, 112),
  },
  numbers: {
    chip: "All helpline numbers",
    keys: ["number", "helpline", "call", "contact"],
    text: "Tap any number to call:",
    calls: calls(112, 100, 1091, 181, 108, 1098, 1930, 14416),
  },
  app: {
    chip: "How does this app work?",
    keys: ["how", "app", "work", "safety mode", "detect"],
    text: "Safety Mode listens for short bursts of audio, and an AI model flags possible distress. It then asks you to check in by voice. If it's a real emergency (or you don't answer), a signed SOS goes to your trusted contacts. It only listens while Safety Mode is on.",
  },
};

const GREETING = {
  from: "bot",
  text: "Hi, I'm your safety helper. Tell me what's happening, or pick one below. I give verified guidance and one-tap helplines.",
};

function reply(input) {
  const q = input.toLowerCase();
  const hit = Object.values(FLOWS).find((f) => f.keys.some((k) => q.includes(k)));
  return (
    hit || {
      text: "I may not have understood. Pick one of the options below, or call 112 if you're in danger.",
      calls: calls(112),
    }
  );
}

export default function Helpline({ onNavigate }) {
  const [open, setOpen] = useState(false);
  const [msgs, setMsgs] = useState([GREETING]);
  const [typing, setTyping] = useState(false);
  const [text, setText] = useState("");
  const endRef = useRef(null);
  const timer = useRef(0);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [msgs, typing, open]);
  useEffect(() => () => clearTimeout(timer.current), []);

  function ask(label, flow) {
    setMsgs((m) => [...m, { from: "me", text: label }]);
    setTyping(true);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => {
      setTyping(false);
      setMsgs((m) => [...m, { from: "bot", ...flow }]);
    }, 550);
  }
  function submit(e) {
    e.preventDefault();
    const t = text.trim();
    if (!t) return;
    setText("");
    ask(t, reply(t));
  }

  return (
    <>
      {open && (
        <section className="hl-panel" role="dialog" aria-label="Safety helper">
          <div className="hl-head">
            <span className="hl-ava"><Icon.Lifebuoy size={20} /></span>
            <div>
              <b>Safety Helper</b>
              <small>Verified helplines, always on</small>
            </div>
            <button className="hl-x" onClick={() => setOpen(false)} aria-label="Close chat">
              <Icon.Close size={18} />
            </button>
          </div>

          <div className="hl-body">
            {msgs.map((m, i) => (
              <div key={i} className={"hl-msg " + m.from}>
                <p>{m.text}</p>
                {m.steps && (
                  <ol>{m.steps.map((s) => <li key={s}>{s}</li>)}</ol>
                )}
                {m.action && onNavigate && (
                  <button className="hl-action" onClick={() => { onNavigate(m.action[1]); setOpen(false); }}>
                    <Icon.Pin size={16} /> {m.action[0]}
                  </button>
                )}
                {m.calls && (
                  <div className="hl-calls">
                    {m.calls.map(([n, label]) => (
                      <a key={n} href={"tel:" + n} className={n === "112" ? "sos" : ""}>
                        <Icon.Phone size={16} />
                        <span><b>{n}</b> {label}</span>
                      </a>
                    ))}
                  </div>
                )}
              </div>
            ))}
            {typing && <div className="hl-msg bot typing"><i /><i /><i /></div>}
            <div ref={endRef} />
          </div>

          <div className="hl-chips">
            {Object.values(FLOWS).map((f) => (
              <button key={f.chip} onClick={() => ask(f.chip, f)}>{f.chip}</button>
            ))}
          </div>
          <form className="hl-input" onSubmit={submit}>
            <input value={text} onChange={(e) => setText(e.target.value)} placeholder="Type what's happening…" aria-label="Message" />
            <button type="submit" aria-label="Send"><Icon.Send size={18} /></button>
          </form>
        </section>
      )}
      <button className={"hl-fab" + (open ? " off" : "")} onClick={() => setOpen(true)} aria-label="Open safety helper">
        <Icon.Chat size={24} />
        <span>Help</span>
      </button>
    </>
  );
}
