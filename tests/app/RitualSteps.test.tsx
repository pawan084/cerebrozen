import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({ api: vi.fn(async () => ({ id: "j-1" })) }));
vi.mock("@/lib/api", () => api);

import {
  BOX_BREATH,
  PacedBreath,
  SLOW_EXHALE,
  ThreeGoodThings,
  WritingStep,
} from "../../apps/app/components/RitualSteps";

beforeEach(() => {
  api.api.mockClear();
  api.api.mockResolvedValue({ id: "j-1" });
});

afterEach(cleanup);

function writingStep(onNext = vi.fn()) {
  render(
    <WritingStep
      eyebrow="Step one · Empty the desk"
      title="What's still on your mind?"
      sub="Anything unfinished."
      placeholder="Whatever's there…"
      ariaLabel="Brain dump"
      journalTitle="Before bed"
      journalTag="wind-down"
      journalSymbol="moon"
      why="Because it helps."
      onNext={onNext}
    />,
  );
  return { onNext };
}

describe("the brain dump stays on the device unless you say otherwise", () => {
  // The footnote under the textarea makes a literal promise: "This stays on
  // your device and is never sent anywhere — unless you choose to save it."
  // It sits under the most unguarded writing anyone does all day, which is why
  // the reference build's silent discard was changed to a stated one.
  it("prints the promise where the writing happens", () => {
    writingStep();
    expect(screen.getByText(/never sent anywhere/i)).toBeTruthy();
  });

  it("sends nothing while you type", async () => {
    const user = userEvent.setup();
    writingStep();
    await user.type(screen.getByLabelText("Brain dump"), "everything on my mind");
    expect(api.api).not.toHaveBeenCalled();
  });

  it("sends nothing when you move on without saving", async () => {
    const user = userEvent.setup();
    const { onNext } = writingStep();
    await user.type(screen.getByLabelText("Brain dump"), "everything on my mind");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(onNext).toHaveBeenCalled();
    expect(api.api, "the brain dump was sent without being saved").not.toHaveBeenCalled();
  });

  it("sends it only when Save is pressed, and sends what was written", async () => {
    const user = userEvent.setup();
    writingStep();
    await user.type(screen.getByLabelText("Brain dump"), "the unfinished thing");
    await user.click(screen.getByRole("button", { name: /save to journal/i }));

    await waitFor(() => expect(api.api).toHaveBeenCalled());
    const [path, init] = api.api.mock.calls[0] as unknown as [string, RequestInit];
    expect(path).toBe("/journal");
    expect(JSON.parse(init.body as string)).toEqual({
      title: "Before bed",
      body: "the unfinished thing",
      tags: ["wind-down"],
      symbol: "moon",
    });
  });
});

describe("saving", () => {
  it("cannot be pressed with nothing written", () => {
    writingStep();
    const save = screen.getByRole("button", { name: /save to journal/i }) as HTMLButtonElement;
    expect(save.disabled).toBe(true);
  });

  it("cannot be pressed with only whitespace", async () => {
    const user = userEvent.setup();
    writingStep();
    await user.type(screen.getByLabelText("Brain dump"), "   ");
    expect((screen.getByRole("button", { name: /save to journal/i }) as HTMLButtonElement).disabled)
      .toBe(true);
  });

  it("says so afterwards, and will not save the same thing twice", async () => {
    const user = userEvent.setup();
    writingStep();
    await user.type(screen.getByLabelText("Brain dump"), "something");
    await user.click(screen.getByRole("button", { name: /save to journal/i }));

    const saved = await screen.findByRole("button", { name: /saved to journal/i });
    expect((saved as HTMLButtonElement).disabled).toBe(true);
    expect(api.api).toHaveBeenCalledTimes(1);
  });

  it("keeps the words on screen when the save fails", async () => {
    // "keep the text on screen; the user can copy it or try again". Losing what
    // someone just wrote at their most unguarded is the worst possible way to
    // handle an error here.
    api.api.mockRejectedValue(new Error("offline"));
    const user = userEvent.setup();
    writingStep();
    await user.type(screen.getByLabelText("Brain dump"), "do not lose this");
    await user.click(screen.getByRole("button", { name: /save to journal/i }));

    await waitFor(() =>
      expect((screen.getByLabelText("Brain dump") as HTMLTextAreaElement).value).toBe(
        "do not lose this",
      ),
    );
  });

  it("lets you try again after a failure", async () => {
    api.api.mockRejectedValueOnce(new Error("offline"));
    const user = userEvent.setup();
    writingStep();
    await user.type(screen.getByLabelText("Brain dump"), "retry me");
    const save = screen.getByRole("button", { name: /save to journal/i });
    await user.click(save);
    await waitFor(() => expect((save as HTMLButtonElement).disabled).toBe(false));
    await user.click(save);
    await waitFor(() => expect(api.api).toHaveBeenCalledTimes(2));
  });

  it("moving on still works after a save", async () => {
    const user = userEvent.setup();
    const { onNext } = writingStep();
    await user.type(screen.getByLabelText("Brain dump"), "something");
    await user.click(screen.getByRole("button", { name: /save to journal/i }));
    await screen.findByRole("button", { name: /saved to journal/i });
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(onNext).toHaveBeenCalled();
  });
});

