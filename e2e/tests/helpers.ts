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

export async function token(request: APIRequestContext, who: Persona): Promise<string> {
  return (await login(request, who)).access_token;
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
  await page.goto(base, { waitUntil: "domcontentloaded" });
  await page.locator('input[name="email"]').fill(PERSONAS[who].email);
  await page.locator('input[name="password"]').fill(PERSONAS[who].password);
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
}

/** The org id a persona's token is scoped to — the tenancy claim the engine enforces. */
export async function orgOf(request: APIRequestContext, t: string): Promise<string> {
  const r = await request.get(`${urls.platform}/users/me`, { headers: auth(t) });
  expect(r.ok()).toBeTruthy();
  return (await r.json()).org_id;
}
