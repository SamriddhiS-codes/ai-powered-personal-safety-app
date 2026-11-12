# WhatsApp alerts to trusted contacts

When an SOS is raised, the backend sends a WhatsApp message to every trusted contact that has a phone number
(in addition to the email). It uses Twilio's WhatsApp API. The free sandbox is enough for a demo.
Without the settings below the app still works and simply skips WhatsApp (the manual WhatsApp button stays).

1. Create a free Twilio account at twilio.com.
2. In the Twilio console open Messaging, Try it out, Send a WhatsApp message. It shows the sandbox number
   (usually +1 415 523 8886) and a join code such as `join word-word`.
3. Every contact who should receive alerts sends that join message from their own WhatsApp to the sandbox number.
   Do this on the day of the demo: WhatsApp only lets a business message someone freely within 24 hours of their
   last message to it, and sandbox sign-ups expire after a few days.
4. Copy the Account SID and Auth Token from the Twilio console dashboard and set them in the terminal that starts the backend:

```powershell
$env:TWILIO_ACCOUNT_SID = "ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
$env:TWILIO_AUTH_TOKEN = "your_auth_token"
```

   `TWILIO_WHATSAPP_FROM` is optional and defaults to the sandbox number (`whatsapp:+14155238886`).
5. Save contacts with a country code (`+91 98765 43210`). A bare 10-digit number is assumed to be Indian (+91).
6. Start the backend, trigger an SOS, and look for "WhatsApp sent to ..." on the SOS screen.

Never commit the token. It is read from the environment on purpose.
For a real product, use Meta's WhatsApp Business Cloud API with approved message templates instead of the sandbox.

## Newer sandbox accounts: template messages (error 21654 "ContentSid Required")

Some Twilio accounts only allow template messages in the sandbox. Twilio's "Try out WhatsApp" page, tab
"API", shows a `ContentSid=HX...` in its sample request. Give that value to the backend:

```powershell
$env:TWILIO_CONTENT_SID = "HXfe5ab5f00277942d4d4200328b4d403c"   # the ContentSid from your Twilio page
```

The sample "Appointment Reminders" template has fixed wording and no blanks, so the contact receives that fixed text
(this proves the automatic WhatsApp works, but it does not carry the SOS details).

For a real SOS wording, create your own template: Twilio console, Messaging, Content Template Builder, type "Text",
language English, body:

```
SOS from the Safety App: {{1}} may be in danger. Time: {{2}}. Location: {{3}}. Please call them now or call 112.
```

Use its `HX...` id as TWILIO_CONTENT_SID and also set `$env:TWILIO_CONTENT_VARS = "3"` so the name, time and map link
fill {{1}}, {{2}} and {{3}}. WhatsApp may need to approve a new template first, which can take from minutes to a day.