describe("the breath patterns", () => {
  it("makes the exhale longer than the inhale", () => {
    // The evidence claim the app actually makes: a longer exhale than inhale is
    // the part of slow breathing with real support behind it. Equalising these
    // would quietly turn a sourced pattern into a made-up one.
    const [inhale, exhale] = SLOW_EXHALE;
    expect(inhale.label).toMatch(/in/i);
    expect(exhale.label).toMatch(/out/i);
    expect(exhale.ms).toBeGreaterThan(inhale.ms);
    expect(inhale.ms).toBe(4000);
    expect(exhale.ms).toBe(6000);
  });

  it("has no holds in the slow-exhale pattern", () => {
    expect(SLOW_EXHALE.some((p) => /hold/i.test(p.label))).toBe(false);
  });

  it("keeps box breathing to four equal counts", () => {
    expect(BOX_BREATH).toHaveLength(4);
    expect(new Set(BOX_BREATH.map((p) => p.ms))).toEqual(new Set([4000]));
  });
});

describe("the paced-breath runner", () => {
  function breath(cycles = 2, onNext = vi.fn()) {
    render(
      <PacedBreath
        phases={SLOW_EXHALE}
        cycles={cycles}
        idleLabel="In 4 · out 6"
        doneLabel="That's it — rest well."
        ctaLabel="I'm settled"
        doneCtaLabel="Finish"
        onNext={onNext}
      />,
    );
    return { onNext };
  }

  it("starts on the inhale", () => {
    breath();
    expect(screen.getByText("Breathe in")).toBeTruthy();
  });

  it("counts one breath per full cycle, not one per phase", async () => {
    // "One counter, everything derived from it" — the alternative bumps a round
    // counter inside the phase updater, which React double-invokes in
    // StrictMode, and the breath count runs at double speed in dev.
    vi.useFakeTimers();
    try {
      breath(3);
      expect(screen.getByText("0 of 3 breaths")).toBeTruthy();
      await act(async () => {
        vi.advanceTimersByTime(4000); // inhale done -> exhale
      });
      expect(screen.getByText("0 of 3 breaths")).toBeTruthy();
      await act(async () => {
        vi.advanceTimersByTime(6000); // exhale done -> one full breath
      });
      expect(screen.getByText("1 of 3 breaths")).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it("finishes after the requested number of breaths", async () => {
    vi.useFakeTimers();
    try {
      breath(1);
      // Phase by phase. Each phase's timer is scheduled by an effect that only
      // runs after the previous phase's state update flushes, so advancing the
      // whole 10s in one step fires the inhale timer and nothing after it.
      await act(async () => {
        vi.advanceTimersByTime(4000);
      });
      await act(async () => {
        vi.advanceTimersByTime(6000);
      });
      expect(screen.getByText("That's it — rest well.")).toBeTruthy();
      expect(screen.getByRole("button", { name: "Finish" })).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it("is skippable before it finishes — a breath you are stuck in is not calming", () => {
    const { onNext } = breath(6);
    screen.getByRole("button", { name: "I'm settled" }).click();
    expect(onNext).toHaveBeenCalled();
  });

  it("can be paused, and says whether it is running", async () => {
    const user = userEvent.setup();
    breath();
    const toggle = screen.getByRole("button", { pressed: true });
    await user.click(toggle);
    // aria-pressed carries the state, so a screen-reader user knows the orb
    // stopped rather than having to watch it.
    expect(screen.getByRole("button", { pressed: false })).toBeTruthy();
    expect(screen.getByText("In 4 · out 6")).toBeTruthy();
  });
});

describe("three good things", () => {
  function goodThings(onNext = vi.fn()) {
    render(
      <ThreeGoodThings
        eyebrow="Step two"
        title="What went right today?"
        sub="Small counts."
        skipLabel="Skip tonight"
        why="Because it helps."
        onNext={onNext}
      />,
    );
    return { onNext };
  }

  it("offers three slots", () => {
    goodThings();
    for (const n of [1, 2, 3]) expect(screen.getByLabelText(`Good thing ${n}`)).toBeTruthy();
  });

  it("offers to skip when nothing has been written", () => {
    // Naming nothing is a legitimate answer on a bad night, and a button that
    // says Continue over three empty fields reads as a demand.
    goodThings();
    expect(screen.getByRole("button", { name: "Skip tonight" })).toBeTruthy();
  });

  it("becomes Continue once something is named", async () => {
    const user = userEvent.setup();
    goodThings();
    await user.type(screen.getByLabelText("Good thing 1"), "the coffee");
    expect(screen.getByRole("button", { name: "Continue" })).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Skip tonight" })).toBeNull();
  });

  it("never sends them anywhere", async () => {
    // Nothing in this step claims to save, so nothing should.
    const user = userEvent.setup();
    const { onNext } = goodThings();
    await user.type(screen.getByLabelText("Good thing 1"), "a message from a friend");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    expect(onNext).toHaveBeenCalled();
    expect(api.api).not.toHaveBeenCalled();
  });
});
