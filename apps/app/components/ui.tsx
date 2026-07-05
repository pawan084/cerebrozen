// Reusable UI for the CereBro web app — the shared vocabulary the screens are
// built from (page header, hero card, panels, rows, chips, week dots). Styling
// lives in globals.css; these components just apply the classes consistently.

import Link from "next/link";
import type { ReactNode } from "react";

/** Eyebrow + serif title header used at the top of every screen. */
export function PageHeader({
  eyebrow, title, trailing,
}: { eyebrow: string; title: string; trailing?: ReactNode }) {
  return (
    <header className="page-head">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1 className="page-title">{title}</h1>
      </div>
      {trailing && <div className="page-head-trailing">{trailing}</div>}
    </header>
  );
}

/** Full-bleed hero: gradient panel, tag pill, serif headline, optional CTA. */
export function HeroCard({
  tag, title, subtitle, cta, onCta, accent = "calm", children,
}: {
  tag: string; title: string; subtitle?: string; cta?: string;
  onCta?: () => void; accent?: "calm" | "sleep" | "warm"; children?: ReactNode;
}) {
  return (
    <section className={`hero-card accent-${accent}`}>
      <span className="hero-tag">{tag}</span>
      <h2 className="hero-title">{title}</h2>
      {subtitle && <p className="hero-sub">{subtitle}</p>}
      {children}
      {cta && (
        <button className="hero-cta" onClick={onCta}>
          <span className="hero-cta-play">▶</span> {cta}
        </button>
      )}
    </section>
  );
}

/** A soft glass panel. */
export function Panel({
  children, className = "", ...rest
}: { children: ReactNode; className?: string } & React.HTMLAttributes<HTMLDivElement>) {
  return <section className={`panel ${className}`} {...rest}>{children}</section>;
}

/** Section heading (serif) with optional trailing text/action. */
export function SectionTitle({ title, trailing }: { title: string; trailing?: ReactNode }) {
  return (
    <div className="section-title">
      <h3>{title}</h3>
      {trailing && <span className="section-trailing">{trailing}</span>}
    </div>
  );
}

/** A tappable / link row with an icon well, title, subtitle and chevron. */
export function Row({
  icon, title, subtitle, href, onClick, emphasis, trailing,
}: {
  icon?: ReactNode; title: string; subtitle?: string; href?: string;
  onClick?: () => void; emphasis?: boolean; trailing?: ReactNode;
}) {
  const inner = (
    <>
      {icon && <span className="ui-row-icon">{icon}</span>}
      <span className="ui-row-body">
        <strong>{title}</strong>
        {subtitle && <small>{subtitle}</small>}
      </span>
      {trailing ?? (href || onClick ? <span className="ui-row-chevron">›</span> : null)}
    </>
  );
  const cls = emphasis ? "ui-row emphasis" : "ui-row";
  if (href) return <Link href={href} className={cls}>{inner}</Link>;
  if (onClick) return <button className={cls} onClick={onClick}>{inner}</button>;
  return <div className={`${cls} static`}>{inner}</div>;
}

/** Selectable pill. */
export function Chip({
  label, active, onClick,
}: { label: string; active?: boolean; onClick?: () => void }) {
  return (
    <button className={active ? "ui-chip active" : "ui-chip"} onClick={onClick} aria-pressed={active}>
      {label}
    </button>
  );
}

/** The seven-day activity ring used by streak / sleep summaries. */
export function WeekDots({
  week,
}: { week: { date: string; active: boolean }[] }) {
  const letters = ["S", "M", "T", "W", "T", "F", "S"];
  return (
    <div className="week-dots" aria-hidden="true">
      {week.map((d, i) => (
        <div key={d.date} className="week-dot">
          <span className={d.active ? "dot on" : "dot"} />
          <em>{letters[new Date(d.date).getDay()] ?? letters[i % 7]}</em>
        </div>
      ))}
    </div>
  );
}
