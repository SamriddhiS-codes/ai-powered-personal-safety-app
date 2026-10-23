package com.ascenddevs.safetyapp.service;

import com.ascenddevs.safetyapp.service.CheckinPolicy.Decision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** White-box tests: every branch of the confirmation-window decision logic. */
class CheckinPolicyTest {

    @Test
    void calmReplyInTime_isDismissed() {
        Decision d = CheckinPolicy.decide(true, "calm");
        assertEquals(Decision.DISMISSED_CALM, d);
        assertFalse(d.escalates());
    }

    @Test
    void frightenedReplyInTime_escalates() {
        Decision d = CheckinPolicy.decide(true, "frightened");
        assertEquals(Decision.ESCALATED_FRIGHTENED, d);
        assertTrue(d.escalates());
    }

    @Test
    void noReply_escalates_evenIfToneLooksCalm() {
        Decision d = CheckinPolicy.decide(false, "calm");
        assertEquals(Decision.ESCALATED_NO_RESPONSE, d);
        assertTrue(d.escalates());
    }

    @Test
    void noReplyAndNoTone_escalates() {
        assertEquals(Decision.ESCALATED_NO_RESPONSE, CheckinPolicy.decide(false, null));
    }

    @Test
    void unknownOrMissingTone_failsSafeAndEscalates() {
        assertEquals(Decision.ESCALATED_UNCLEAR, CheckinPolicy.decide(true, null));
        assertEquals(Decision.ESCALATED_UNCLEAR, CheckinPolicy.decide(true, "sad"));
        assertTrue(CheckinPolicy.decide(true, null).escalates());
    }

    @Test
    void isInTime_boundaries() {
        long deadline = 100_000;
        long grace = 3_000;
        assertTrue(CheckinPolicy.isInTime(90_000, deadline, grace));   // before deadline
        assertTrue(CheckinPolicy.isInTime(100_000, deadline, grace));  // exactly at deadline
        assertTrue(CheckinPolicy.isInTime(103_000, deadline, grace));  // last moment of grace
        assertFalse(CheckinPolicy.isInTime(103_001, deadline, grace)); // one ms too late
    }
}
