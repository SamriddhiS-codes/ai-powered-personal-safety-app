import { useState, useSyncExternalStore } from "react";
import MapScreen from "./MapScreen";
import SafetyScreen from "./SafetyScreen";
import DashboardScreen from "./DashboardScreen";
import AboutScreen from "./AboutScreen";
import ContactsScreen from "./ContactsScreen";
import AuthScreen from "./AuthScreen";
import Helpline from "./Helpline";
import * as Icon from "./Icons";
import { getSession, subscribe, logout } from "./auth";
import "./App.css";
import "./theme.css";
import "./Contacts.css";
import "./Auth.css";
import "./polish.css";

const TABS = [
  ["safety", "Safety", Icon.Shield],
  ["map", "Nearby", Icon.Pin],
  ["alerts", "Alerts", Icon.Bell],
  ["contacts", "Contacts", Icon.Users],
  ["about", "About", Icon.Info],
];

function Brand({ sub }) {
  return (
    <div className="brand">
      <span className="logo"><Icon.Shield size={22} /></span>
      <div>
        <h1>Safety App</h1>
        {sub && <small>{sub}</small>}
      </div>
    </div>
  );
}

export default function App() {
  const [tab, setTab] = useState("safety");
  const session = useSyncExternalStore(subscribe, getSession);
  if (!session) {
    return (
      <div className="shell">
        <header>
          <Brand sub="Your personal safety companion" />
        </header>
        <main className="screens"><AuthScreen /></main>
        <Helpline />
      </div>
    );
  }
  const first = String(session.name || "").split(" ")[0];
  return (
    <div className="shell">
      <header>
        <Brand sub={first ? "Hi, " + first : undefined} />
        <button className="signout" onClick={logout} title={"Signed in as " + session.name}>
          Sign out
        </button>
      </header>
      <main className="screens">
        <div hidden={tab !== "safety"}>
          <SafetyScreen />
        </div>
        {tab === "map" && <MapScreen />}
        {tab === "alerts" && <DashboardScreen />}
        {tab === "contacts" && <ContactsScreen />}
        {tab === "about" && <AboutScreen />}
      </main>
      <div className="tabbar" role="tablist" aria-label="Main">
        {TABS.map(([id, label, Ico]) => (
          <button key={id} role="tab" aria-selected={tab === id} className={tab === id ? "on" : ""} onClick={() => setTab(id)}>
            <Ico size={22} />
            <span>{label}</span>
          </button>
        ))}
      </div>
      <Helpline onNavigate={setTab} />
    </div>
  );
}
