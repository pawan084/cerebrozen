import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Every door into an account, mocked at the module boundary. What is under test
// is the GATE in front of them, not what they do once past it.
// vi.hoisted because vi.mock factories are lifted above every const in the
// file; referencing a plain one gives "Cannot access 'api' before
// initialization" at import time, not at assertion time.
const api = vi.hoisted(() => ({
  signIn: vi.fn(async () => {}),
  signUp: vi.fn(async () => {}),
  requestOtp: vi.fn(async () => {}),
  verifyOtp: vi.fn(async () => {}),
  signInApple: vi.fn(async () => {}),
  signInGoogle: vi.fn(async () => {}),
}));
vi.mock("@/lib/api", () => api);

const social = vi.hoisted(() => ({
  appleIdentityToken: vi.fn(async () => ({ token: "apple-token", name: "A Person" })),
  googleIdToken: vi.fn(async () => "google-token"),
  NotConfiguredError: class NotConfiguredError extends Error {},
}));
vi.mock("@/lib/social", () => social);

import AuthPanel from "../../apps/app/components/AuthPanel";

const AGE_LABEL = /18 or older/i;
const AGE_ERROR = /confirm you're 18 or older/i;

/** Render the standalone /signin panel, which is the only caller that sets
 *  requireAgeAttest, and switch it to account creation. */
async function creatingAnAccount() {
  const user = userEvent.setup();
  const onAuthed = vi.fn();
  render(<AuthPanel initialMode="signUp" requireAgeAttest onAuthed={onAuthed} />);
  return { user, onAuthed };
}

beforeEach(() => {
  for (const fn of Object.values(api)) (fn as any).mockClear?.();
  social.appleIdentityToken.mockClear();
  social.googleIdToken.mockClear();
});

afterEach(cleanup);

