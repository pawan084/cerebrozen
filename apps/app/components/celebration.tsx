"use client";

import { useEffect, useState } from "react";

/** Renders a brief celebration flourish when `celebrate()` fires. Confetti is
 *  suppressed under reduced motion (the check + message still show). */
export function Celebration() {
  const [show, setShow] = useState(false);
  const [msg, setMsg] = useState("Nice.");
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    setReduced(window.matchMedia("(prefers-reduced-motion: reduce)").matches);
    let timer: ReturnType<typeof setTimeout>;
    const on = (e: Event) => {
      setMsg((e as CustomEvent).detail || "Nice.");
      setShow(true);
      clearTimeout(timer);
      timer = setTimeout(() => setShow(false), 1600);
    };
    window.addEventListener("cbz:celebrate", on);
    return () => { window.removeEventListener("cbz:celebrate", on); clearTimeout(timer); };
  }, []);

  if (!show) return null;
  return (
    <div className="celebrate" role="status" aria-live="polite">
      <div className="celebrate-inner">
        <div className="celebrate-badge">
          <span className="celebrate-check" aria-hidden="true">✓</span>
          {!reduced && Array.from({ length: 12 }).map((_, i) => (
            <span key={i} className="confetti" style={{ ["--i" as string]: i }} />
          ))}
        </div>
        <span className="celebrate-msg">{msg}</span>
      </div>
    </div>
  );
}
