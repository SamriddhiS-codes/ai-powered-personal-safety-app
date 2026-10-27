import { useState, useSyncExternalStore } from "react";
import MapScreen from "./MapScreen";
import SafetyScreen from "./SafetyScreen";
import DashboardScreen from "./DashboardScreen";
import AboutScreen from "./AboutScreen";
import ContactsScreen from "./ContactsScreen";
import AuthScreen from "./AuthScreen";
import { getSession, subscribe, logout } from "./auth";
import "./App.css";
import "./theme.css";
import "./Contacts.css";
import "./Auth.css";

export default function App() {
  const [tab, setTab] = useState("safety");
  const session = useSyncExternalStore(subscribe, getSession);
  if (!session) {
    return (
      <div className="shell">
        <header>
          <div className="top">
            <h1>Safety App</h1>
          </div>
        </header>
        <AuthScreen />
      </div>
    );
  }
  return (
    <div className="shell">
      <header>
        <div className="top">
          <h1>Safety App</h1>
          <button className="signout" onClick={logout} title={"Signed in as " + session.name}>
            Sign out
          </button>
        </div>
        <nav>
          <button className={tab === "safety" ? "on" : ""} onClick={() => setTab("safety")}>
            Safety
          </button>
          <button className={tab === "map" ? "on" : ""} onClick={() => setTab("map")}>
            Nearby
          </button>
          <button className={tab === "alerts" ? "on" : ""} onClick={() => setTab("alerts")}>
            Alerts
          </button>
          <button className={tab === "contacts" ? "on" : ""} onClick={() => setTab("contacts")}>
            Contacts
          </button>
          <button className={tab === "about" ? "on" : ""} onClick={() => setTab("about")}>
            About
          </button>
        </nav>
      </header>
      <div hidden={tab !== "safety"}>
        <SafetyScreen />
      </div>
      {tab === "map" && <MapScreen />}
      {tab === "alerts" && <DashboardScreen />}
      {tab === "contacts" && <ContactsScreen />}
      {tab === "about" && <AboutScreen />}
    </div>
  );
}