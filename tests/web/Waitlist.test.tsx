import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import Waitlist from "../../apps/web/components/Waitlist";

// This component resolves "@/lib/api" to apps/WEB's copy, not apps/app's —
// see the importer-aware alias in vitest.config.ts.

let fetchMock: ReturnType<typeof vi.fn>;

function response(status: number, body: unknown = {}) {
  return { ok: status >= 200 && status < 300, status, json: async () => body } as Response;
}

async function join(email = "someone@test.app") {
  const user = userEvent.setup();
  render(<Waitlist />);
  await user.type(screen.getByLabelText(/email address/i), email);
  await user.click(screen.getByRole("button", { name: /join the waitlist/i }));
  return user;
}

beforeEach(() => {
  fetchMock = vi.fn(async () => response(200, { status: "joined" }));
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  cleanup();
});

describe("joining", () => {
  it("posts the address to /waitlist as JSON", async () => {
    await join("someone@test.app");
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toMatch(/\/waitlist$/);
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ email: "someone@test.app" });
  });

  it("swaps the form out on success, so there is nothing to resubmit", async () => {
    await join();
    expect(await screen.findByText("Thank you")).toBeTruthy();
    expect(screen.queryByRole("button", { name: /join the waitlist/i })).toBeNull();
  });

  it("announces the confirmation politely instead of losing focus context", async () => {
    // The confirmation REPLACES the form, so a screen-reader user who is not
    // told what happened simply finds the controls gone.
    await join();
    const status = await screen.findByRole("status");
    expect(status.getAttribute("aria-live")).toBe("polite");
    expect(status.textContent).toMatch(/you're in/i);
  });

  it("does not send an empty address", async () => {
    const user = userEvent.setup();
    render(<Waitlist />);
    await user.click(screen.getByRole("button", { name: /join the waitlist/i }));
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe("a failure must never read as success", () => {
  // "A 429 or 5xx still carries a JSON body — parsing it and announcing
  // success would tell someone they're on the list when they aren't." Only 2xx
  // means the address was recorded. This is the one that matters: the person
  // walks away believing they will hear from us.
  it("says a rate limit is a rate limit", async () => {
    fetchMock.mockResolvedValue(response(429, { detail: "slow down" }));
    await join();
    expect(await screen.findByText(/too fast/i)).toBeTruthy();
    expect(screen.queryByText("Thank you")).toBeNull();
  });

  it("does not claim success on a 500, even though the body parses", async () => {
    fetchMock.mockResolvedValue(response(500, { status: "joined" }));
    await join();
    expect(await screen.findByText(/something went wrong/i)).toBeTruthy();
    expect(screen.queryByText("Thank you")).toBeNull();
  });

  it("keeps what was typed so it can be retried", async () => {
    // Erasing the field on failure makes the retry a re-type, which is where
    // people give up.
    fetchMock.mockResolvedValue(response(500));
    await join("keep-me@test.app");
    await screen.findByText(/something went wrong/i);
    expect((screen.getByLabelText(/email address/i) as HTMLInputElement).value).toBe(
      "keep-me@test.app",
    );
  });

  it("survives the network never answering", async () => {
    fetchMock.mockImplementation(() => {
      throw new TypeError("Failed to fetch");
    });
    await join();
    expect(await screen.findByText(/something went wrong/i)).toBeTruthy();
  });

  it("re-enables the button afterwards", async () => {
    // A form left disabled after a failure is a dead end.
    fetchMock.mockResolvedValue(response(500));
    await join();
    await screen.findByText(/something went wrong/i);
    await waitFor(() =>
      expect((screen.getByRole("button", { name: /join/i }) as HTMLButtonElement).disabled).toBe(
        false,
      ),
    );
  });
});

describe("the honeypot", () => {
  it("is hidden from people and skipped by the keyboard", async () => {
    const { container } = render(<Waitlist />);
    const pot = container.querySelector('input[name="company"]') as HTMLInputElement;
    expect(pot).toBeTruthy();
    expect(pot.getAttribute("aria-hidden")).toBe("true");
    expect(pot.tabIndex).toBe(-1);
    expect(pot.getAttribute("autocomplete")).toBe("off");
  });

  it("gives a bot a fake success and never calls the API", async () => {
    const user = userEvent.setup();
    const { container } = render(<Waitlist />);
    const pot = container.querySelector('input[name="company"]') as HTMLInputElement;

    await user.type(screen.getByLabelText(/email address/i), "bot@spam.test");
    // fireEvent, not user.type: the field carries `pointer-events: none` and
    // userEvent refuses to touch it — which is the component correctly
    // preventing HUMAN interaction, and exactly why the field works. A bot
    // sets the value directly, so that is what this simulates.
    fireEvent.change(pot, { target: { value: "Acme Corp" } });
    await user.click(screen.getByRole("button", { name: /join the waitlist/i }));

    expect(await screen.findByText("Thank you")).toBeTruthy();
    expect(fetchMock, "the honeypot submission reached the API").not.toHaveBeenCalled();
  });

  it("does not trip on an ordinary submission", async () => {
    await join();
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
  });
});

describe("the label — audit E20", () => {
  it("is a real, visible label rather than a placeholder", async () => {
    // `aria-label` alone served screen readers and left everyone else with a
    // placeholder, which disappears on the first keystroke and takes the
    // field's only description with it — hardest on a distracted reader who
    // looks away mid-form.
    const { container } = render(<Waitlist />);
    const label = container.querySelector("label.wl-label")!;
    expect(label).toBeTruthy();
    expect(label.textContent).toBe("Email address");

    const input = screen.getByLabelText(/email address/i) as HTMLInputElement;
    expect(label.getAttribute("for")).toBe(input.id);
    expect(input.id).toBeTruthy();
  });

  it("keeps the placeholder as an example, not as the description", async () => {
    render(<Waitlist />);
    const input = screen.getByLabelText(/email address/i) as HTMLInputElement;
    expect(input.placeholder).toBe("you@email.com");
    expect(input.type).toBe("email");
    expect(input.required).toBe(true);
  });
});
