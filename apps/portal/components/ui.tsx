import type { CSSProperties, ReactNode } from "react";
import { PRIVACY_WALL_NOTICE_BODY, PRIVACY_WALL_NOTICE_TITLE } from "@/lib/copy";

/**
 * Presentational primitives shared by the portal routes. Everything here is a
 * server component: this surface has no data layer, so nothing needs state
 * except the two builder pages that own their own client components.
 */

export function PageIntro({
  eyebrow, title, lede,
}: { eyebrow: string; title: string; lede: string }) {
  return (
    <>
      <div className="eyebrow">{eyebrow}</div>
      <h1>{title}</h1>
      <p className="lede">{lede}</p>
    </>
  );
}

/**
 * The reusable privacy-wall notice. Its wording is fixed in lib/copy.ts and
 * repeated on every reporting surface — an administrator should never have to
 * remember which page the rule was stated on.
 */
export function PrivacyWall() {
  return (
    <div className="notice">
      <span aria-hidden="true">⛨</span>
      <div>
        <b>{PRIVACY_WALL_NOTICE_TITLE}</b>
        <br />
        {PRIVACY_WALL_NOTICE_BODY}
      </div>
    </div>
  );
}

export function Metric({
  value, label, delta, warn,
}: { value: string; label: string; delta?: string; warn?: boolean }) {
  return (
    <div className="card metric">
      <strong>{value}</strong>
      <small>{label}</small>
      {delta ? <span className={warn ? "delta warn" : "delta"}>{delta}</span> : null}
    </div>
  );
}

export function Badge({
  tone = "", children,
}: { tone?: "" | "good" | "warn" | "danger" | "info"; children: ReactNode }) {
  return <span className={tone ? `badge ${tone}` : "badge"}>{children}</span>;
}

/**
 * A proportion bar. `label` is required, not optional: a bare bar with no
 * accessible name is unreadable to anyone not looking at it.
 */
export function Progress({ value, label }: { value: number; label: string }) {
  return (
    <div className="progress" role="img" aria-label={label}>
      <span style={{ "--value": `${value}%` } as CSSProperties} />
    </div>
  );
}

/**
 * Vertical bars. The whole plot carries one aria-label describing the shape
 * and the peak, which is what a screen-reader user needs — 8 individually
 * announced bars are noise.
 */
export function BarChart({
  bars, label,
}: { bars: { week: string; height: number }[]; label: string }) {
  return (
    <div className="chart" role="img" aria-label={label}>
      {bars.map((b) => (
        <div
          key={b.week}
          className="bar"
          data-label={b.week}
          style={{ "--h": `${b.height}%` } as CSSProperties}
        />
      ))}
    </div>
  );
}

export function Notice({
  tone = "", icon, children,
}: { tone?: "" | "warn" | "danger" | "info"; icon: string; children: ReactNode }) {
  return (
    <div className={tone ? `notice ${tone}` : "notice"}>
      <span aria-hidden="true">{icon}</span>
      <div>{children}</div>
    </div>
  );
}

export function Spacer() {
  return <div className="spacer" />;
}
