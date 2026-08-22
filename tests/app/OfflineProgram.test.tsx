import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { OfflineProgram, type OfflineModule } from "../../apps/app/components/OfflineProgram";

const MODULES: OfflineModule[] = [
  { title: "Why sleep drifts", body: "What keeps the night short.", practice: "Note your wake time." },
  { title: "The wind-down", body: "An hour before bed.", practice: "Dim the lights." },
  { title: "Getting up", body: "A fixed hour, even after a bad night.", practice: "Same alarm." },
];

function program(id = "cbti") {
  return render(
    <OfflineProgram
      id={id}
      eyebrow="Sleep"
      title="CBT-I, in plain words"
      subtitle="Six short reads."
      modules={MODULES}
    />,
  );
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  window.localStorage.clear();
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  cleanup();
});

describe("'offline' is literal", () => {
  // "no fetch, no session, nothing to fail". These are the pages someone opens
  // on a train, and the whole reason they exist as static reading rather than
  // as a served programme.
  it("renders without touching the network", async () => {
    const user = await Promise.resolve(userEvent.setup());
    program();
    await user.click(screen.getAllByRole("button", { name: "Mark read" })[0]);
    expect(fetchMock, "an offline programme reached for the network").not.toHaveBeenCalled();
  });

  it("renders every module without a session", () => {
    program();
    for (const m of MODULES) {
      expect(screen.getByText(m.title)).toBeTruthy();
      expect(screen.getByText(m.body)).toBeTruthy();
      expect(screen.getByText(m.practice)).toBeTruthy();
    }
  });
});

describe("keeping your place", () => {
  it("marks a module read and says so", async () => {
    const user = userEvent.setup();
    program();
    await user.click(screen.getAllByRole("button", { name: "Mark read" })[0]);
    expect(screen.getByRole("button", { name: "Read" })).toBeTruthy();
  });

  it("announces the count, and says where it is kept", async () => {
    // "kept on this device only" is a privacy statement on a page about sleep
    // problems — the kind of reading someone may not want on a server.
    const user = userEvent.setup();
    program();
    await user.click(screen.getAllByRole("button", { name: "Mark read" })[0]);
    const status = screen.getByRole("status");
    expect(status.textContent).toMatch(/1 of 3 marked read/);
    expect(status.textContent).toMatch(/on this device only/i);
  });

  it("survives a reload", async () => {
    // "a reading list that forgets where you were is one people stop opening".
    const user = userEvent.setup();
    const { unmount } = program();
    await user.click(screen.getAllByRole("button", { name: "Mark read" })[1]);
    unmount();

    program();
    await waitFor(() => expect(screen.getByRole("button", { name: "Read" })).toBeTruthy());
    expect(screen.getByRole("status").textContent).toMatch(/1 of 3/);
  });

  it("can be un-marked", async () => {
    const user = userEvent.setup();
    program();
    const chip = screen.getAllByRole("button", { name: "Mark read" })[0];
    await user.click(chip);
    await user.click(screen.getByRole("button", { name: "Read" }));
    expect(screen.getAllByRole("button", { name: "Mark read" })).toHaveLength(3);
  });

  it("keeps each programme's progress separate", async () => {
    // CBT-I and MBCT are different reading lists; one key for both would have
    // them ticking each other's modules.
    const user = userEvent.setup();
    const { unmount } = program("cbti");
    await user.click(screen.getAllByRole("button", { name: "Mark read" })[0]);
    unmount();

    program("mbct");
    await waitFor(() => expect(screen.getAllByRole("button", { name: "Mark read" })).toHaveLength(3));
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("says nothing before anything is read", () => {
    program();
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("tells a screen reader which chips are on", async () => {
    const user = userEvent.setup();
    program();
    await user.click(screen.getAllByRole("button", { name: "Mark read" })[0]);
    expect(screen.getByRole("button", { pressed: true })).toBeTruthy();
    expect(screen.getAllByRole("button", { pressed: false })).toHaveLength(2);
  });
});

describe("when storage will not cooperate", () => {
  it("starts from zero rather than crashing on a corrupt value", () => {
    window.localStorage.setItem("cerebro_app_offline_cbti", "{not json");
    program();
    expect(screen.getAllByRole("button", { name: "Mark read" })).toHaveLength(3);
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("still shows the tick for this visit when writing is blocked", async () => {
    // "The tick still shows for this visit; it just will not survive."
    const setItem = vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
      throw new DOMException("QuotaExceededError");
    });
    const user = userEvent.setup();
    program();
    await user.click(screen.getAllByRole("button", { name: "Mark read" })[0]);
    expect(screen.getByRole("button", { name: "Read" })).toBeTruthy();
    setItem.mockRestore();
  });

  it("renders when reading is blocked", () => {
    const getItem = vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
      throw new DOMException("SecurityError");
    });
    expect(() => program()).not.toThrow();
    expect(screen.getAllByRole("button", { name: "Mark read" })).toHaveLength(3);
    getItem.mockRestore();
  });
});

describe("what it says it is", () => {
  it("says plainly that this is reading, not treatment", () => {
    // These are EDUCATIONAL overviews, not a clinician-led course. CBT-I and
    // MBCT are the names of real therapies, so a page carrying them has to say
    // which one it is not.
    program();
    expect(screen.getByText(/reading, not treatment/i)).toBeTruthy();
  });

  it("says it knows nothing about the reader", () => {
    program();
    expect(screen.getByText(/does not know anything about you/i)).toBeTruthy();
  });

  it("points at a person when it has been hard for a while", () => {
    // The one thing a static reading list can usefully do about a problem it
    // cannot address.
    program();
    // AppHeader carries a support link of its own, so this asserts that the
    // honesty paragraph has one rather than that the page has exactly one.
    const paragraph = screen.getByText(/reading, not treatment/i);
    const link = paragraph.querySelector('a[href="/support"]');
    expect(link, "the 'deserves a person' sentence has no way to reach one").toBeTruthy();
  });
});
