import "./Guardian.css";

// Animated mascot for the Safety Helper. mood: "idle" | "talk"
export default function Guardian({ size = 64, mood = "idle", className = "" }) {
  return (
    <svg
      className={`gd gd-${mood} ${className}`}
      width={size}
      height={Math.round(size * 1.1)}
      viewBox="0 0 100 110"
      role="img"
      aria-label="Guardian, your safety helper"
    >
      <defs>
        <linearGradient id="gd-body" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#5fe0b0" />
          <stop offset="1" stopColor="#1f9a70" />
        </linearGradient>
      </defs>

      <ellipse className="gd-shadow" cx="50" cy="105" rx="22" ry="4" />

      <g className="gd-float">
        <line x1="15" y1="62" x2="6" y2="78" stroke="#2fb386" strokeWidth="8" strokeLinecap="round" />
        <circle cx="6" cy="80" r="5.5" fill="#ffd9b8" />
        <g className="gd-wave">
          <line x1="85" y1="62" x2="95" y2="42" stroke="#2fb386" strokeWidth="8" strokeLinecap="round" />
          <circle cx="96" cy="39" r="6" fill="#ffd9b8" />
        </g>

        <path
          d="M50 6 L88 20 V50 C88 77 71 95 50 103 C29 95 12 77 12 50 V20 Z"
          fill="url(#gd-body)"
          stroke="#12694b"
          strokeWidth="3"
          strokeLinejoin="round"
        />
        <path
          d="M50 14 L80 25.5 V50 C80 72 66 87 50 94"
          fill="none"
          stroke="#ffffff"
          strokeOpacity=".28"
          strokeWidth="3"
          strokeLinecap="round"
        />

        <path
          d="M50 17l2.6 5.2 5.7.8-4.1 4 1 5.7-5.2-2.7-5.2 2.7 1-5.7-4.1-4 5.7-.8z"
          fill="#ffe27a"
          stroke="#d9a400"
          strokeWidth="1"
          strokeLinejoin="round"
        />

        <g className="gd-eyes">
          <ellipse cx="37" cy="50" rx="6.5" ry="7.5" fill="#fff" />
          <ellipse cx="63" cy="50" rx="6.5" ry="7.5" fill="#fff" />
          <circle cx="38.2" cy="51" r="3.6" fill="#12313a" />
          <circle cx="64.2" cy="51" r="3.6" fill="#12313a" />
          <circle cx="39.4" cy="49.6" r="1.2" fill="#fff" />
          <circle cx="65.4" cy="49.6" r="1.2" fill="#fff" />
        </g>
        <circle cx="27" cy="62" r="4.6" fill="#ff9aa2" opacity=".6" />
        <circle cx="73" cy="62" r="4.6" fill="#ff9aa2" opacity=".6" />
        <path className="gd-smile" d="M42 64 Q50 72 58 64" fill="none" stroke="#12313a" strokeWidth="3" strokeLinecap="round" />
        <ellipse className="gd-talk-mouth" cx="50" cy="66" rx="5.5" ry="4.5" fill="#12313a" />
      </g>
    </svg>
  );
}