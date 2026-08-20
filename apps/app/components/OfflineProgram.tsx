"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppHeader } from "./AppHeader";

// The web half of Android's `OfflineProgramScreen` (ui/offline/).
//
// These are EDUCATIONAL overviews, not a clinician-led course, and the copy is
// the same text the Android strings carry word for word — the two clients
// saying different things about what CBT-I or MBCT is would be the failure
// mode worth avoiding here.
//
// "Offline" is literal: no fetch, no session, nothing to fail. Progress is a
// tick per module kept in localStorage on this device, because a reading list
// that forgets where you were is one people stop opening.

export type OfflineModule = { title: string; body: string; practice: string };

export function OfflineProgram({
  id,
  eyebrow,
  title,
  subtitle,
  modules,
}: {
  id: string;
  eyebrow: string;
  title: string;
  subtitle: string;
  modules: OfflineModule[];
}) {
  const key = `cerebro_app_offline_${id}`;
  const [done, setDone] = useState<Set<number>>(new Set());
  // Hydration: reading localStorage during render would make the server HTML
  // and the first client render disagree, so it lands in an effect.
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    try {
      const raw = window.localStorage.getItem(key);
      if (raw) setDone(new Set(JSON.parse(raw) as number[]));
    } catch {
      // Unreadable or blocked storage just means starting from zero ticks.
    }
    setLoaded(true);
  }, [key]);

  function toggle(i: number) {
    setDone((prev) => {
      const next = new Set(prev);
      if (next.has(i)) next.delete(i);
      else next.add(i);
      try {
        window.localStorage.setItem(key, JSON.stringify([...next]));
      } catch {
        // The tick still shows for this visit; it just will not survive.
      }
      return next;
    });
  }

  return (
    <>
      <AppHeader eyebrow={eyebrow} title={title} />
      <div className="today-wrap">
        <p className="sub today-lede">{subtitle}</p>
        {loaded && done.size > 0 && (
          <p className="tiny" role="status">
            {done.size} of {modules.length} marked read — kept on this device only.
          </p>
        )}

        <ol className="offline-modules">
          {modules.map((m, i) => (
            <li key={m.title} className="ds-card">
              <div className="ds-head">
                <h2 className="serif-h">{m.title}</h2>
                <button
                  type="button"
                  className="chip"
                  aria-pressed={done.has(i)}
                  onClick={() => toggle(i)}
                >
                  {done.has(i) ? "Read" : "Mark read"}
                </button>
              </div>
              <p className="sub">{m.body}</p>
              <p className="tiny">
                <b>Try this:</b> {m.practice}
              </p>
            </li>
          ))}
        </ol>

        <section className="ds-card">
          <p className="sub">
            This is reading, not treatment, and it does not know anything about you. If sleep
            or mood has been hard for a while, that deserves a person — start with{" "}
            <Link href="/support" className="link">
              support
            </Link>
            .
          </p>
        </section>
      </div>
    </>
  );
}
