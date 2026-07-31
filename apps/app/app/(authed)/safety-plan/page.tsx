"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { AppHeader } from "@/components/AppHeader";
import { api, authedFetch } from "@/lib/api";

// A personal safety plan, in the user's own words — the six Stanley-Brown
// sections. The app never interprets, scores or diagnoses what is written here.
//
// Two rules this screen has to keep:
//   • It is written by the user. Nothing is pre-filled by the model.
//   • It must be readable when the network is not. The browser client has no
//     offline caching (sw.js is push-only), so the last saved plan is mirrored
//     to localStorage and shown with an honest "offline copy" note if the fetch
//     fails. The printable page exists for the same reason.
type Plan = {
  id: string;
  version: number;
  warning_signs: string;
  internal_coping: string;
  social_distractors: string;
  social_support: string;
  professionals: string;
  means_safety: string;
  notes: string;
};

type Field = keyof Omit<Plan, "id" | "version">;

const SECTIONS: { field: Field; label: string; hint: string }[] = [
  {
    field: "warning_signs",
    label: "Warning signs I notice",
    hint: "Thoughts, feelings or situations that tell you things are getting harder.",
  },
  {
    field: "internal_coping",
    label: "Things I can do on my own",
    hint: "What has helped before, without needing anyone else.",
  },
  {
    field: "social_distractors",
    label: "People and places that take my mind off it",
    hint: "Company or surroundings that shift the mood — no conversation required.",
  },
  {
    field: "social_support",
    label: "People I can ask for help",
    hint: "Names and numbers, so you don't have to think of them later.",
  },
  {
    field: "professionals",
    label: "Professionals and services I can contact",
    hint: "Your doctor, a counsellor, a helpline you trust.",
  },
  {
    field: "means_safety",
    label: "Making my space safer",
    hint: "Anything you'd want out of reach on a hard night, and who could hold it.",
  },
  { field: "notes", label: "Anything else", hint: "Whatever else you want future-you to read." },
];

const CACHE_KEY = "cbz-safety-plan";
const EMPTY = Object.fromEntries(SECTIONS.map((s) => [s.field, ""])) as Record<Field, string>;

export default function SafetyPlan() {
  const [values, setValues] = useState<Record<Field, string>>(EMPTY);
  const [version, setVersion] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [offline, setOffline] = useState(false);
  const [saving, setSaving] = useState<Field | null>(null);
  const [saved, setSaved] = useState<Field | null>(null);
  const [error, setError] = useState("");

  const cache = useCallback((v: Record<Field, string>, ver: number | null) => {
    try {
      window.localStorage.setItem(CACHE_KEY, JSON.stringify({ v, ver }));
    } catch {
      // A full or disabled store must not break the screen.
    }
  }, []);

  useEffect(() => {
    api<Plan | null>("/safety-plan/me")
      .then((plan) => {
        if (plan) {
          const next = Object.fromEntries(
            SECTIONS.map((s) => [s.field, plan[s.field] ?? ""]),
          ) as Record<Field, string>;
          setValues(next);
          setVersion(plan.version);
          cache(next, plan.version);
        }
        setLoading(false);
      })
      .catch(() => {
        // Fall back to the last copy this device saw. Better a stale plan than
        // no plan at the moment someone opens this screen.
        try {
          const raw = window.localStorage.getItem(CACHE_KEY);
          if (raw) {
            const { v, ver } = JSON.parse(raw);
            setValues({ ...EMPTY, ...v });
            setVersion(ver);
            setOffline(true);
          }
        } catch {
          /* nothing cached */
        }
        setLoading(false);
      });
  }, [cache]);

  // One section at a time: the API merges unset fields, so a save here never
  // blanks anything the user wrote elsewhere.
  async function saveSection(field: Field) {
    setSaving(field);
    setError("");
    try {
      const plan = await api<Plan>("/safety-plan/me", {
        method: "PUT",
        body: JSON.stringify({ [field]: values[field] }),
      });
      setVersion(plan.version);
      cache(values, plan.version);
      setOffline(false);
      setSaved(field);
      setTimeout(() => setSaved(null), 2500);
    } catch {
      setError("Couldn't save that section — it's still here, try again when you're back online.");
    } finally {
      setSaving(null);
    }
  }

  // The printable page is behind auth and the access token lives in memory,
  // not a cookie — a plain link in a new tab would 401. Fetch it with the
  // authed client and hand the browser a blob instead.
  async function openPrintable() {
    try {
      const res = await authedFetch("/safety-plan/me/printable");
      if (!res.ok) throw new Error("unavailable");
      const url = URL.createObjectURL(
        new Blob([await res.text()], { type: "text/html" }),
      );
      window.open(url, "_blank", "noopener");
      // Give the new tab time to load before releasing the object URL.
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch {
      setError("Couldn't open the printable copy — save a section first, or try again.");
    }
  }

  return (
    <>
      <AppHeader eyebrow="Yours, in your words" title="My safety plan" />
      <div className="page-body">
        <p style={{ color: "var(--muted)", maxWidth: 620 }}>
          A plan you write while things are steady, so a harder day has fewer decisions in it.
          Fill in as much or as little as you like — a part-written plan still helps.
        </p>
        <p className="footnote" style={{ maxWidth: 620 }}>
          CereBro doesn&apos;t read this back to you as advice, score it, or share it. If you
          need someone now, <Link href="/support" className="link">support is here</Link>.
        </p>

        {offline && (
          <p className="footnote" role="status">
            Showing the copy saved on this device — we couldn&apos;t reach the server.
          </p>
        )}
        {error && <p className="error">{error}</p>}

        {loading ? (
          <p className="sub">Loading…</p>
        ) : (
          <>
            {SECTIONS.map((s, i) => (
              <section key={s.field} className={`card cz-in cz-d${Math.min(i + 1, 6)}`} style={{ marginTop: 14 }}>
                <h2 style={{ fontSize: 17 }}>{s.label}</h2>
                <p className="sub" style={{ maxWidth: 560 }}>{s.hint}</p>
                <textarea
                  value={values[s.field]}
                  onChange={(e) => setValues({ ...values, [s.field]: e.target.value })}
                  aria-label={s.label}
                  rows={3}
                  style={{ width: "100%", marginTop: 8 }}
                />
                <div style={{ display: "flex", gap: 12, alignItems: "center", marginTop: 8 }}>
                  <button
                    className="btn"
                    onClick={() => saveSection(s.field)}
                    disabled={saving === s.field}
                  >
                    {saving === s.field ? "Saving…" : "Save"}
                  </button>
                  {saved === s.field && (
                    <span className="meta" role="status">Saved.</span>
                  )}
                </div>
              </section>
            ))}

            <section className="card cz-in" style={{ marginTop: 14 }}>
              <h2 style={{ fontSize: 17 }}>Keep a copy</h2>
              <p className="sub" style={{ maxWidth: 560 }}>
                Opens a plain, printable page — print it, or save it as a PDF, so it&apos;s
                there when a screen isn&apos;t.
              </p>
              <button className="btn" onClick={openPrintable} style={{ marginTop: 10 }}>
                Open printable plan
              </button>
              {version !== null && (
                <p className="meta" style={{ marginTop: 10 }}>Version {version}</p>
              )}
            </section>
          </>
        )}
      </div>
    </>
  );
}
