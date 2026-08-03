"use client";

// The top bar shared by every authed screen: eyebrow + serif title, plus an
// optional per-page action slot.
//
// It used to carry a "Search calm…" field and a notification bell. Neither had a
// handler — web has no search backend and no in-app notifications — so they were
// focusable dead ends with aria-labels promising features that don't exist. They
// are gone until they're real; the Support door lives in the nav, always visible.
import type { ReactNode } from "react";

export function AppHeader({
  eyebrow, title, right,
}: { eyebrow: string; title: string; right?: ReactNode }) {
  return (
    <header className="app-header">
      <div className="app-header-title">
        <p className="eyebrow">{eyebrow}</p>
        <h1 className="page-title">{title}</h1>
      </div>
      {right && <div className="app-header-tools">{right}</div>}
    </header>
  );
}
