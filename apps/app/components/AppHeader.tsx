"use client";

// The top bar shared by every authed screen: eyebrow + serif title, plus an
// optional per-page action slot.
//
// It used to carry a "Search calm…" field and a notification bell. Neither had a
// handler — web has no search backend and no in-app notifications — so they were
// focusable dead ends with aria-labels promising features that don't exist. They
// are gone until they're real; the Support door lives in the nav, always visible.
import { useEffect, useRef, type ReactNode } from "react";

export function AppHeader({
  eyebrow, title, right,
}: { eyebrow: string; title: string; right?: ReactNode }) {
  const h1 = useRef<HTMLHeadingElement>(null);
  // Client-side navigation moves the page but not the reader: without this, a
  // screen-reader user who activates "Sleep" keeps hearing the old page. Focus
  // lands on the new h1 (tabIndex -1 = programmatic-only, never in tab order).
  // Skipped on first document load so the skip-link stays first in tab order.
  useEffect(() => {
    if ((window as any).__cbNavigated) h1.current?.focus({ preventScroll: true });
    (window as any).__cbNavigated = true;
  }, [title]);
  return (
    <header className="app-header">
      <div className="app-header-title">
        <p className="eyebrow">{eyebrow}</p>
        <h1 className="page-title" tabIndex={-1} ref={h1} style={{ outline: "none" }}>{title}</h1>
      </div>
      {right && <div className="app-header-tools">{right}</div>}
    </header>
  );
}
