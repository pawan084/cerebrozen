import { act, cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mod = vi.hoisted(() => ({ api: vi.fn() }));
vi.mock("@/lib/api", () => mod);

import { InterventionCard } from "../../apps/app/components/InterventionCard";

const REC = {
  id: "rec-1",
  action_kind: "practice",
  action_target: "grounding",
  reason: "You have checked in as tense four evenings this week.",
};

beforeEach(() => {
  mod.api.mockReset();
  mod.api.mockResolvedValue(REC);
});

afterEach(cleanup);

describe("it says what prompted it", () => {
  // "The app has always nudged, but never said what it noticed." The reason
  // arrives from the server already worded and frozen (services/interventions.py)
  // and the client never recomputes or paraphrases it, so what is shown is what
  // was recorded.
  it("shows the server's sentence verbatim", async () => {
    render(<InterventionCard />);
    expect(await screen.findByText(REC.reason)).toBeTruthy();
  });

  it("shows a reason it has never seen before, unchanged", async () => {
    // The proof that it is not a lookup: an unfamiliar wording has to survive.
    // A client that mapped reasons to canned copy would silently replace what
    // was actually recorded about this person.
    const odd = "Because your sleep diary has three blank nights in a row.";
    mod.api.mockResolvedValue({ ...REC, reason: odd });
    render(<InterventionCard />);
    expect(await screen.findByText(odd)).toBeTruthy();
  });
});

describe("it is an offer, not an alert", () => {
  it("can be dismissed", async () => {
    const user = userEvent.setup();
    render(<InterventionCard />);
    await screen.findByText(REC.reason);
    await user.click(screen.getByRole("button", { name: /not now|dismiss/i }));
    await waitFor(() => expect(screen.queryByText(REC.reason)).toBeNull());
  });

  it("disappears immediately, before the server is told", async () => {
    // `setGone(true)` runs before the POST. Waiting on the network to remove a
    // card someone has just declined would leave it on screen at exactly the
    // moment they said no.
    let resolvePost: (v: unknown) => void = () => {};
    mod.api.mockImplementation(async (path: string) => {
      if (path.endsWith("/active")) return REC;
      return new Promise((r) => {
        resolvePost = r;
      });
    });
    const user = userEvent.setup();
    render(<InterventionCard />);
    await screen.findByText(REC.reason);
    await user.click(screen.getByRole("button", { name: /not now|dismiss/i }));

    expect(screen.queryByText(REC.reason)).toBeNull();
    resolvePost({});
  });

  it("records the dismissal so the rule can hold its cooldown", async () => {
    const user = userEvent.setup();
    render(<InterventionCard />);
    await screen.findByText(REC.reason);
    await user.click(screen.getByRole("button", { name: /not now|dismiss/i }));
    await waitFor(() =>
      expect(mod.api).toHaveBeenCalledWith("/interventions/rec-1/dismiss", { method: "POST" }),
    );
  });

  it("records an accept when the offer is taken", async () => {
    const user = userEvent.setup();
    render(<InterventionCard />);
    await screen.findByText(REC.reason);
    await user.click(screen.getByRole("link", { name: /try grounding/i }));
    await waitFor(() =>
      expect(mod.api).toHaveBeenCalledWith("/interventions/rec-1/accept", { method: "POST" }),
    );
  });
});

describe("where it sends you", () => {
  it.each([
    ["grounding", "/games"],
    ["mini_journal", "/journal"],
    ["sleep_checkin", "/sleep"],
    // /crisis, not /support: the crisis page is public, renders without
    // JavaScript and leads with Tele-MANAS, so it is the right destination for
    // "talking to someone is an option".
    ["human_support", "/crisis"],
    ["breathing", "/games"],
  ])("sends %s to %s", async (target, href) => {
    mod.api.mockResolvedValue({ ...REC, action_target: target });
    render(<InterventionCard />);
    const link = await screen.findByRole("link");
    expect(link.getAttribute("href")).toBe(href);
  });

  it("sends a programme to the programmes list, whatever it is called", async () => {
    mod.api.mockResolvedValue({ ...REC, action_kind: "program", action_target: "Sleep Reset" });
    render(<InterventionCard />);
    const link = await screen.findByRole("link");
    expect(link.getAttribute("href")).toBe("/programs");
  });

  it("falls back to Home rather than to a dead link", async () => {
    // A target the client has never heard of must still lead somewhere. A card
    // whose only button 404s is worse than no card.
    mod.api.mockResolvedValue({ ...REC, action_target: "something_new" });
    render(<InterventionCard />);
    const link = await screen.findByRole("link");
    expect(link.getAttribute("href")).toBe("/home");
  });
});

describe("silence is the default", () => {
  /**
   * Let the effect's promise settle before asserting emptiness.
   *
   * `waitFor` checks IMMEDIATELY first, and the card is empty on the initial
   * render regardless — so `waitFor(() => expect(container.textContent).toBe(""))`
   * passes before the fetch has resolved and proves nothing. A mutant that
   * turned a failed fetch into a fake suggestion survived exactly that way.
   */
  async function settle() {
    await waitFor(() => expect(mod.api).toHaveBeenCalled());
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
  }

  it("renders nothing when there is no suggestion", async () => {
    mod.api.mockResolvedValue(null);
    const { container } = render(<InterventionCard />);
    await settle();
    expect(container.textContent).toBe("");
  });

  it("renders nothing when the fetch fails", async () => {
    // "Failing quietly is correct: a suggestion is never load-bearing, and an
    // error banner about a missing suggestion would be worse than silence."
    mod.api.mockRejectedValue(new Error("offline"));
    const { container } = render(<InterventionCard />);
    await settle();
    expect(container.textContent).toBe("");
  });

  it("shows one card, not a stack", async () => {
    render(<InterventionCard />);
    await screen.findByText(REC.reason);
    expect(screen.getAllByRole("link")).toHaveLength(1);
  });
});
