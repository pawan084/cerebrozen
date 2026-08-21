import { cleanup, render, screen, waitFor } from "@testing-library/react";
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
