"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useMe } from "@/components/shell";
import { CrisisPanel } from "@/components/crisis";

/** A floating quick-support button, available on every screen: one tap to a
 *  breathing pacer, grounding, or — the point of it — regional crisis helplines. */
export function Sos() {
  const me = useMe();
  const [open, setOpen] = useState(false);
  const [showCrisis, setShowCrisis] = useState(false);
  const wrap = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) { setShowCrisis(false); return; }
    const onKey = (e: KeyboardEvent) => { if (e.key === "Escape") setOpen(false); };
    const onClick = (e: MouseEvent) => { if (!wrap.current?.contains(e.target as Node)) setOpen(false); };
    document.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onClick);
    return () => { document.removeEventListener("keydown", onKey); document.removeEventListener("mousedown", onClick); };
  }, [open]);

  return (
    <div className="sos-wrap" ref={wrap}>
      {open && (
        <div className="sos-panel" role="dialog" aria-label="Quick support">
          <div className="sos-head">Need a moment?</div>
          <Link href="/tools/breathe" className="sos-item" onClick={() => setOpen(false)}>
            <span aria-hidden="true">🫧</span> Breathe
          </Link>
          <Link href="/tools/grounding" className="sos-item" onClick={() => setOpen(false)}>
            <span aria-hidden="true">🌿</span> Ground yourself
          </Link>
          {!showCrisis ? (
            <button className="sos-item crisis" onClick={() => setShowCrisis(true)}>
              <span aria-hidden="true">🆘</span> In crisis? Reach a helpline
            </button>
          ) : (
            <div className="sos-crisis"><CrisisPanel region={me?.crisis_region ?? ""} /></div>
          )}
        </div>
      )}
      <button className="sos-btn" aria-expanded={open} aria-label="Quick support and crisis help"
        onClick={() => setOpen((v) => !v)}>
        {open ? "✕" : "SOS"}
      </button>
    </div>
  );
}
