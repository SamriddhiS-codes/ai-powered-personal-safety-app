package com.ascenddevs.safetyapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/**
 * Sends a WhatsApp message through Twilio's WhatsApp API (the free sandbox works for a demo).
 * It is optional: without TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN the app still works and simply skips WhatsApp.
 */
@Service
public class WhatsAppSender {

    private final String accountSid;
    private final String authToken;
    private final String from;
    private final String contentSid;
    private final int contentVars;
    private final RestClient http;

    public WhatsAppSender(@Value("${TWILIO_ACCOUNT_SID:}") String accountSid,
                          @Value("${TWILIO_AUTH_TOKEN:}") String authToken,
                          @Value("${TWILIO_WHATSAPP_FROM:whatsapp:+14155238886}") String from,
                          @Value("${TWILIO_CONTENT_SID:}") String contentSid,
                          @Value("${TWILIO_CONTENT_VARS:0}") int contentVars) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.from = from;
        this.contentSid = contentSid == null ? "" : contentSid.trim();
        this.contentVars = contentVars;
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    public boolean configured() {
        return !accountSid.isBlank() && !authToken.isBlank();
    }

    /** True when messages are sent from a Twilio Content Template (needed on newer sandbox accounts). */
    public boolean usesTemplate() {
        return !contentSid.isBlank();
    }

    /**
     * Builds the form Twilio expects. Without a template id the text goes in Body. With one (TWILIO_CONTENT_SID),
     * Twilio sends that template instead: its first TWILIO_CONTENT_VARS blanks are filled from the variables
     * (a template with no blanks, such as the sandbox's sample, takes none). Pure, so it can be unit-tested.
     */
    static MultiValueMap<String, String> buildForm(String from, String toE164, String body,
                                                   List<String> variables, String contentSid, int contentVars) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("From", from);
        form.add("To", "whatsapp:" + toE164);
        if (contentSid == null || contentSid.isBlank()) {
            form.add("Body", body);
            return form;
        }
        form.add("ContentSid", contentSid);
        int n = Math.min(contentVars, variables.size());
        if (n > 0) {
            StringBuilder json = new StringBuilder("{");
            for (int i = 0; i < n; i++) {
                if (i > 0) json.append(',');
                json.append('"').append(i + 1).append("\":\"").append(escape(variables.get(i))).append('"');
            }
            form.add("ContentVariables", json.append('}').toString());
        }
        return form;
    }

    private static String escape(String v) {
        // Template blanks cannot hold line breaks, so they become spaces.
        return v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
    }

    /** Sends one message. Throws if Twilio refuses it, so the caller can report the failure. */
    public void send(String toE164, String body, List<String> variables) {
        MultiValueMap<String, String> form = buildForm(from, toE164, body, variables, contentSid, contentVars);
        http.post()
                .uri("https://api.twilio.com/2010-04-01/Accounts/{sid}/Messages.json", accountSid)
                .headers(h -> h.setBasicAuth(accountSid, authToken))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .toBodilessEntity();
    }

    /**
     * "+91 98765-43210" gives "+919876543210". A bare 10-digit number is assumed to be Indian (+91),
     * the same rule the frontend uses for its WhatsApp links. Returns null if the number is not usable.
     */
    public static String toE164(String phone) {
        if (phone == null) return null;
        String cleaned = ContactValidator.cleanPhone(phone);
        if (cleaned.startsWith("+")) {
            return cleaned.substring(1).matches("[0-9]{7,15}") ? cleaned : null;
        }
        if (!cleaned.matches("[0-9]{7,15}")) return null;
        return cleaned.length() == 10 ? "+91" + cleaned : "+" + cleaned;
    }
}
