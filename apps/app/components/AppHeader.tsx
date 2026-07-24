"use client";

// The top bar shared by every authed screen: eyebrow + serif title on the left,
// an optional per-page action slot on the right. (A search field and a
// notification bell used to render here, but neither had a backend — dead,
// focusable-but-inert chrome fails the credibility bar and keyboard a11y.)
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
