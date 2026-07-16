import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";
import { auth, orgOf, token } from "./helpers";

/* Cross-tenant denial — "the sharpest inherited edge" (docs/SECURITY.md).
 *
 * The reference isolated tenants by DEPLOYMENT CONFIG (a database name per deploy) and
 * shipped the incumbent tenant's real resource names as defaults. We moved isolation into
 * the app layer, which is only worth anything if it holds across the wire — the unit suites
 * assert it inside each service, and this asserts it through a real token, over HTTP,
 * against the composed stack. That is the gap a unit test cannot close: it is the JWT the
 * platform actually signs meeting the engine that actually validates it.
 */

test.describe("tenancy", () => {
  test("the engine refuses a request with no token at all", async ({ request }) => {
    const r = await request.get(`${urls.engine}/v1/agents`);
    expect(r.status(), "an unauthenticated engine read must not succeed").toBe(401);
  });

  test("the engine refuses a forged token", async ({ request }) => {
    // Right shape, wrong secret. If this ever passes, the shared-secret contract is broken
    // and every tenancy assertion below is theatre.
    const forged =
      "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9." +
      "eyJzdWIiOiJhdHRhY2tlciIsIm9yZ19pZCI6ImRlbW8tY28iLCJyb2xlIjoidXNlciJ9." +
      "bm90LXRoZS1yZWFsLXNpZ25hdHVyZS1hdC1hbGw";
    const r = await request.get(`${urls.engine}/v1/agents`, { headers: auth(forged) });
    expect(r.status()).toBe(401);
  });

  test("a member's token carries their org, and the platform agrees with it", async ({ request }) => {
    const t = await token(request, "member");
    const claims = JSON.parse(Buffer.from(t.split(".")[1], "base64").toString());
    expect(claims.org_id, "the engine 401s without an org claim").toBeTruthy();
    expect(claims.org_id).toBe(await orgOf(request, t));
  });

  test("an HR admin cannot read or write another org", async ({ request }) => {
    const hr = await token(request, "hr");
    // /orgs/me is scoped by the token, so the only way to ask about a different org is to
    // name one. 405 is the honest answer for GET: no read-by-id route exists at all, which
    // is stronger than a 404. The PATCH is the one that matters — a write door DOES exist.
    const read = await request.get(`${urls.platform}/orgs/not-their-org-id`, { headers: auth(hr) });
    expect([401, 403, 404, 405]).toContain(read.status());

    const write = await request.patch(`${urls.platform}/orgs/not-their-org-id`, {
      headers: auth(hr),
      data: { seats_total: 9999 },
    });
    expect([401, 403, 404], "an org_admin edited another tenant").toContain(write.status());
  });

  test("an HR admin cannot reach the internal-admin tenant list", async ({ request }) => {
    const hr = await token(request, "hr");
    const r = await request.get(`${urls.platform}/orgs`, { headers: auth(hr) });
    expect(r.status(), "org_admin must not enumerate tenants").toBe(403);
  });

  test("a member cannot reach HR analytics for their own org", async ({ request }) => {
    // Role is enforced server-side, not by hiding a tab.
    const member = await token(request, "member");
    const r = await request.get(`${urls.platform}/orgs/me/analytics`, { headers: auth(member) });
    expect(r.status()).toBe(403);
  });

  test("a member cannot reach the ops safety queue on the engine", async ({ request }) => {
    // The queue names which employees hit the crisis screen. Org-scoping is not enough:
    // their own HR must not read it either — see the engine's routers/safety.py.
    const member = await token(request, "member");
    const r = await request.get(`${urls.engine}/v1/safety/escalations`, { headers: auth(member) });
    expect(r.status()).toBe(403);
  });
});
