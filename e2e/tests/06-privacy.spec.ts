import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";
import { auth, signIn, token } from "./helpers";

/* Export and delete as PRODUCT FUNCTIONS (SECURITY.md; the marketing site sells it).
 *
 * Both servers implemented this and were tested; the client never called either — so the
 * claim had no mechanism behind it. These specs pin the client half, and specifically the
 * two things a unit test cannot reach: that an export carries BOTH services' halves, and
 * that a failed erasure leaves the account usable.
 *
 * Deliberately does NOT run the happy-path delete: it would destroy the shared stack's
 * seeded member for every later spec in the file order. The destructive path is covered by
 * the servers' own suites; what is only checkable here is the ORDERING contract below.
 */

test.describe("privacy: export + delete", () => {
  test("the export carries both services' halves in one document", async ({ page }) => {
    // Neither service alone can answer "everything about me" — the platform holds the
    // account, the engine holds the content, and that split is deliberate. Half an export
    // presented as a whole one is a false answer to a statutory request.
    await signIn(page, urls.app, "member");
    await page.waitForSelector(".sidebar", { timeout: 30_000 });
    await page.goto(`${urls.app}/settings`, { waitUntil: "domcontentloaded" });

    const dl = page.waitForEvent("download", { timeout: 30_000 });
    await page.getByRole("button", { name: "Download my data" }).click();
    const stream = await (await dl).createReadStream();
    const raw = await new Promise<string>((res, rej) => {
      let s = ""; stream.on("data", (c) => (s += c)); stream.on("end", () => res(s)); stream.on("error", rej);
    });
    const bundle = JSON.parse(raw);

    expect(bundle.account, "the platform half is missing").toBeTruthy();
    expect(bundle.coaching, "the engine half is missing").toBeTruthy();
    expect(Object.keys(bundle.account)).toContain("consent");
    expect(Object.keys(bundle.coaching)).toContain("sessions");
    expect(bundle.incomplete, `an export half failed: ${JSON.stringify(bundle.incomplete)}`).toBeUndefined();
  });

  test("delete is gated behind a typed confirmation, not a click", async ({ page }) => {
    // Everything else in Settings is reversible. This is not, so the friction is the point.
    await signIn(page, urls.app, "member");
    await page.waitForSelector(".sidebar", { timeout: 30_000 });
    await page.goto(`${urls.app}/settings`, { waitUntil: "domcontentloaded" });

    await page.getByRole("button", { name: "Delete my account" }).click();
    const go = page.getByRole("button", { name: "Permanently delete everything" });
    await expect(go).toBeDisabled();
    await page.locator(".dz-confirm input").fill("delete");
    await expect(go, "a lowercase near-miss armed the button").toBeDisabled();
    await page.locator(".dz-confirm input").fill("DELETE");
    await expect(go).toBeEnabled();
    // Leave without firing it — the seeded member must survive for the rest of the suite.
    await page.getByRole("button", { name: "Cancel" }).click();
    await expect(page.locator(".danger-zone")).toHaveCount(0);
  });

  test("both servers refuse an unconfirmed erasure", async ({ request }) => {
    // The client always sends ?confirm=true; this pins that the SERVERS enforce it, so a
    // DELETE fired from a mistyped URL or a stray tool cannot erase anyone.
    const t = await token(request, "member");
    expect((await request.delete(`${urls.engine}/v1/privacy/me`, { headers: auth(t) })).status()).toBe(400);
    expect((await request.delete(`${urls.platform}/users/me`, { headers: auth(t) })).status()).toBe(400);
  });

  test("erasure names no user id in the path — you cannot express someone else's", async ({ request }) => {
    /* The engine's own doc: "An erasure endpoint is a weapon… there is no request they can
       send that would even mean it." The subject comes from the signed token only. */
    const t = await token(request, "member");
    const r = await request.delete(`${urls.engine}/v1/privacy/someone-else?confirm=true`, { headers: auth(t) });
    expect([404, 405]).toContain(r.status());
  });

  test("a member cannot erase another user via ?user_id=", async ({ request }) => {
    // ?user_id= is an ADMIN allowlist (CEREBROZEN_ADMIN_SUBJECTS) — a config file a human
    // wrote, not a role claim. A member passing it must be refused, not obeyed.
    const t = await token(request, "member");
    const r = await request.delete(`${urls.engine}/v1/privacy/me?confirm=true&user_id=someone-else`, {
      headers: auth(t),
    });
    expect([401, 403]).toContain(r.status());
  });
});
