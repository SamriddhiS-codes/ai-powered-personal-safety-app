package com.ascenddevs.safetyapp.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a Twilio error response into a plain sentence the person can act on. Pure, so it can be unit-tested. */
public final class TwilioErrors {

    private static final Pattern CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");

    private TwilioErrors() {
    }

    public static String explain(String responseBody, int httpStatus) {
        int code = -1;
        if (responseBody != null) {
            Matcher m = CODE.matcher(responseBody);
            if (m.find()) code = Integer.parseInt(m.group(1));
        }
        if (code == 21654) {
            return "Twilio wants a message template here. Set TWILIO_CONTENT_SID (see docs/WHATSAPP_SETUP.md).";
        }
        if (code == 63015) {
            return "This phone has not joined the Twilio sandbox. From that phone's WhatsApp, send the join message to the sandbox number.";
        }
        if (code == 63016) {
            return "More than 24 hours since this contact last messaged the sandbox. Ask them to send the join message again.";
        }
        if (code == 63007) {
            return "Twilio cannot find the sandbox number. Check TWILIO_WHATSAPP_FROM.";
        }
        if (code == 21211 || code == 21614) {
            return "That phone number is not valid for WhatsApp. Check the number and its country code.";
        }
        if (code == 20003 || httpStatus == 401) {
            return "Twilio rejected the Account SID or Auth Token. Check TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN.";
        }
        return code > 0
                ? "WhatsApp could not deliver (Twilio error " + code + ")."
                : "WhatsApp could not deliver (Twilio status " + httpStatus + ").";
    }
}
