"use client";

// The top bar shared by every authed screen: eyebrow + serif title on the left,
// a "Search calm…" field and a notification bell on the right (matches ref).
import type { ReactNode } from "react";
import { Icon } from "@/components/icons";

export function AppHeader({
  eyebrow, title, right,
}: { eyebrow: string; title: string; right?: ReactNode }) {
  return (
    <header className="app-header">
      <div className="app-header-title">
        <p className="eyebrow">{eyebrow}</p>
        <h1 className="page-title">{title}</h1>
      </div>
      <div className="app-header-tools">
        {right}
        <label className="search-field">
          <Icon.search size={17} />
          <input type="search" placeholder="Search calm…" aria-label="Search" />
        </label>
        <button className="bell-btn" aria-label="Notifications" title="Notifications">
          <Icon.bellDot size={18} />
        </button>
      </div>
    </header>
  );
}
