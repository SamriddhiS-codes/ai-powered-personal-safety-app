import { useEffect, useState } from "react";
import * as api from "./api";
import "./Contacts.css";

const initials = (n) =>
  n
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w[0].toUpperCase())
    .join("");

export default function ContactsScreen() {
  const [list, setList] = useState(null);
  const [err, setErr] = useState("");
  const [form, setForm] = useState({ name: "", phone: "", email: "" });
  const [saving, setSaving] = useState(false);

  const load = () =>
    api
      .listContacts()
      .then((l) => {
        setList(l);
        setErr("");
      })
      .catch((e) => {
        setList([]);
        setErr(e.message);
      });

  useEffect(() => {
    load();
  }, []);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function add(e) {
    e.preventDefault();
    setSaving(true);
    setErr("");
    try {
      await api.addContact(form.name, form.phone, form.email);
      setForm({ name: "", phone: "", email: "" });
      await load();
    } catch (e2) {
      // The backend answers with its own validation message after the status code.
      setErr(e2.message.replace(/^\d+\s*/, ""));
    } finally {
      setSaving(false);
    }
  }

  async function remove(id) {
    try {
      await api.deleteContact(id);
      await load();
    } catch (e) {
      setErr("Could not remove: " + e.message);
    }
  }

  return (
    <div className="contacts">
      <div>
        <h2>Trusted contacts</h2>
        <p className="muted">
          People you trust. When an SOS is raised, you can alert them in one tap, with your live location and the time.
        </p>
      </div>

      {list && list.length === 0 && !err && (
        <div className="empty">
          <span>👥</span>
          <b>No trusted contacts yet</b>
          <small>Add at least one so the SOS screen can alert them.</small>
        </div>
      )}

      <ul className="clist">
        {(list || []).map((c) => (
          <li key={c.id}>
            <span className="av">{initials(c.name)}</span>
            <div className="who">
              <strong>{c.name}</strong>
              <small>{[c.phone, c.email].filter(Boolean).join(" · ")}</small>
            </div>
            <button className="x" aria-label={`Remove ${c.name}`} onClick={() => remove(c.id)}>
              ✕
            </button>
          </li>
        ))}
      </ul>

      <form className="cform card" onSubmit={add}>
        <b>Add a contact</b>
        <input placeholder="Name" value={form.name} onChange={set("name")} required maxLength={100} />
        <input placeholder="Phone (e.g. +91 98765 43210)" value={form.phone} onChange={set("phone")} inputMode="tel" />
        <input placeholder="Email (optional)" value={form.email} onChange={set("email")} inputMode="email" />
        <button className="btn solid" disabled={saving}>
          {saving ? "Saving…" : "Save contact"}
        </button>
        <small className="muted">Up to 5 contacts. A phone number or an email is needed.</small>
      </form>

      {err && <p className="note">{err}</p>}
    </div>
  );
}
