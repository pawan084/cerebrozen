"use client";

import { logout } from "@/lib/api";
import { useMe } from "@/components/shell";

export default function SettingsPage() {
  const me = useMe();
  async function signOut() {
    await logout();
    window.location.href = "/";
  }
  return (
    <div className="page">
      <div className="page-head"><div><div className="eyebrow">Settings</div><h1>Settings</h1></div></div>
      <div className="empty">
        <div className="card">
          <h3>Account</h3>
          <p className="placeholder" style={{ marginBottom: 4 }}>{me?.name || "—"}</p>
          <p className="placeholder">{me?.email}</p>
          <div style={{ marginTop: 18 }}>
            <button className="primary" onClick={signOut}>Sign out</button>
          </div>
        </div>
        <div className="card" style={{ marginTop: 16 }}>
          <h3>Privacy &amp; consent</h3>
          <p className="placeholder">
            Data-sharing controls (mood, journal, voice) land here — each off until you turn it on.
            Your conversations are always private to you.
          </p>
        </div>
      </div>
    </div>
  );
}
