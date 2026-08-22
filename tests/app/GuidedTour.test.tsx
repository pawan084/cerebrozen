import { readFileSync } from "node:fs";
import { resolve } from "node:path";

import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { GuidedTour, resetTour } from "../../apps/app/components/GuidedTour";

const KEY = "cb_tour_done";

beforeEach(() => window.localStorage.clear());
afterEach(cleanup);

async function tour() {
  const user = userEvent.setup();
  render(<GuidedTour />);
  await screen.findByRole("dialog");
  return user;
}

describe("once per browser", () => {
  it("shows on a first visit", async () => {
    render(<GuidedTour />);
    expect(await screen.findByRole("dialog", { name: "Guided tour" })).toBeTruthy();
  });

  it("does not show again once it has been seen", async () => {
    window.localStorage.setItem(KEY, "1");
    const { container } = render(<GuidedTour />);
    await waitFor(() => expect(container.textContent).toBe(""));
  });

  it("records that it was skipped", async () => {
    const user = await tour();
    await user.click(screen.getByRole("button", { name: "Skip" }));
    expect(window.localStorage.getItem(KEY)).toBe("1");
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("records that it was finished", async () => {
    const user = await tour();
    for (let i = 0; i < 3; i++) await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Let's begin" }));
    expect(window.localStorage.getItem(KEY)).toBe("1");
  });

  it("stays hidden rather than crashing when storage cannot be read", async () => {
    // Private mode. The read is inside a try/catch that swallows, so `show`
    // is never set and the overlay simply does not appear. That is the right
    // way round: an unreadable flag means the tour cannot know whether it has
    // been seen, and showing a full-screen overlay on EVERY visit would be
    // worse than never showing it. Crashing Home on first paint would be worse
    // than both.
    const getItem = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("SecurityError");
    });
    const { container } = render(<GuidedTour />);
    await waitFor(() => expect(container.textContent).toBe(""));
    getItem.mockRestore();
  });

  it("closes even when the flag cannot be written", async () => {
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("QuotaExceededError");
    });
    const user = await tour();
    await user.click(screen.getByRole("button", { name: "Skip" }));
    expect(screen.queryByRole("dialog")).toBeNull();
    setItem.mockRestore();
  });
});

describe("resetTour", () => {
  it("lets the tour run again", () => {
    window.localStorage.setItem(KEY, "1");
    resetTour();
    expect(window.localStorage.getItem(KEY)).toBeNull();
  });

  it("touches no other stored state", () => {
    // "Clears only the once-per-browser flag." Account → "Take a quick tour"
    // must not be a way to lose a journal draft or a session.
    const others = {
      cerebro_app_refresh: "r-1",
      cerebro_app_journal_draft: "half a sentence",
      cerebro_app_onboarding_draft: "{}",
      theme_mode: "dawn",
    };
    for (const [k, v] of Object.entries(others)) window.localStorage.setItem(k, v);
    window.localStorage.setItem(KEY, "1");

    resetTour();

    for (const [k, v] of Object.entries(others)) expect(window.localStorage.getItem(k)).toBe(v);
  });

  it("does not throw when storage is blocked", () => {
    const removeItem = vi.spyOn(Storage.prototype, "removeItem").mockImplementation(() => {
      throw new DOMException("SecurityError");
    });
    expect(() => resetTour()).not.toThrow();
    removeItem.mockRestore();
  });
});

describe("walking through it", () => {
  it("counts the stops", async () => {
    const user = await tour();
    expect(screen.getByText("Guided tour · 1 of 4")).toBeTruthy();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText("Guided tour · 2 of 4")).toBeTruthy();
  });

  it("offers 'Let's begin' only on the last stop", async () => {
    const user = await tour();
    expect(screen.queryByRole("button", { name: "Let's begin" })).toBeNull();
    for (let i = 0; i < 3; i++) await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByRole("button", { name: "Let's begin" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Next" })).toBeNull();
  });

  it("can be skipped from any stop", async () => {
    const user = await tour();
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Skip" }));
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("is a labelled modal, and its dots are decoration", async () => {
    // The "1 of 4" text carries the position; the dots repeating it to a screen
    // reader is noise.
    const { container } = render(<GuidedTour />);
    const dialog = await screen.findByRole("dialog");
    expect(dialog.getAttribute("aria-modal")).toBe("true");
    expect(dialog.getAttribute("aria-label")).toBe("Guided tour");
    expect(container.querySelector('[aria-hidden="true"]')).toBeTruthy();
  });
});

describe("the copy is the same promise on every client", () => {
  const android = readFileSync(
    resolve(__dirname, "../../apps/android/app/src/main/res/values/strings.xml"),
    "utf8",
  );
  const ios = readFileSync(
    resolve(__dirname, "../../apps/ios/CereBro/Features/Home/GuidedTour.swift"),
    "utf8",
  );
  const androidString = (name: string) =>
    android.match(new RegExp(`<string name="${name}">([\\s\\S]*?)</string>`))![1].replace(/\\'/g, "'");

  it("shows each stop's title in the same order", async () => {
    const user = await tour();
    for (let i = 1; i <= 4; i++) {
      expect(screen.getByText(androidString(`tour_stop${i}_title`))).toBeTruthy();
      if (i < 4) await user.click(screen.getByRole("button", { name: "Next" }));
    }
  });

  it("says the same thing about what it is, word for word, on all three", async () => {
    // "It's AI — never a therapist, and always honest about that." This is the
    // product's central claim about itself. Three clients wording it three ways
    // is three different promises, and this one is not ours to soften.
    const sentence = "It's AI — never a therapist, and always honest about that.";
    const user = await tour();
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText(new RegExp(sentence.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")))).toBeTruthy();
    expect(androidString("tour_stop3_body")).toContain(sentence);
    expect(ios).toContain(sentence);
  });

  it("makes the same privacy promise on all three", async () => {
    const promise = "Nothing is remembered without your say-so.";
    const user = await tour();
    for (let i = 0; i < 3; i++) await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText(new RegExp(promise.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")))).toBeTruthy();
    expect(androidString("tour_stop4_body")).toContain(promise);
    expect(ios).toContain(promise);
  });

  it("names each client's OWN navigation, which is why the bodies differ", async () => {
    // Deliberate divergence, asserted so nobody "fixes" it into a lie: Android
    // sends people to You → Privacy & memory, the web to Account → Privacy.
    const user = await tour();
    for (let i = 0; i < 3; i++) await user.click(screen.getByRole("button", { name: "Next" }));
    expect(screen.getByText(/Account → Privacy/)).toBeTruthy();
    expect(androidString("tour_stop4_body")).toMatch(/You → Privacy/);
  });
});
