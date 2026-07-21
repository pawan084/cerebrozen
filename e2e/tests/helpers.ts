import { expect, type APIRequestContext, type Page } from "@playwright/test";
import { urls } from "../playwright.config";

/* The dev-seeded personas — one per surface (services/platform/README.md). They exist so a
   fresh clone is usable, and they are gated twice (CEREBROZEN_SEED_DEV_ADMIN + a dev ENV)
   with guard_production() refusing to boot a deployed env with the flag on. Using them
   here is deliberate: the suite asserts on the same stack a developer gets from
   `docker compose up`, with no bespoke fixtures to drift from it. */
export const PERSONAS = {
  /** internal_admin, no org — the ops console (Tenants, Prompt workbook, Safety, Nudges). */
  ops: { email: "admin@cerebrozen.in", password: "admin12345" },
  /** org_admin of "Demo Co" — the HR surface (Overview, Analytics, People, Invite). */
  hr: { email: "hr@cerebrozen.in", password: "demo12345" },
  /** user of "Demo Co" — the employee app. */
  member: { email: "demo@cerebrozen.in", password: "demo12345" },
} as const;

export type Persona = keyof typeof PERSONAS;

/** Log in through the platform's API and return the token pair. */
export async function login(request: APIRequestContext, who: Persona) {
  const r = await request.post(`${urls.platform}/auth/login`, {
    form: { username: PERSONAS[who].email, password: PERSONAS[who].password },
  });
  expect(r.ok(), `${who} could not sign in — is the stack seeded?`).toBeTruthy();
  return (await r.json()) as { access_token: string; refresh_token: string };
}

/* One access token per persona, for the whole run.

   Not an optimisation — a correctness fix. The platform rate-limits the auth endpoints per
   IP (20/60s, `services/platform/app/ratelimit.py`), which is a control we want and which
   the suite was walking straight into: 35 `token()` calls and 18 form logins, all from one
   IP, single worker. `04-ops-console` alone did 23 logins inside ~30 seconds. Past the
   limit the platform correctly answers 429, the sign-in page says "Too many attempts", and
   whatever test happened to be running fails somewhere far from the cause — which is how
   this suite came to have ~10 failures that moved around between runs and looked like
   flakiness in the console, the crisis panel, and privacy.

   Re-logging-in was never the thing under test in those places; a persona's token is. The
   form login stays uncached wherever the login PATH is what a test is exercising, and
   `login()` itself is untouched for anything that needs a genuinely fresh pair.

   Safe to hold for a run: access tokens outlive it, and no test here depends on `token()`
   handing back a *new* one (the rotation tests drive localStorage directly, and the
   account-delete test deliberately stops short of firing). */
const tokenCache = new Map<Persona, string>();

export async function token(request: APIRequestContext, who: Persona): Promise<string> {
  const cached = tokenCache.get(who);
  if (cached) return cached;
  const fresh = (await login(request, who)).access_token;
  tokenCache.set(who, fresh);
  return fresh;
}

export const auth = (t: string) => ({ Authorization: `Bearer ${t}` });

/**
 * Sign a persona into a browser UI.
 *
 * Both clients keep their tokens in localStorage and both offer a dev-only one-click
 * demo chip — but the chip only *fills* the form, and it is stripped from a production
 * build. So this drives the real form: it works against either build, and it exercises
 * the login path a person actually takes rather than a shortcut that could rot.
 */
export async function signIn(page: Page, base: string, who: Persona) {
  await skipFirstRun(page);
  await page.goto(base, { waitUntil: "domcontentloaded" });
  await page.locator('input[name="email"]').fill(PERSONAS[who].email);
  await page.locator('input[name="password"]').fill(PERSONAS[who].password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}

/**
 * Start every signed-in test past the first-run welcome dialog.
 *
 * `.onb-overlay` is `aria-modal` and covers the app, so a click lands on the overlay rather
 * than the control the test named. Playwright says so honestly ("intercepts pointer
 * events") — but 15s later and against whatever control that test wanted, which reads as
 * the app being broken instead of a modal being open. It cost two privacy tests exactly
 * that way.
 *
 * The dialog is gated on `localStorage["cbz-onboarded"]` (`components/onboarding.tsx`), and
 * every Playwright test gets a fresh context — so it is not an occasional first-run thing,
 * it is EVERY test, on every navigation until something dismisses it. Setting the flag
 * before any page script runs is the deterministic fix; clicking it away is not, because
 * the dialog belongs to the app shell and returns on the next `page.goto`, and right after
 * the sign-in click it frequently has not rendered yet. (Both of those were tried.)
 *
 * This means no test here exercises onboarding. Nothing did before either — but a test that
 * wants to must skip this helper and drive the login form itself, rather than relaxing it.
 */
async function skipFirstRun(page: Page) {
  await page.addInitScript(() => {
    try { localStorage.setItem("cbz-onboarded", "1"); } catch { /* private mode */ }
  });
}

/** The org id a persona's token is scoped to — the tenancy claim the engine enforces. */
export async function orgOf(request: APIRequestContext, t: string): Promise<string> {
  const r = await request.get(`${urls.platform}/users/me`, { headers: auth(t) });
  expect(r.ok()).toBeTruthy();
  return (await r.json()).org_id;
}
