// Builds one-tap message links for a trusted contact (no SMS gateway needed: the person's own
// WhatsApp, Messages app or mail app sends it).

const mapsLink = (lat, lon) => `https://www.google.com/maps?q=${lat},${lon}`;

export function sosText(alert) {
  const where =
    alert && alert.latitude != null
      ? `My location: ${mapsLink(alert.latitude, alert.longitude)}`
      : "My location could not be shared.";
  const when = alert ? new Date(alert.signedTimestamp).toLocaleString() : new Date().toLocaleString();
  return `SOS from the Safety App: I may be in danger and need help. ${where} (Alert raised ${when}.) Please call me or contact the police on 112.`;
}

// WhatsApp wants digits only with the country code. A bare 10-digit number is assumed to be Indian (+91).
function waNumber(phone) {
  const d = String(phone).replace(/\D/g, "");
  return d.length === 10 ? "91" + d : d;
}

export function linksFor(contact, alert) {
  const text = sosText(alert);
  const out = [];
  if (contact.phone) {
    out.push({ key: "wa", label: "WhatsApp", href: `https://wa.me/${waNumber(contact.phone)}?text=${encodeURIComponent(text)}` });
    out.push({ key: "sms", label: "SMS", href: `sms:${contact.phone}?body=${encodeURIComponent(text)}` });
    out.push({ key: "call", label: "Call", href: `tel:${contact.phone}` });
  }
  if (contact.email) {
    out.push({
      key: "mail",
      label: "Email",
      href: `mailto:${contact.email}?subject=${encodeURIComponent("SOS: I need help")}&body=${encodeURIComponent(text)}`,
    });
  }
  return out;
}
