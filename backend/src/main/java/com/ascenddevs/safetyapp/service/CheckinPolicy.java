package com.ascenddevs.safetyapp.service;

/**
 * The confirmation-window decision logic, kept as pure static functions (no Spring,
 * no database, no ML) so every branch can be unit-tested directly.
 *
 * Rules (from the project design):
 *   - no reply, or a reply that arrives after the countdown -> escalate
 *   - reply judged "frightened"                              -> escalate
 *   - reply judged "calm"                                    -> dismiss as a false alarm
 *   - anything else (unknown tone, model failure)            -> escalate, because a
 *     missed emergency is worse than one extra alert that the user can cancel
 */
public final class CheckinPolicy {

    private CheckinPolicy() {
    }

    public enum Decision {
        DISMISSED_CALM(false),
        ESCALATED_NO_RESPONSE(true),
        ESCALATED_FRIGHTENED(true),
        ESCALATED_UNCLEAR(true);

        private final boolean escalates;

        Decision(boolean escalates) {
            this.escalates = escalates;
        }

        public boolean escalates() {
            return escalates;
        }
    }

    /** True if a reply received at nowMs still counts (deadline plus a small network grace). */
    public static boolean isInTime(long nowMs, long deadlineMs, long graceMs) {
        return nowMs <= deadlineMs + graceMs;
    }

    /**
     * @param respondedInTime true only if the user sent a usable reply before the deadline
     * @param tone            "calm", "frightened", or null/anything else if unknown
     */
    public static Decision decide(boolean respondedInTime, String tone) {
        if (!respondedInTime) {
            return Decision.ESCALATED_NO_RESPONSE;
        }
        if ("calm".equals(tone)) {
            return Decision.DISMISSED_CALM;
        }
        if ("frightened".equals(tone)) {
            return Decision.ESCALATED_FRIGHTENED;
        }
        return Decision.ESCALATED_UNCLEAR;
    }
}
