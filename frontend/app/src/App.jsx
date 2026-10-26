import { useState } from "react";
import MapScreen from "./MapScreen";
import SafetyScreen from "./SafetyScreen";
import DashboardScreen from "./DashboardScreen";
import AboutScreen from "./AboutScreen";
import "./App.css";
import "./theme.css";

export default function App() {
  const [tab, setTab] = useState("safety");
  return (
    <div className="shell">
      <header>
        <h1>Safety App</h1>
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
      {tab === "about" && <AboutScreen />}
    </div>
  );
}