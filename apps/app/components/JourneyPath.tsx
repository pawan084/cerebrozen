"use client";

import { useState } from "react";

export type DayGuide = { title: string; body: string };

/**
 * Where a day sits relative to the day the enrollment is on.
 *
 * Deliberately NOT "done / locked", and the Android port says the same thing
 * (ui/screens/JourneyPath.kt). An enrollment counts days from its start date and
 * records nothing per day, so the app does not know whether anyone actually did
 * Tuesday — only that Tuesday has been and gone. "passed" says exactly that much;
 * calling it "completed" would be congratulating a user for the passage of time.
 *
 * And nothing is ever locked. A program here is a suggested order, not a
 * syllabus: every day opens, in any order, on any day. Withholding a coping
 * practice until someone has earned it is the opposite of what this is for — the
 * person who needs Friday's wind-down tonight is often exactly the person who has
 * not opened Monday.
 */
export type DayState = "passed" | "today" | "ahead";

/** Pure: where day `day` (1-based) sits when the enrollment is on `currentDay`. */
export function dayState(day: number, currentDay: number): DayState {
  if (day < currentDay) return "passed";
  if (day === currentDay) return "today";
  return "ahead";
}

/**
 * The serpentine offset of a node, as a -1..1 horizontal bias.
 *
 * A four-phase cycle (centre → right → centre → left) rather than a plain
 * left/right alternation, so the connecting line reads as one continuous meander
 * instead of a zig-zag. Same sequence as the Android path, so the two clients
 * draw the same shape.
 */
export function nodeBias(index: number): number {
  return [0, 0.62, 0, -0.62][index % 4];
}

/** "Day 3 · today" / "Day 1 · earlier" / "Day 5 · coming up". */
function dayLabel(day: number, currentDay: number): string {
  const state = dayState(day, currentDay);
  if (state === "today") return `Day ${day} · today`;
  if (state === "passed") return `Day ${day} · earlier`;
  return `Day ${day} · coming up`;
}

const STEP = 74; // px between nodes — tuned so a seven-day week fits one view
const NODE = 50;

/**
 * A program's days as a winding path — the whole week visible at once.
 *
 * Every node opens that day's guide. There is no lock, no streak, no score: see
 * `DayState` for why. The one thing the path asserts is where today falls, which
 * is the one thing the data actually knows.
 */
export function JourneyPath({
  guides,
  currentDay,
}: {
  guides: DayGuide[];
  currentDay: number;
}) {
  const clamped = Math.min(Math.max(currentDay, 1), guides.length);
  const [selected, setSelected] = useState(clamped);
  if (guides.length === 0) return null;

  const height = STEP * guides.length;
  // Node centres in a 0..100 viewBox so the SVG scales with the card.
  const cx = (i: number) => 50 + nodeBias(i) * 34;
  const cy = (i: number) => (i * STEP + STEP / 2) * (100 / height);

  const d = guides
    .map((_, i) =>
      i === 0
        ? `M ${cx(0)} ${cy(0)}`
        : `Q ${cx(i - 1)} ${(cy(i - 1) + cy(i)) / 2} ${cx(i)} ${cy(i)}`,
    )
    .join(" ");

  const guide = guides[Math.min(selected, guides.length) - 1];

  return (
    <div>
      <div style={{ position: "relative", height }}>
        <svg
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          aria-hidden="true"
          style={{ position: "absolute", inset: 0, width: "100%", height: "100%" }}
        >
          <path d={d} fill="none" stroke="var(--line)" strokeWidth="0.6" strokeLinecap="round" />
        </svg>
        {guides.map((_, i) => {
          const day = i + 1;
          const state = dayState(day, currentDay);
          const isSelected = day === selected;
          return (
            <button
              key={day}
              onClick={() => setSelected(day)}
              aria-label={
                state === "today"
                  ? `Day ${day}, today`
                  : state === "passed"
                    ? `Day ${day}, earlier in this program`
                    : `Day ${day}, later in this program`
              }
              aria-current={state === "today" ? "step" : undefined}
              style={{
                position: "absolute",
                top: i * STEP + (STEP - NODE) / 2,
                left: `calc(50% + ${nodeBias(i) * 34}% - ${NODE / 2}px)`,
                width: NODE,
                height: NODE,
                borderRadius: "50%",
                cursor: "pointer",
                font: "inherit",
                fontWeight: 700,
                // Today is the only filled node — the one fact the enrollment
                // actually knows. Days gone by are outlined, never ticked.
                background:
                  state === "today" ? "var(--lav)" : isSelected ? "var(--card)" : "transparent",
                color: state === "today" ? "var(--night)" : "var(--text)",
                border: `2px solid ${isSelected || state === "today" ? "var(--lav)" : "var(--line)"}`,
                opacity: state === "ahead" && !isSelected ? 0.7 : 1,
              }}
            >
              {day}
            </button>
          );
        })}
      </div>

      {/* The selected day's guide. Always present, so a tap never leads nowhere. */}
      <div
        style={{
          marginTop: 10,
          padding: 16,
          borderRadius: 18,
          background: "var(--card)",
          border: "1px solid var(--line)",
        }}
      >
        <p className="eyebrow" style={{ marginBottom: 4 }}>
          {dayLabel(selected, currentDay)}
        </p>
        {guide.title.trim() && <h4 style={{ margin: "0 0 4px", fontSize: 14 }}>{guide.title}</h4>}
        {guide.body.trim() && (
          <p style={{ color: "var(--muted)", fontSize: 13, margin: 0 }}>{guide.body}</p>
        )}
      </div>
    </div>
  );
}