describe("the 18+ gate — register D23", () => {
  // The comment above the checkbox says "the one gate every account has to pass
  // now renders for every sign-up path". That claim used to be false in two
  // places: a passwordless (OTP) sign-up met no 18+ moment at all, and neither
  // did Google. This is the test that keeps it true, one door at a time.
  it("shows the confirmation when creating an account outside the funnel", async () => {
    await creatingAnAccount();
    expect(screen.getByLabelText(AGE_LABEL)).toBeTruthy();
  });

  it("does not ask a returning user to confirm their age", async () => {
    // Signing IN is not account creation; a checkbox here would be noise on the
    // screen someone sees most often.
    render(<AuthPanel initialMode="signIn" requireAgeAttest onAuthed={vi.fn()} />);
    expect(screen.queryByLabelText(AGE_LABEL)).toBeNull();
  });

  it("does not ask inside the funnel, where the gate already ran", async () => {
    render(<AuthPanel initialMode="signUp" onAuthed={vi.fn()} />);
    expect(screen.queryByLabelText(AGE_LABEL)).toBeNull();
  });

  it("blocks an email sign-up until it is ticked", async () => {
    const { user } = await creatingAnAccount();
    await user.type(screen.getByLabelText(/name/i), "A Person");
    await user.type(screen.getByLabelText(/email/i), "new@test.app");
    await user.type(screen.getByLabelText(/password/i), "password123");
    await user.click(screen.getByRole("button", { name: "Create my account" }));

    // No account, which is the whole point. The assertion is on the OUTCOME
    // rather than on the error text, because this path is stopped twice over:
    // the checkbox carries `required`, so the browser refuses to submit the
    // form at all and `submitEmail` — where the friendly message lives — never
    // runs. Belt and braces, and worth knowing which one is holding.
    expect(api.signUp).not.toHaveBeenCalled();
  });

  it("lets the email sign-up through once it is ticked", async () => {
    const { user } = await creatingAnAccount();
    await user.type(screen.getByLabelText(/name/i), "A Person");
    await user.type(screen.getByLabelText(/email/i), "new@test.app");
    await user.type(screen.getByLabelText(/password/i), "password123");
    await user.click(screen.getByLabelText(AGE_LABEL));
    await user.click(screen.getByRole("button", { name: /create/i }));

    await waitFor(() => expect(api.signUp).toHaveBeenCalled());
  });

  it("blocks a passwordless sign-up too — the D23 hole", async () => {
    // "this was `!useCode`, so a passwordless (OTP) sign-up met no 18+ moment
    // at all". The code path signs up new addresses, so it creates accounts.
    const { user } = await creatingAnAccount();
    await user.click(screen.getByRole("button", { name: /without a password/i }));
    await user.type(screen.getByLabelText(/email/i), "new@test.app");
    await user.click(screen.getByRole("button", { name: "Email me a code" }));

    // Outcome, not message: the checkbox lives inside the same <form> and
    // carries `required`, so the browser refuses to submit and `submitEmail`
    // never reaches its own check. Two gates, one visible.
    expect(api.requestOtp).not.toHaveBeenCalled();
  });

  it("blocks Google — the other half of D23", async () => {
    const { user } = await creatingAnAccount();
    await user.click(screen.getByRole("button", { name: /google/i }));

    expect(await screen.findByText(AGE_ERROR)).toBeTruthy();
    expect(social.googleIdToken).not.toHaveBeenCalled();
    expect(api.signInGoogle).not.toHaveBeenCalled();
  });

  it("blocks Apple, which is the same door by a different name", async () => {
    // Apple is inert until the owner configures a Services ID, so this is
    // latent rather than live — but "every sign-up path" has to mean every
    // one, and the day Apple is wired is not the day to discover it was the
    // exception.
    const { user } = await creatingAnAccount();
    await user.click(screen.getByRole("button", { name: /apple/i }));

    // The social buttons are type="button" and sit outside the form's
    // submission, so `required` does NOT protect them — the JS check is the
    // only gate they have. Google has one. Apple did not.
    expect(await screen.findByText(AGE_ERROR)).toBeTruthy();
    expect(social.appleIdentityToken).not.toHaveBeenCalled();
    expect(api.signInApple).not.toHaveBeenCalled();
  });

  it("lets Google through once the box is ticked", async () => {
    const { user, onAuthed } = await creatingAnAccount();
    await user.click(screen.getByLabelText(AGE_LABEL));
    await user.click(screen.getByRole("button", { name: /google/i }));

    await waitFor(() => expect(api.signInGoogle).toHaveBeenCalledWith("google-token"));
    await waitFor(() => expect(onAuthed).toHaveBeenCalled());
  });

  it("lets Apple through once the box is ticked", async () => {
    const { user, onAuthed } = await creatingAnAccount();
    await user.click(screen.getByLabelText(AGE_LABEL));
    await user.click(screen.getByRole("button", { name: /apple/i }));

    await waitFor(() => expect(api.signInApple).toHaveBeenCalled());
    await waitFor(() => expect(onAuthed).toHaveBeenCalled());
  });

  it("never gates a returning user's social sign-in", async () => {
    // signIn mode means the account already exists and already passed a gate.
    const user = userEvent.setup();
    render(<AuthPanel initialMode="signIn" requireAgeAttest onAuthed={vi.fn()} />);
    await user.click(screen.getByRole("button", { name: /google/i }));
    await waitFor(() => expect(api.signInGoogle).toHaveBeenCalled());
  });
});

/** The embedded funnel case: the 18+ gate already ran upstream, so these
 *  render without it and the code path is what is under test. */
function panel(initialMode: "signIn" | "signUp" = "signIn") {
  const user = userEvent.setup();
  const onAuthed = vi.fn();
  render(<AuthPanel initialMode={initialMode} onAuthed={onAuthed} />);
  return { user, onAuthed };
}

async function switchToCode(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Sign in without a password" }));
}

