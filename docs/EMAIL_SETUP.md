# Emailing trusted contacts

When an SOS is raised, every trusted contact that has an email address is emailed automatically
(location link, time, alert ID). Without the settings below the app still works and simply skips the emails.

1. Use a Gmail account for sending (a separate one for the project is best).
2. Turn on 2-Step Verification for it, then create an app password:
   Google Account, Security, 2-Step Verification, App passwords. You get 16 letters.
3. Set two environment variables in the terminal that starts the backend:

```powershell
$env:MAIL_USERNAME = "your.sender@gmail.com"
$env:MAIL_PASSWORD = "abcdefghijklmnop"   # the app password, without spaces
```

4. Start the backend. Add a trusted contact with an email, trigger an SOS, and check the contact's inbox (and spam).

Never commit the password. It is read from the environment on purpose.
