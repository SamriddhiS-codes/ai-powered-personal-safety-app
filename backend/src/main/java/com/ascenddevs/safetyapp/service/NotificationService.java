package com.ascenddevs.safetyapp.service;

import com.ascenddevs.safetyapp.entity.Alert;
import com.ascenddevs.safetyapp.entity.TrustedContact;
import com.ascenddevs.safetyapp.entity.User;
import com.ascenddevs.safetyapp.repository.TrustedContactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Emails the user's trusted contacts when an SOS is raised.
 * Sending happens on a background thread, so a slow or broken mail server can never delay or fail the SOS.
 * The outcome for each contact is kept in memory so the app can show "Emailed Asha" on the SOS screen.
 */
@Service
public class NotificationService {

    public record Result(String contactName, String maskedEmail, String status, String detail, String channel) {
    }

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.of("Asia/Kolkata"));

    private final TrustedContactRepository contacts;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final WhatsAppSender whatsapp;
    private final String from;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    // Outcomes of the most recent alerts (oldest dropped), keyed by alert id.
    private final Map<UUID, List<Result>> outcomes = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, List<Result>> e) {
                    return size() > 200;
                }
            });

    public NotificationService(TrustedContactRepository contacts,
                               ObjectProvider<JavaMailSender> mailSender,
                               @Value("${spring.mail.username:}") String from,
                               WhatsAppSender whatsapp) {
        this.contacts = contacts;
        this.mailSender = mailSender;
        this.from = from;
        this.whatsapp = whatsapp;
    }

    public List<Result> resultsFor(UUID alertId) {
        List<Result> r = outcomes.get(alertId);
        return r == null ? List.of() : new ArrayList<>(r);
    }

    private record Task(TrustedContact contact, String channel) {
    }

    /** Queues an email and a WhatsApp message per trusted contact (whichever details they have). Never throws. */
    public void notifyContacts(Alert alert, User user) {
        try {
            List<Task> tasks = new ArrayList<>();
            for (TrustedContact c : contacts.findByUserIdOrderByCreatedAtAsc(user.getId())) {
                if (c.getEmail() != null && !c.getEmail().isBlank()) tasks.add(new Task(c, "EMAIL"));
                if (c.getPhone() != null && !c.getPhone().isBlank()) tasks.add(new Task(c, "WHATSAPP"));
            }
            if (tasks.isEmpty()) {
                return;
            }
            List<Result> results = Collections.synchronizedList(new ArrayList<>());
            for (Task t : tasks) {
                String target = t.channel().equals("EMAIL") ? mask(t.contact().getEmail()) : maskPhone(t.contact().getPhone());
                results.add(new Result(t.contact().getName(), target, "SENDING", "", t.channel()));
            }
            outcomes.put(alert.getId(), results);
            for (int i = 0; i < tasks.size(); i++) {
                final int idx = i;
                final Task t = tasks.get(i);
                CompletableFuture.runAsync(() -> results.set(idx, t.channel().equals("EMAIL")
                        ? send(alert, user, t.contact())
                        : sendWhatsApp(alert, user, t.contact())), pool);
            }
        } catch (Exception e) {
            log.warn("Could not queue trusted-contact messages for alert {}: {}", alert.getId(), e.toString());
        }
    }

    private Result send(Alert alert, User user, TrustedContact c) {
        String masked = mask(c.getEmail());
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || from == null || from.isBlank()) {
            return new Result(c.getName(), masked, "NOT_CONFIGURED",
                    "Email is not set up on the server (MAIL_USERNAME / MAIL_PASSWORD).", "EMAIL");
        }
        try {
            SimpleMailMessage m = buildMessage(from, alert, user, c);
            sender.send(m);
            log.info("SOS email sent to {} for alert {}", masked, alert.getId());
            return new Result(c.getName(), masked, "SENT", "", "EMAIL");
        } catch (Exception e) {
            log.warn("SOS email to {} failed: {}", masked, e.toString());
            return new Result(c.getName(), masked, "FAILED", "The mail server refused or could not be reached.", "EMAIL");
        }
    }

    private Result sendWhatsApp(Alert alert, User user, TrustedContact c) {
        String masked = maskPhone(c.getPhone());
        if (!whatsapp.configured()) {
            return new Result(c.getName(), masked, "NOT_CONFIGURED",
                    "WhatsApp is not set up on the server (TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN).", "WHATSAPP");
        }
        String to = WhatsAppSender.toE164(c.getPhone());
        if (to == null) {
            return new Result(c.getName(), masked, "FAILED", "The phone number is not valid for WhatsApp.", "WHATSAPP");
        }
        try {
            whatsapp.send(to, whatsappBody(alert, user, c), whatsappVars(alert, user));
            log.info("SOS WhatsApp sent to {} for alert {}", masked, alert.getId());
            return new Result(c.getName(), masked, "SENT", "", "WHATSAPP");
        } catch (RestClientResponseException e) {
            // Twilio answered with an error: log its reason and show the person what to do about it.
            log.warn("SOS WhatsApp to {} failed: HTTP {} {}", masked, e.getStatusCode().value(), e.getResponseBodyAsString());
            return new Result(c.getName(), masked, "FAILED",
                    TwilioErrors.explain(e.getResponseBodyAsString(), e.getStatusCode().value()), "WHATSAPP");
        } catch (Exception e) {
            log.warn("SOS WhatsApp to {} failed: {}", masked, e.toString());
            return new Result(c.getName(), masked, "FAILED",
                    "WhatsApp could not be sent (the server could not reach Twilio).", "WHATSAPP");
        }
    }

    /** Blanks for a WhatsApp template: {{1}} name, {{2}} time, {{3}} map link. */
    static List<String> whatsappVars(Alert alert, User user) {
        String link = alert.getLatitude() != null && alert.getLongitude() != null
                ? "https://www.google.com/maps?q=" + alert.getLatitude() + "," + alert.getLongitude()
                : "not shared";
        return List.of(user.getName(), TIME.format(Instant.ofEpochMilli(alert.getSignedTimestamp())) + " IST", link);
    }

    /** Short WhatsApp version of the alert (WhatsApp shows *text* in bold). Pure, so it can be unit-tested. */
    static String whatsappBody(Alert alert, User user, TrustedContact c) {
        StringBuilder b = new StringBuilder();
        b.append("*SOS from the Safety App*\n\n");
        b.append("Hi ").append(c.getName()).append(", ").append(user.getName())
                .append(" may be in danger and listed you as a trusted contact.\n\n");
        b.append("Time: ").append(TIME.format(Instant.ofEpochMilli(alert.getSignedTimestamp()))).append(" (IST)\n");
        if (alert.getLatitude() != null && alert.getLongitude() != null) {
            b.append("Location: https://www.google.com/maps?q=")
                    .append(alert.getLatitude()).append(',').append(alert.getLongitude()).append('\n');
        } else {
            b.append("Location: not shared\n");
        }
        if (alert.getDistressConfidence() != null) {
            b.append("Detected by AI: distress sound ").append(Math.round(alert.getDistressConfidence() * 100)).append("%\n");
        } else {
            b.append("Raised manually by pressing the SOS button.\n");
        }
        b.append("\nPlease call ").append(user.getName()).append(" right away. ");
        b.append("If you cannot reach them, call the police on 112 and share the location above.\n");
        b.append("Alert ID: ").append(alert.getId());
        return b.toString();
    }

    /** +919876543210 becomes +91*****10 : recognisable without exposing the full number. */
    static String maskPhone(String phone) {
        String d = phone == null ? "" : phone.replaceAll("[^0-9+]", "");
        if (d.length() < 7) return d;
        return d.substring(0, 3) + "*****" + d.substring(d.length() - 2);
    }

    /** Pure message builder (no Spring beans), so it can be unit-tested. */
    static SimpleMailMessage buildMessage(String from, Alert alert, User user, TrustedContact c) {
        SimpleMailMessage m = new SimpleMailMessage();
        m.setFrom(from);
        m.setTo(c.getEmail());
        m.setSubject("SOS: " + user.getName() + " may be in danger");
        m.setText(body(alert, user, c));
        return m;
    }

    static String body(Alert alert, User user, TrustedContact c) {
        StringBuilder b = new StringBuilder();
        b.append("Hi ").append(c.getName()).append(",\n\n");
        b.append(user.getName()).append(" raised an SOS in the Safety App and listed you as a trusted contact.\n\n");
        b.append("Time: ").append(TIME.format(Instant.ofEpochMilli(alert.getSignedTimestamp()))).append(" (IST)\n");
        if (alert.getLatitude() != null && alert.getLongitude() != null) {
            b.append("Location: https://www.google.com/maps?q=")
                    .append(alert.getLatitude()).append(',').append(alert.getLongitude()).append('\n');
        } else {
            b.append("Location: not shared\n");
        }
        if (alert.getDistressConfidence() != null) {
            b.append("Detected by AI: distress sound ").append(Math.round(alert.getDistressConfidence() * 100)).append("%\n");
        } else {
            b.append("Raised manually by pressing the SOS button.\n");
        }
        b.append("Alert ID: ").append(alert.getId()).append("\n\n");
        b.append("Please try to call ").append(user.getName()).append(" right away. ");
        b.append("If you cannot reach them, contact the police on 112 and share the location above.\n\n");
        b.append("This alert is digitally signed (HMAC-SHA256) with a one-time nonce and timestamp, ")
                .append("so it cannot be forged or replayed.\n");
        return b.toString();
    }

    /** a***@gmail.com : enough for the user to recognise it, without exposing the full address. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
