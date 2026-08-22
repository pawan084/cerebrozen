import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { JourneyPath, dayState, nodeBias } from "../../apps/app/components/JourneyPath";

afterEach(cleanup);

const GUIDES = [
  { title: "Set your wake time", body: "One hour, every day." },
  { title: "Wind down", body: "Dim the lights." },
  { title: "The bad night", body: "Get up anyway." },
  { title: "Caffeine", body: "Nothing after noon." },
  { title: "The nap question", body: "Short or not at all." },
];

function path(currentDay = 3) {
  // The number of days IS guides.length — there is no separate `days` prop.
  return render(<JourneyPath currentDay={currentDay} guides={GUIDES} />);
}

describe("where a day sits", () => {
  // Deliberately NOT "done / locked". An enrollment counts days from its start
  // date and records nothing per day, so the app does not know whether anyone
  // actually did Tuesday — only that Tuesday has been and gone.
  it("calls a day gone by 'passed', which is all the data supports", () => {
    expect(dayState(1, 3)).toBe("passed");
    expect(dayState(2, 3)).toBe("passed");
  });

  it("knows which day is today", () => {
    expect(dayState(3, 3)).toBe("today");
  });

  it("calls a later day 'ahead', not 'locked'", () => {
    expect(dayState(4, 3)).toBe("ahead");
    expect(dayState(99, 3)).toBe("ahead");
  });

  it("has no state that means 'completed'", () => {
    // Calling it completed would be congratulating someone for the passage of
    // time. The type has three members and none of them is an achievement.
    const states = new Set([1, 2, 3, 4, 5].map((d) => dayState(d, 3)));
    expect([...states].sort()).toEqual(["ahead", "passed", "today"]);
  });

  it("works on day one, before anything has passed", () => {
    expect(dayState(1, 1)).toBe("today");
    expect(dayState(2, 1)).toBe("ahead");
  });
});

describe("nothing is ever locked", () => {
  // "A program here is a suggested order, not a syllabus: every day opens, in
  // any order, on any day. Withholding a coping practice until someone has
  // earned it is the opposite of what this is for — the person who needs
  // Friday's wind-down tonight is often exactly the person who has not opened
  // Monday."
  it("gives every day a live control, including the ones ahead", () => {
    path(1);
    const nodes = screen.getAllByRole("button");
    expect(nodes).toHaveLength(5);
    for (const node of nodes) {
      expect((node as HTMLButtonElement).disabled, `${node.textContent} is disabled`).toBe(false);
    }
  });

  it("opens a future day's guide on the first day of the programme", async () => {
    const user = await Promise.resolve(userEvent.setup());
    path(1);
    await user.click(screen.getByRole("button", { name: /Day 5/ }));
    expect(screen.getByText("The nap question")).toBeTruthy();
    expect(screen.getByText("Short or not at all.")).toBeTruthy();
  });

  it("opens a day already gone", async () => {
    const user = userEvent.setup();
    path(5);
    await user.click(screen.getByRole("button", { name: /Day 1/ }));
    expect(screen.getByText("Set your wake time")).toBeTruthy();
  });

  it("shows no lock, tick or score anywhere", async () => {
    path(3);
    const text = document.body.textContent ?? "";
    expect(text).not.toMatch(/locked|complete|done|✓|streak|score/i);
  });
});

describe("what a day is called", () => {
  it("describes position, never achievement", async () => {
    const user = userEvent.setup();
    path(3);
    await user.click(screen.getByRole("button", { name: /Day 1/ }));
    expect(screen.getByText("Day 1 · earlier")).toBeTruthy();
  });

  it("names today as today", () => {
    path(3);
    expect(screen.getByText("Day 3 · today")).toBeTruthy();
  });

  it("calls a later day 'coming up'", async () => {
    const user = userEvent.setup();
    path(3);
    await user.click(screen.getByRole("button", { name: /Day 5/ }));
    expect(screen.getByText("Day 5 · coming up")).toBeTruthy();
  });

  it("tells a screen reader where a day sits, in the same words", () => {
    path(3);
    expect(screen.getByRole("button", { name: "Day 3, today" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Day 1, earlier in this program" })).toBeTruthy();
    expect(screen.getByRole("button", { name: "Day 5, later in this program" })).toBeTruthy();
  });

  it("marks only today as the current step", () => {
    // aria-current="step" on more than one node would make the path ambiguous
    // to anyone navigating it without sight.
    path(3);
    const current = screen
      .getAllByRole("button")
      .filter((b) => b.getAttribute("aria-current") === "step");
    expect(current).toHaveLength(1);
    expect(current[0].textContent).toBe("3");
  });
});

describe("a tap never leads nowhere", () => {
  it("shows today's guide before anything is tapped", () => {
    path(3);
    expect(screen.getByText("The bad night")).toBeTruthy();
  });

  it("still renders the panel when a day has no guide text", () => {
    // A day whose title and body are blank must not blank the panel — the
    // label is still information, and a tap that appears to do nothing reads
    // as a broken control.
    const sparse = [...GUIDES.slice(0, 2), { title: "", body: "" }];
    render(<JourneyPath currentDay={3} guides={sparse} />);
    expect(screen.getByText("Day 3 · today")).toBeTruthy();
    expect(screen.getAllByRole("button")).toHaveLength(3);
  });
});

describe("the serpentine geometry is Android's", () => {
  it("cycles centre, right, centre, left", () => {
    expect([0, 1, 2, 3].map(nodeBias)).toEqual([0, 0.62, 0, -0.62]);
  });

  it("repeats every four nodes", () => {
    for (let i = 0; i < 12; i++) expect(nodeBias(i)).toBe(nodeBias(i % 4));
  });

  it("matches the Kotlin the Android path draws from", () => {
    // Both clients derive their nodes AND their connecting line from this, so a
    // drift here is two products drawing different shapes for the same week.
    const kotlin = readFileSync(
      resolve(__dirname, "../../apps/android/app/src/main/java/com/cerebrozen/app/ui/screens/JourneyPath.kt"),
      "utf8",
    );
    const fn = kotlin.slice(kotlin.indexOf("internal fun nodeBias"));
    expect(fn).toMatch(/0\s*->\s*0f/);
    expect(fn).toMatch(/1\s*->\s*0\.62f/);
    expect(fn).toMatch(/2\s*->\s*0f/);
    expect(fn).toMatch(/else\s*->\s*-0\.62f/);
  });
});
