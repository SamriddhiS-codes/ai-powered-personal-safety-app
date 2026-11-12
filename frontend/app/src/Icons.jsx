// Small inline icon set (stroke icons, inherit currentColor) - replaces emoji for a consistent look.
const mk = (paths) =>
  function Icon({ size = 20, ...rest }) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor"
        strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" {...rest}>
        {paths}
      </svg>
    );
  };

export const Shield = mk(<><path d="M12 3l7 3v5c0 4.5-3 8.2-7 10-4-1.8-7-5.5-7-10V6l7-3z" /><path d="M9 12l2 2 4-4" /></>);
export const Mic = mk(<><rect x="9" y="3" width="6" height="11" rx="3" /><path d="M5 11a7 7 0 0014 0M12 18v3" /></>);
export const Brain = mk(<><path d="M3 12h3l2-5 4 10 3-8 2 3h4" /></>);
export const Clock = mk(<><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>);
export const Tone = mk(<><path d="M4 10v4M8 7v10M12 4v16M16 8v8M20 11v2" /></>);
export const Lock = mk(<><rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 018 0v3" /></>);
export const Pin = mk(<><path d="M12 21s7-6.2 7-11a7 7 0 10-14 0c0 4.8 7 11 7 11z" /><circle cx="12" cy="10" r="2.5" /></>);
export const Bell = mk(<><path d="M6 16V11a6 6 0 1112 0v5l1.5 2h-15L6 16z" /><path d="M10 21h4" /></>);
export const Users = mk(<><circle cx="9" cy="8" r="3.2" /><path d="M3 20c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5" /><path d="M16 5.3a3.2 3.2 0 010 5.9M18 14.8c1.8.7 3 2.4 3 5.2" /></>);
export const Info = mk(<><circle cx="12" cy="12" r="9" /><path d="M12 11v5M12 8h.01" /></>);
export const Chat = mk(<><path d="M4 5h16v11H9l-5 4V5z" /><path d="M8 10h8M8 13h5" /></>);
export const Send = mk(<><path d="M4 12l16-8-6 16-3-7-7-1z" /></>);
export const Close = mk(<><path d="M6 6l12 12M18 6L6 18" /></>);
export const Phone = mk(<><path d="M5 4h4l2 5-2.5 1.5a11 11 0 005 5L15 13l5 2v4a2 2 0 01-2 2A16 16 0 013 6a2 2 0 012-2z" /></>);
export const Lifebuoy = mk(<><circle cx="12" cy="12" r="9" /><circle cx="12" cy="12" r="3.5" /><path d="M5.6 5.6l3.9 3.9M14.5 14.5l3.9 3.9M18.4 5.6l-3.9 3.9M9.5 14.5l-3.9 3.9" /></>);
