package com.ascenddevs.safetyapp.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TwilioErrorsTest {

    @Test
    void explainsTheSandboxJoinProblem() {
        String body = "{\"code\": 63015, \"message\": \"Channel Sandbox can only send messages to phone numbers that have joined the Sandbox\", \"status\": 400}";
        assertTrue(TwilioErrors.explain(body, 400).contains("not joined the Twilio sandbox"));
    }

    @Test
    void explainsBadCredentialsAndWrongSender() {
        assertTrue(TwilioErrors.explain("{\"code\": 20003}", 401).contains("Auth Token"));
        assertTrue(TwilioErrors.explain("{\"code\": 63007}", 400).contains("TWILIO_WHATSAPP_FROM"));
        assertTrue(TwilioErrors.explain("", 401).contains("Auth Token"));
    }

    @Test
    void fallsBackToTheCodeOrStatus() {
        assertTrue(TwilioErrors.explain("{\"code\": 99999}", 400).contains("99999"));
        assertTrue(TwilioErrors.explain(null, 500).contains("500"));
    }
}
