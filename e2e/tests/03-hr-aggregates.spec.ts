import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";
import { auth, signIn, token } from "./helpers";

/* The HR surface: aggregates, cohort floors, and the promise that this console cannot
 * reach a single person's coaching content.
 *
 * SECURITY.md makes the floor a SERVER-side property "enforced in the aggregation layer
 * (analytics._floored, after the COUNT query) — never in the UI, so no client can render
 * what the floor suppressed". A unit test proves the function floors. Only an e2e run
 * proves the HTTP response an HR admin's browser actually receives is floored — i.e. that
 * the suppressed value never leaves the building.
 */

test.describe("HR aggregates", () => {
  test("analytics are floored in the RESPONSE, not just in the UI", async ({ request }) => {
    const hr = await token(request, "hr");
    const r = await request.get(`${urls.platform}/orgs/me/analytics`, { headers: auth(hr) });
    expect(r.ok(), `HR analytics unreachable: ${r.status()}`).toBeTruthy();
    const body = await r.json();

    expect(body.cohort_floor, "the floor must be reported, not implied").toBeGreaterThan(0);
    const metrics = Object.entries(body.metrics) as [string, { value: number | null; suppressed: boolean }][];
    expect(metrics.length).toBeGreaterThan(0);

    // The load-bearing assertion: a suppressed metric carries NO value on the wire. If the
    // number rode along with `suppressed: true` and the UI merely hid it, the floor would
    // be a UI convention and anyone with devtools could read a cohort of one.
    for (const [name, m] of metrics) {
      if (m.suppressed) {
        expect(m.value, `${name} is suppressed but its value was still sent to the client`).toBeNull();
      }
    }
  });

  test("an HR admin can offboard a leaver, and it frees the seat", async ({ request }) => {
    /* The core B2B operation, and the roster was GET-only until 2026-07-16 — an org_admin
       could see a leaver and do nothing, while the seat they no longer used stayed counted
       against the org (_seats_used gates every invitation). Pinned across the wire because
       "does it actually cut access" spans the platform's auth path, not just this route. */
    const hr = await token(request, "hr");
    const member = await token(request, "member");
    const me = await (await request.get(`${urls.platform}/users/me`, { headers: auth(member) })).json();
    const before = (await (await request.get(`${urls.platform}/orgs/me`, { headers: auth(hr) })).json()).seats_used;

    const off = await request.patch(`${urls.platform}/orgs/me/people/${me.id}`, {
      headers: auth(hr), data: { is_active: false },
    });
    expect(off.ok(), await off.text()).toBeTruthy();
    expect((await off.json()).seats_used, "the seat was not freed").toBe(before - 1);

    // Offboarding theatre check: the leaver must actually be out.
    const still = await request.get(`${urls.platform}/users/me`, { headers: auth(member) });
    expect(still.status(), "a deactivated leaver still had access").toBe(401);

    // Put them back — the shared stack's seeded member must survive for later specs.
    const on = await request.patch(`${urls.platform}/orgs/me/people/${me.id}`, {
      headers: auth(hr), data: { is_active: true },
    });
    expect(on.ok()).toBeTruthy();
  });

  test("an org_admin cannot deactivate themselves", async ({ request }) => {
    // With one org_admin this locks the whole tenant out of its own console.
    const hr = await token(request, "hr");
    const me = await (await request.get(`${urls.platform}/users/me`, { headers: auth(hr) })).json();
    const r = await request.patch(`${urls.platform}/orgs/me/people/${me.id}`, {
      headers: auth(hr), data: { is_active: false },
    });
    expect(r.status()).toBe(400);
  });

  test("the HR console never exposes coaching content", async ({ request }) => {
    // "Counts, never content" is structural: the platform is the database this token
    // reaches, and it holds no content column and no content route.
    const hr = await token(request, "hr");
    const analytics = JSON.stringify(
      await (await request.get(`${urls.platform}/orgs/me/analytics`, { headers: auth(hr) })).json(),
    );
    for (const forbidden of ["transcript", "journal", "message", "commitment_body", "note"]) {
      expect(analytics.toLowerCase(), `HR analytics leaked "${forbidden}"`).not.toContain(forbidden);
    }
  });

  test("an HR admin cannot read a named employee's coaching content from the engine", async ({ request }) => {
    // There is no user_id parameter on any wellness route by design — an HR token asking
    // the engine for somebody's journal must not find a door.
    const hr = await token(request, "hr");
    const r = await request.get(`${urls.engine}/v1/wellness/journal?user_id=someone-else`, {
      headers: auth(hr),
    });
    if (r.ok()) {
      // If it answered, it answered about the CALLER (the param is ignored), never a target.
      const rows = await r.json();
      const text = JSON.stringify(rows);
      expect(text).not.toContain("someone-else");
    } else {
      expect([401, 403, 404, 422]).toContain(r.status());
    }
  });

  test("the HR console renders its own org and the seat count", async ({ page }) => {
    await signIn(page, urls.admin, "hr");
    await expect(page.getByRole("button", { name: "Overview" })).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText("seats used")).toBeVisible();
    // Role-gated tabs: the ops console's tabs must not exist for an org_admin.
    await expect(page.getByRole("button", { name: "Prompt workbook" })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Tenants" })).toHaveCount(0);
  });

  test("the suppression notice is shown when any metric is floored", async ({ page }) => {
    // The floor is a privacy feature, so a person reading the dashboard should be told a
    // number is missing on purpose rather than left thinking the product is broken.
    await signIn(page, urls.admin, "hr");
    await page.getByRole("button", { name: "Analytics" }).click();
    await expect(page.getByText(/Last \d+ days/)).toBeVisible({ timeout: 30_000 });
    const dashes = await page.locator(".stat b", { hasText: "—" }).count();
    if (dashes > 0) {
      await expect(page.getByText(/fewer than \d+ people contributed/)).toBeVisible();
    }
  });
});
