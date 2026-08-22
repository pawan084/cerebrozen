import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const api = vi.hoisted(() => ({ api: vi.fn(async () => ({ id: "j-1" })) }));
vi.mock("@/lib/api", () => api);

import {
  BOX_BREATH,
  GROUND_STEPS,
  PacedBreath,
  PromptSequence,
  SensorySteps,
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

describe("the body scan's prompt line", () => {
  // Three is enough to prove the mechanics; the real screens pass six.
  const PROMPTS = [
    "Let your jaw come unclenched.",
    "Drop your shoulders away from your ears.",
    "Open your hands.",
  ];

  function scan(onNext = vi.fn(), prompts: readonly string[] = PROMPTS) {
    const { container } = render(
      <PromptSequence
        prompts={prompts}
        ms={6000}
        ctaLabel="Skip ahead"
        doneCtaLabel="Continue"
        onNext={onNext}
      />,
    );
    return { onNext, container };
  }

  // One advance per prompt, never one big jump: each prompt's timer is
  // scheduled by an effect that only runs after the previous prompt's state
  // update flushes, so advancing the whole sequence at once fires exactly one
  // timer and the assertion after it would be reading the second prompt.
  async function runToLast() {
    for (let i = 1; i < PROMPTS.length; i++) {
      await act(async () => {
        vi.advanceTimersByTime(6000);
      });
    }
  }

  it("holds the first prompt for the full interval before moving", async () => {
    vi.useFakeTimers();
    try {
      scan();
      expect(screen.getByText(PROMPTS[0])).toBeTruthy();
      await act(async () => {
        vi.advanceTimersByTime(5999);
      });
      expect(screen.getByText(PROMPTS[0])).toBeTruthy();
      await act(async () => {
        vi.advanceTimersByTime(1);
      });
      expect(screen.getByText(PROMPTS[1])).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it("advances one prompt at a time, on its own", async () => {
    vi.useFakeTimers();
    try {
      scan();
      for (let i = 1; i < PROMPTS.length; i++) {
        await act(async () => {
          vi.advanceTimersByTime(6000);
        });
        expect(screen.getByText(PROMPTS[i])).toBeTruthy();
        expect(screen.queryByText(PROMPTS[i - 1])).toBeNull();
      }
    } finally {
      vi.useRealTimers();
    }
  });

  it("stops on the last prompt instead of running off the end", async () => {
    // `if (last) return` before scheduling. Without it the index walks past
    // the array and the screen goes blank on someone lying in the dark.
    vi.useFakeTimers();
    try {
      scan();
      await runToLast();
      await act(async () => {
        vi.advanceTimersByTime(6000 * 20);
      });
      expect(screen.getByText(PROMPTS[PROMPTS.length - 1])).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it("is skippable from the very first prompt", () => {
    // "A body scan you're stuck inside is not relaxing" — the button is never
    // gated on reaching the end, unlike the paced-breath runner's counter.
    const { onNext } = scan();
    screen.getByRole("button", { name: "Skip ahead" }).click();
    expect(onNext).toHaveBeenCalled();
  });

  it("offers to continue rather than to skip once the last prompt is up", async () => {
    vi.useFakeTimers();
    try {
      scan();
      expect(screen.getByRole("button", { name: "Skip ahead" })).toBeTruthy();
      await runToLast();
      expect(screen.getByRole("button", { name: "Continue" })).toBeTruthy();
      expect(screen.queryByRole("button", { name: "Skip ahead" })).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });

  it("shows one dot per prompt, filled up to where you are", async () => {
    vi.useFakeTimers();
    try {
      const { container } = scan();
      const dots = () => Array.from(container.querySelectorAll('[aria-hidden="true"] > div'));
      const filled = () =>
        dots().filter((d) => (d.getAttribute("style") ?? "").includes("var(--lav)")).length;
      expect(dots()).toHaveLength(PROMPTS.length);
      expect(filled()).toBe(1);
      await act(async () => {
        vi.advanceTimersByTime(6000);
      });
      expect(filled()).toBe(2);
    } finally {
      vi.useRealTimers();
    }
  });

  it("takes its timer with it when the screen goes away", () => {
    // Ritual screens swap phases by unmounting the previous one, so the scan is
    // routinely torn down mid-interval. The pending timer count is asserted
    // directly rather than through a symptom: React 18 no longer warns about a
    // setState on an unmounted tree, so watching console.error here catches
    // NOTHING — the first version of this test did exactly that and a mutant
    // deleting the cleanup sailed through it. What is real is the leak itself:
    // the callback holds the unmounted tree alive until it fires.
    vi.useFakeTimers();
    try {
      const { unmount } = render(
        <PromptSequence
          prompts={PROMPTS}
          ms={6000}
          ctaLabel="Skip ahead"
          doneCtaLabel="Continue"
          onNext={vi.fn()}
        />,
      );
      expect(vi.getTimerCount()).toBe(1);
      unmount();
      expect(vi.getTimerCount()).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });

  it("lets the two body scans differ exactly where the body is", () => {
    // Deliberate divergence, asserted so nobody dedupes these into one
    // constant: the wind-down speaks to someone already in bed, the builder to
    // someone in a chair at any hour. One shared list would tell a person
    // sitting at their desk to sink into the bed.
    const promptsIn = (path: string) => {
      const src = readFileSync(resolve(__dirname, "../..", path), "utf8");
      const block = src.match(/const SCAN_PROMPTS = \[([\s\S]*?)\];/)![1];
      return Array.from(block.matchAll(/"([^"]+)"/g)).map((m) => m[1]);
    };
    const bed = promptsIn("apps/app/app/(authed)/sleep/ritual/page.tsx");
    const chair = promptsIn("apps/app/app/(authed)/games/ritual/page.tsx");

    expect(bed).toHaveLength(chair.length);
    expect(bed.slice(0, 4)).toEqual(chair.slice(0, 4));
    expect(bed.slice(4)).not.toEqual(chair.slice(4));
    expect(bed.join(" ")).toMatch(/bed/i);
    expect(chair.join(" ")).toMatch(/chair/i);
    expect(chair.join(" ")).not.toMatch(/bed/i);
  });
});

describe("5-4-3-2-1 grounding", () => {
  function ground(onFinish?: () => void, finishLabel?: string) {
    render(<SensorySteps onFinish={onFinish} finishLabel={finishLabel} />);
  }

  it("counts down through the five senses in order", async () => {
    const user = userEvent.setup();
    ground();
    for (const [i, step] of GROUND_STEPS.entries()) {
      expect(screen.getByText(step.title)).toBeTruthy();
      expect(screen.getByText(step.hint)).toBeTruthy();
      if (i < GROUND_STEPS.length - 1) {
        await user.click(screen.getByRole("button", { name: "Next" }));
      }
    }
  });

  it("shows one step at a time", () => {
    ground();
    expect(screen.queryByText(GROUND_STEPS[1].title)).toBeNull();
  });

  it("offers Back only after the first step, and goes back one", async () => {
    const user = userEvent.setup();
    ground();
    expect(screen.queryByRole("button", { name: "Back" })).toBeNull();
    await user.click(screen.getByRole("button", { name: "Next" }));
    await user.click(screen.getByRole("button", { name: "Back" }));
    expect(screen.getByText(GROUND_STEPS[0].title)).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Back" })).toBeNull();
  });

  it("starts over rather than dead-ending when it stands alone in the Toolkit", async () => {
    // /games renders it with no onFinish: it's a tool you dip into, and the
    // last step has to lead somewhere or the card becomes a wall.
    const user = userEvent.setup();
    ground();
    for (let i = 0; i < GROUND_STEPS.length - 1; i++) {
      await user.click(screen.getByRole("button", { name: "Next" }));
    }
    await user.click(screen.getByRole("button", { name: "Start over" }));
    expect(screen.getByText(GROUND_STEPS[0].title)).toBeTruthy();
  });

  it("hands over instead of looping when it sits inside a ritual", async () => {
    const user = userEvent.setup();
    const onFinish = vi.fn();
    ground(onFinish, "Next block");
    for (let i = 0; i < GROUND_STEPS.length - 1; i++) {
      await user.click(screen.getByRole("button", { name: "Next" }));
    }
    await user.click(screen.getByRole("button", { name: "Next block" }));
    expect(onFinish).toHaveBeenCalledTimes(1);
    // A ritual that quietly restarts grounding while the user thinks they
    // moved on is the trap the standalone loop would create here.
    expect(screen.getByText(GROUND_STEPS[GROUND_STEPS.length - 1].title)).toBeTruthy();
  });

  it("never sends the senses anywhere", async () => {
    const user = userEvent.setup();
    ground();
    await user.click(screen.getByRole("button", { name: "Next" }));
    expect(api.api).not.toHaveBeenCalled();
  });
});

describe("the grounding copy is the same practice on every client", () => {
  // Hand-synced by comment in all three files, which is exactly the kind of
  // contract that drifts silently. iOS's ToolsViews GroundingView is a
  // different presentation — five rows read at once, with its own shorter
  // subtitles — so only the titles could be compared there.
  const android = readFileSync(
    resolve(__dirname, "../../apps/android/app/src/main/res/values/strings.xml"),
    "utf8",
  );
  const ios = readFileSync(
    resolve(__dirname, "../../apps/ios/CereBro/Features/Tools/Rituals.swift"),
    "utf8",
  );
  const androidString = (name: string) =>
    android.match(new RegExp(`<string name="${name}">([\\s\\S]*?)</string>`))![1].replace(/\\'/g, "'");

  it("names the same five senses in the same order as Android", () => {
    GROUND_STEPS.forEach((step, i) => {
      expect(androidString(`ground_step${i + 1}_title`)).toBe(step.title);
    });
  });

  it("gives the same hint under each one as Android", () => {
    GROUND_STEPS.forEach((step, i) => {
      expect(androidString(`ground_step${i + 1}_hint`)).toBe(step.hint);
    });
  });

  it("matches iOS's ritual runner, title and hint both", () => {
    const block = ios.match(/private let steps: \[\(String, String\)\] = \[([\s\S]*?)\n\s*\]/)![1];
    const pairs = Array.from(block.matchAll(/\("([^"]+)", "([^"]+)"\)/g)).map((m) => ({
      title: m[1],
      hint: m[2],
    }));
    expect(pairs).toEqual(GROUND_STEPS.map((s) => ({ title: s.title, hint: s.hint })));
  });

  it("counts down rather than up, on every client", () => {
    // 5-4-3-2-1 is the technique's name and its mechanism — attention narrows
    // as the count falls. A client that renders it 1-2-3-4-5 is doing a
    // different exercise under the same label.
    const leading = GROUND_STEPS.map((s) => Number(s.title.match(/^\d+/)![0]));
    expect(leading).toEqual([5, 4, 3, 2, 1]);
    for (let i = 1; i <= 5; i++) {
      expect(androidString(`ground_step${i}_title`)).toMatch(new RegExp(`^${6 - i} `));
    }
  });
});