describe("signing in without a password", () => {
  it("asks for an email and nothing else before a code exists", async () => {
    const { user } = panel();
    await switchToCode(user);
    expect(screen.getByRole("button", { name: "Email me a code" })).toBeTruthy();
    expect(screen.queryByLabelText("Password")).toBeNull();
    expect(screen.queryByLabelText("Code")).toBeNull();
  });

  it("sends the code to the address that was typed", async () => {
    const { user } = panel();
    await switchToCode(user);
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.click(screen.getByRole("button", { name: "Email me a code" }));
    await waitFor(() => expect(api.requestOtp).toHaveBeenCalledWith("someone@example.com"));
  });

  it("says where to look, rather than leaving a changed button as the only feedback", async () => {
    const { user } = panel();
    await switchToCode(user);
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.click(screen.getByRole("button", { name: "Email me a code" }));
    expect(await screen.findByText(/Code sent — enter the 6 digits from your email\./)).toBeTruthy();
  });

  it("gives the code field the hints a phone keyboard and a password manager need", async () => {
    // A 6-digit code typed on a phone with a full QWERTY keyboard, or retyped
    // by hand because autofill was never offered it, is the whole friction
    // this path exists to remove.
    const { user } = panel();
    await switchToCode(user);
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.click(screen.getByRole("button", { name: "Email me a code" }));
    const field = (await screen.findByLabelText("Code")) as HTMLInputElement;
    expect(field.getAttribute("inputMode")).toBe("numeric");
    expect(field.getAttribute("autocomplete")).toBe("one-time-code");
    expect(field.maxLength).toBe(6);
  });

  it("verifies the code against the same address, and reports how the session began", async () => {
    const { user, onAuthed } = panel();
    await switchToCode(user);
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.click(screen.getByRole("button", { name: "Email me a code" }));
    await user.type(await screen.findByLabelText("Code"), "123456");
    await user.click(screen.getByRole("button", { name: "Sign in with code" }));
    await waitFor(() => expect(api.verifyOtp).toHaveBeenCalledWith("someone@example.com", "123456"));
    // "otp" may be either a new account or a returning one — the code path
    // signs up unknown addresses — so callers treat it as possibly-new.
    expect(onAuthed).toHaveBeenCalledWith("otp");
  });

  it("never signs in with a password it was never given", async () => {
    const { user } = panel();
    await switchToCode(user);
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.click(screen.getByRole("button", { name: "Email me a code" }));
    await user.type(await screen.findByLabelText("Code"), "123456");
    await user.click(screen.getByRole("button", { name: "Sign in with code" }));
    await waitFor(() => expect(api.verifyOtp).toHaveBeenCalled());
    expect(api.signIn).not.toHaveBeenCalled();
    expect(api.signUp).not.toHaveBeenCalled();
  });

  it("throws the half-finished code away when the password is chosen instead", async () => {
    // Otherwise a code typed against the previous address is still sitting in
    // state, and the next "Email me a code" submits into a stale field.
    const { user } = panel();
    await switchToCode(user);
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.click(screen.getByRole("button", { name: "Email me a code" }));
    await user.type(await screen.findByLabelText("Code"), "999");

    await user.click(screen.getByRole("button", { name: "Use a password instead" }));
    expect(screen.getByLabelText("Password")).toBeTruthy();

    await switchToCode(user);
    expect(screen.queryByLabelText("Code")).toBeNull();
    expect(screen.getByRole("button", { name: "Email me a code" })).toBeTruthy();
  });
});

