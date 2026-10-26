import { useState } from "react";
import MapScreen from "./MapScreen";
import SafetyScreen from "./SafetyScreen";
import DashboardScreen from "./DashboardScreen";
import "./App.css";

export default function App() {
  const [tab, setTab] = useState("safety");
  return (
    <div className="shell">
      <header>
        <h1>Safety App</h1>
        <nav>
          <button className={tab === "safety" ? "on" : ""} onClick={() => setTab("safety")}>
            Safety Mode
          </button>
          <button className={tab === "map" ? "on" : ""} onClick={() => setTab("map")}>
            Nearby help
          </button>
          <button className={tab === "alerts" ? "on" : ""} onClick={() => setTab("alerts")}>
            Alerts
          </button>
        </nav>
      </header>
      <div hidden={tab !== "safety"}>
        <SafetyScreen />
      </div>
      {tab === "map" && <MapScreen />}
      {tab === "alerts" && <DashboardScreen />}
    </div>
  );
}