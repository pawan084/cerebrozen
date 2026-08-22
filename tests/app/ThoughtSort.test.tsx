import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { act, cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { ThoughtSort } from "../../apps/app/components/ThoughtSort";

afterEach(cleanup);

/**
 * The classification of each example, duplicated here on purpose.
 *
 * This is a mental-health product deciding, on screen, whether a sentence is
 * helping the person thinking it. A flipped entry would teach the opposite of
 * what it means to — "I'm worthless" praised as helpful is not a cosmetic bug —
 * and nothing else in the suite would notice, because the component reports
 * only a count. If the table below stops matching the component, that is the
 * signal.
 */
const HELPFUL: Record<string, boolean> = {
  "I made a mistake, but I can learn from it": true,
  "I always mess everything up": false,
  "This is hard, but I can handle it": true,
  "I'm worthless": false,
  "I'll try my best, and that's enough": true,
  "Nobody likes me": false,
  "I can ask for help when I need it": true,
  "I should be good at this by now": false,
  "It's okay to feel uncomfortable sometimes": true,
  "If this goes wrong, everything falls apart": false,
  "I've got through hard things before": true,
  "Everyone else has it figured out": false,
};

/** The thought currently on screen, without its typographic quotes. */
function shownThought(): string {
  const quote = document.querySelector("blockquote p")!;
  return quote.textContent!.replace(/[“”]/g, "").trim();
}

async function startSorting() {
  const user = userEvent.setup();
  render(<ThoughtSort />);
  await user.click(screen.getByRole("button", { name: "Start sorting" }));
  return user;
}

describe("what it refuses to claim", () => {
  // The reference build scores "Thought awareness: 87%" and congratulates
  // "Perfect cognitive awareness!". A ten-item quiz over pre-written sentences
  // measures no such faculty, and unevidenced cognitive-training claims are the
  // exact class the FTC acted on in the 2016 Lumosity settlement.
  it("reports what happened and nothing more", async () => {
    const user = await startSorting();
    for (let i = 0; i < 8; i++) {
      await user.click(screen.getByRole("button", { name: "Not sure" }));
      await user.click(screen.getByRole("button", { name: "Next" }));
    }
    expect(screen.getByText(/You matched \d+ of 8\./)).toBeTruthy();
  });

  it("shows no score, percentage or faculty rating", async () => {
    const user = await startSorting();
    for (let i = 0; i < 8; i++) {
      await user.click(screen.getByRole("button", { name: "Not sure" }));
      await user.click(screen.getByRole("button", { name: "Next" }));
    }
    const summary = document.body.textContent ?? "";
    expect(summary).not.toMatch(/%/);
    expect(summary).not.toMatch(/awareness/i);
    expect(summary).not.toMatch(/score/i);
  });

  it("offers no praise ladder or trophy", async () => {
    // F5: celebrate notable moments, not every rep.
    const user = await startSorting();
    for (let i = 0; i < 8; i++) {
      await user.click(screen.getByRole("button", { name: "Not sure" }));
      await user.click(screen.getByRole("button", { name: "Next" }));
    }
    const summary = document.body.textContent ?? "";
    expect(summary).not.toMatch(/perfect|trophy|congratulations|well done|streak/i);
  });

  it("never calls itself a game", () => {
    // "These are example thoughts, not the user's own — framing matters when
    // the subject is self-criticism."
    const src = readFileSync(
      resolve(__dirname, "../../apps/app/components/ThoughtSort.tsx"),
      "utf8",
    );
    // Only the rendered strings; the header comment discusses the word.
    const rendered = src.slice(src.indexOf("export function ThoughtSort"));
    expect(rendered).not.toMatch(/>[^<]*\bgame\b/i);
  });
});

describe("Not sure is a real answer", () => {
  it("is offered alongside the other two", async () => {
    await startSorting();
    expect(screen.getByRole("button", { name: "Not sure" })).toBeTruthy();
  });

  it("is answered without being told off", async () => {
    const user = await startSorting();
    await user.click(screen.getByRole("button", { name: "Not sure" }));
    expect(screen.getByText(/plenty of thoughts sit in between/i)).toBeTruthy();
    expect(document.body.textContent).not.toMatch(/wrong|incorrect|try again/i);
  });

  it("still explains the thought", async () => {
    // The teaching is the `why`, not the verdict — so it has to survive the
    // answer that declines to guess.
    const user = await startSorting();
    const shown = shownThought();
    expect(shown in HELPFUL, `unknown thought: ${shown}`).toBe(true);
    await user.click(screen.getByRole("button", { name: "Not sure" }));

    const live = document.querySelector('[aria-live="polite"]')!;
    const paragraphs = [...live.querySelectorAll("p")].map((p) => p.textContent?.trim() ?? "");
    // Two: the verdict, then the reason. "Not sure" must still get the reason.
    expect(paragraphs).toHaveLength(2);
    expect(paragraphs[1].length).toBeGreaterThan(20);
  });

  it("does not count as a match", async () => {
    const user = await startSorting();
    for (let i = 0; i < 8; i++) {
      await user.click(screen.getByRole("button", { name: "Not sure" }));
      await user.click(screen.getByRole("button", { name: "Next" }));
    }
    expect(screen.getByText("You matched 0 of 8.")).toBeTruthy();
  });
});

describe("sorting", () => {
  it("runs eight of the twelve examples", async () => {
    await startSorting();
    expect(screen.getByText("Thought 1 of 8")).toBeTruthy();
  });

  it("counts every correct answer", async () => {
    const user = await startSorting();
    for (let i = 0; i < 8; i++) {
      const shown = shownThought();
      expect(shown in HELPFUL, `the component shows a thought this test does not know: ${shown}`)
        .toBe(true);
      await user.click(
        screen.getByRole("button", { name: HELPFUL[shown] ? "Helping" : "Not helping" }),
      );
      await user.click(screen.getByRole("button", { name: "Next" }));
    }
    expect(screen.getByText("You matched 8 of 8.")).toBeTruthy();
  });

  it("counts every wrong answer as a miss, without scolding", async () => {
    const user = await startSorting();
    for (let i = 0; i < 8; i++) {
      const shown = shownThought();
      await user.click(
        screen.getByRole("button", { name: HELPFUL[shown] ? "Not helping" : "Helping" }),
      );
      await user.click(screen.getByRole("button", { name: "Next" }));
    }
    expect(screen.getByText("You matched 0 of 8.")).toBeTruthy();
    expect(document.body.textContent).not.toMatch(/wrong|failed|poor/i);
  });

  it("tells a screen reader the feedback arrived", async () => {
    // The answer buttons are replaced by an explanation; without a live region
    // a screen-reader user is left on a control that no longer exists.
    const user = await startSorting();
    await user.click(screen.getByRole("button", { name: "Helping" }));
    expect(document.querySelector('[aria-live="polite"]')).toBeTruthy();
  });

  it("advances on its own after the feedback pause", async () => {
    vi.useFakeTimers();
    try {
      render(<ThoughtSort />);
      // userEvent needs the real clock; these clicks go through fireEvent-level
      // helpers instead.
      screen.getByRole("button", { name: "Start sorting" }).click();
      await act(async () => {});
      screen.getByRole("button", { name: "Helping" }).click();
      await act(async () => {});
      expect(screen.getByText("Thought 1 of 8")).toBeTruthy();
      await act(async () => {
        vi.advanceTimersByTime(2600);
      });
      expect(screen.getByText("Thought 2 of 8")).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it("can be run again from the summary", async () => {
    const user = await startSorting();
    for (let i = 0; i < 8; i++) {
      await user.click(screen.getByRole("button", { name: "Not sure" }));
      await user.click(screen.getByRole("button", { name: "Next" }));
    }
    await user.click(screen.getByRole("button", { name: "Again" }));
    expect(screen.getByText("Thought 1 of 8")).toBeTruthy();
  });
});

describe("the start button", () => {
  it("is named 'Start sorting', not 'Start'", () => {
    // The Toolkit page also carries the box breather's Start, and two
    // identically-named buttons on one page is a real screen-reader ambiguity —
    // it broke the e2e locator the same way.
    render(<ThoughtSort />);
    expect(screen.getByRole("button", { name: "Start sorting" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Start" })).toBeNull();
  });

  it("says up front that Not sure is allowed", async () => {
    render(<ThoughtSort />);
    expect(screen.getByText(/is a real\s+answer, not a cop-out/i)).toBeTruthy();
  });
});