describe("the resend cooldown", () => {
  // Requesting a second code inside the window burns rate limit for no benefit,
  // so the button says when trying again can actually work.
  //
  // Driven with fireEvent rather than userEvent: userEvent schedules its own
  // delays between keystrokes, and pointing those at a fake clock that this
  // component is ALSO driving (a 1s interval) deadlocks — every one of these
  // cases hit the 20s timeout, and the two after them then failed on the
  // leaked fake clock rather than on anything they asserted.
  async function sendCode() {
    render(<AuthPanel onAuthed={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Sign in without a password" }));
    fireEvent.change(screen.getByLabelText("Email"), {
      target: { value: "someone@example.com" },
    });
    await act(async () => {
      fireEvent.submit(screen.getByLabelText("Email").closest("form")!);
    });
  }

  const secondsLeft = () =>
    Number(screen.getByRole("button", { name: /Resend code in/ }).textContent!.match(/(\d+)s/)![1]);

  it("counts down instead of offering a button that will fail", async () => {
    vi.useFakeTimers();
    try {
      await sendCode();
      const resend = screen.getByRole("button", { name: /Resend code/ });
      expect(resend.textContent).toMatch(/Resend code in \d+s/);
      expect((resend as HTMLButtonElement).disabled).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });

  it("ticks down while the user waits", async () => {
    vi.useFakeTimers();
    try {
      await sendCode();
      const before = secondsLeft();
      await act(async () => {
        vi.advanceTimersByTime(5000);
      });
      expect(secondsLeft()).toBeLessThan(before);
    } finally {
      vi.useRealTimers();
    }
  });

  it("opens up again once the window has passed", async () => {
    vi.useFakeTimers();
    try {
      await sendCode();
      expect(api.requestOtp).toHaveBeenCalledTimes(1);
      await act(async () => {
        vi.advanceTimersByTime(60_000);
      });
      const resend = screen.getByRole("button", { name: "Resend code" });
      expect((resend as HTMLButtonElement).disabled).toBe(false);
      await act(async () => {
        fireEvent.click(resend);
      });
      expect(api.requestOtp).toHaveBeenCalledTimes(2);
      expect(screen.getByText("A fresh code is on its way.")).toBeTruthy();
    } finally {
      vi.useRealTimers();
    }
  });

  it("starts the window again after a resend", async () => {
    vi.useFakeTimers();
    try {
      await sendCode();
      await act(async () => {
        vi.advanceTimersByTime(60_000);
      });
      await act(async () => {
        fireEvent.click(screen.getByRole("button", { name: "Resend code" }));
      });
      expect(screen.getByRole("button", { name: /Resend code in/ })).toBeTruthy();
      expect(secondsLeft()).toBeGreaterThan(50);
    } finally {
      vi.useRealTimers();
    }
  });

  it("stops ticking when the panel goes away", async () => {
    // The interval outlives the panel otherwise — /signin unmounts this the
    // moment a session exists, and a 1s setState would keep firing at a tree
    // that is gone. Asserted only once a code has been sent, because before
    // that there is no interval and the check would pass on an empty clock.
    vi.useFakeTimers();
    try {
      await sendCode();
      expect(vi.getTimerCount()).toBeGreaterThan(0);
      cleanup();
      expect(vi.getTimerCount()).toBe(0);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe("when something goes wrong", () => {
  it("does not show the browser's developer string to a user", async () => {
    // fetch() rejects with a bare TypeError when the API is unreachable.
    // "Failed to fetch" is a message for a console, not for someone trying to
    // get into their account.
    const { user } = panel();
    api.signIn.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.type(screen.getByLabelText("Password"), "a good long password");
    await user.click(screen.getByRole("button", { name: "Continue with email" }));
    expect(await screen.findByText(/couldn't reach CereBro just now/i)).toBeTruthy();
    expect(screen.queryByText(/failed to fetch/i)).toBeNull();
  });

  it("passes a real server message through, because it says something useful", async () => {
    const { user } = panel();
    api.signIn.mockRejectedValueOnce(new Error("That email and password don't match."));
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.type(screen.getByLabelText("Password"), "a good long password");
    await user.click(screen.getByRole("button", { name: "Continue with email" }));
    expect(await screen.findByText("That email and password don't match.")).toBeTruthy();
  });

  it("still says something when the failure carries no message at all", async () => {
    const { user } = panel();
    api.signIn.mockRejectedValueOnce({});
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.type(screen.getByLabelText("Password"), "a good long password");
    await user.click(screen.getByRole("button", { name: "Continue with email" }));
    expect(await screen.findByText("That didn't go through.")).toBeTruthy();
  });

  it("interrupts, and offers the retry rather than the form again", async () => {
    const { user } = panel();
    api.signIn.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.type(screen.getByLabelText("Password"), "a good long password");
    await user.click(screen.getByRole("button", { name: "Continue with email" }));
    await screen.findByRole("alert");
    expect(screen.getByRole("button", { name: "Try again" })).toBeTruthy();
  });

  it("retries the thing that failed, without asking for it all again", async () => {
    const { user, onAuthed } = panel();
    api.signIn.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.type(screen.getByLabelText("Password"), "a good long password");
    await user.click(screen.getByRole("button", { name: "Continue with email" }));
    await user.click(await screen.findByRole("button", { name: "Try again" }));
    await waitFor(() => expect(onAuthed).toHaveBeenCalledWith("signIn"));
    expect(api.signIn).toHaveBeenCalledTimes(2);
    expect(api.signIn).toHaveBeenLastCalledWith("someone@example.com", "a good long password");
  });

  it("retries the code request too, not just the password path", async () => {
    const { user } = panel();
    api.requestOtp.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await switchToCode(user);
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.click(screen.getByRole("button", { name: "Email me a code" }));
    await user.click(await screen.findByRole("button", { name: "Try again" }));
    await screen.findByLabelText("Code");
    expect(api.requestOtp).toHaveBeenCalledTimes(2);
  });

  it("treats an unconfigured provider as a notice, not a failure", async () => {
    // Sign in with Apple is code-complete and inert until a Services ID is
    // configured. Someone pressing it has done nothing wrong, and a red error
    // would say they had.
    const { user } = panel();
    social.appleIdentityToken.mockRejectedValueOnce(
      new social.NotConfiguredError("Sign in with Apple isn't set up yet."),
    );
    await user.click(screen.getByRole("button", { name: /Sign in with Apple/ }));
    expect(await screen.findByText("Sign in with Apple isn't set up yet.")).toBeTruthy();
    expect(screen.queryByRole("alert")).toBeNull();
    expect(screen.getByRole("status")).toBeTruthy();
  });
});

describe("the password hint", () => {
  async function typePassword(value: string) {
    const { user } = panel("signUp");
    await user.type(screen.getByLabelText("Password"), value);
    return { user };
  }

  it("says nothing until something has been typed", () => {
    panel("signUp");
    expect(screen.queryByRole("status")).toBeNull();
  });

  it("asks for the server's own minimum, and names the number", async () => {
    await typePassword("short");
    expect(screen.getByText("At least 8 characters.")).toBeTruthy();
  });

  it("acknowledges a workable password without pretending it is ideal", async () => {
    await typePassword("nine char");
    expect(screen.getByText("Decent — longer is stronger.")).toBeTruthy();
  });

  // The boundaries, not just the middles. Sliding the first threshold from 8 to
  // 6 leaves every "short"/"nine char"/passphrase case reading exactly the
  // same — a mutant proved it — while a 7-character password would be told it
  // was decent and then rejected by the server that owns the real minimum.
  it.each([
    ["1234567", "At least 8 characters."],
    ["12345678", "Decent — longer is stronger."],
    ["12345678901", "Decent — longer is stronger."],
    ["123456789012", "Good length."],
  ])("reads a %s-long password the way the server will", async (password, hint) => {
    await typePassword(password);
    expect(screen.getByText(hint)).toBeTruthy();
  });

  it("measures length, and only length", async () => {
    // No trademarked strength theatre: length is the one factor that reliably
    // matters, so a long all-lowercase phrase reads as good — which is true.
    await typePassword("correct horse battery staple");
    expect(screen.getByText("Good length.")).toBeTruthy();
  });

  it("is guidance, never a gate — a short password still submits", async () => {
    // The server owns the minimum. A client-side block here would invent a
    // second rule that the API does not have.
    const { user } = panel("signUp");
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.type(screen.getByLabelText("Password"), "sixchar");
    await user.click(screen.getByRole("button", { name: "Create my account" }));
    await waitFor(() =>
      expect(api.signUp).toHaveBeenCalledWith("someone@example.com", "sixchar", ""),
    );
  });

  it("stays out of the way of someone signing in", async () => {
    const { user } = panel("signIn");
    await user.type(screen.getByLabelText("Password"), "x");
    expect(screen.queryByText("At least 8 characters.")).toBeNull();
  });

  it("has nothing to say about a password that is not being used", async () => {
    const { user } = panel("signUp");
    await user.type(screen.getByLabelText("Password"), "x");
    expect(screen.getByText("At least 8 characters.")).toBeTruthy();
    await switchToCode(user);
    expect(screen.queryByText("At least 8 characters.")).toBeNull();
  });
});

describe("the button says which door it is", () => {
  it.each([
    ["signIn" as const, "Continue with email"],
    ["signUp" as const, "Create my account"],
  ])("reads %s as '%s'", (mode, label) => {
    panel(mode);
    expect(screen.getByRole("button", { name: label })).toBeTruthy();
  });

  it("changes from asking for a code to using one", async () => {
    const { user } = panel();
    await switchToCode(user);
    expect(screen.getByRole("button", { name: "Email me a code" })).toBeTruthy();
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.click(screen.getByRole("button", { name: "Email me a code" }));
    expect(await screen.findByRole("button", { name: "Sign in with code" })).toBeTruthy();
  });

  it("says it is working, and stops taking presses while it is", async () => {
    const { user } = panel();
    let release!: () => void;
    api.signIn.mockImplementationOnce(
      () => new Promise<void>((resolve) => (release = () => resolve())),
    );
    await user.type(screen.getByLabelText("Email"), "someone@example.com");
    await user.type(screen.getByLabelText("Password"), "a good long password");
    await user.click(screen.getByRole("button", { name: "Continue with email" }));

    const cta = await screen.findByRole("button", { name: "One moment…" });
    expect((cta as HTMLButtonElement).disabled).toBe(true);
    release();
  });
});
