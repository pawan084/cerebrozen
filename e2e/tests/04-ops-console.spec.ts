import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";
import { auth, signIn, token } from "./helpers";

/* The ops console: the prompt workbook, the agent flow, the safety queue — and the CSP
 * that wraps all of it.
 *
 * The workbook is where the coaching content is edited, so the properties worth pinning
 * across the wire are the SAFETY boundaries: what the workbook may NOT contain, and that
 * an org_admin cannot reach any of it.
 */

test.describe("ops console", () => {
  test("the ops console serves a per-request nonce CSP", async ({ request }) => {
    /* Promised when the CSP landed: the admin has no unit-test runner, so this is its
       home. The point is script-src — an injected <script> cannot run without this
       request's nonce. */
    const r = await request.get(urls.admin);
    const csp = r.headers()["content-security-policy"];
    expect(csp, "the admin served no CSP at all").toBeTruthy();
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp, "script-src must not allow inline").not.toMatch(/script-src[^;]*'unsafe-inline'/);
    expect(csp, "no nonce → the policy is decorative").toMatch(/script-src[^;]*'nonce-[^']+'/);
  });

  test("the nonce is fresh on every request", async ({ request }) => {
    // A reused nonce is a guessable nonce, which is no nonce at all.
    const nonce = async () => {
      const csp = (await request.get(urls.admin)).headers()["content-security-policy"];
      return /'nonce-([^']+)'/.exec(csp)?.[1];
    };
    const [a, b] = [await nonce(), await nonce()];
    expect(a).toBeTruthy();
    expect(a, "the same nonce was served twice").not.toBe(b);
  });

  test("the ops console loads under its own CSP with no violations", async ({ page }) => {
    // A CSP that breaks the console would be reverted in a hurry, so prove it doesn't:
    // sign in, open the heaviest surface (React Flow), and catch any violation.
    const violations: string[] = [];
    page.on("console", (m) => {
      if (/Content Security Policy|Refused to/i.test(m.text())) violations.push(m.text());
    });
    await signIn(page, urls.admin, "ops");
    await page.getByRole("button", { name: "Agent flow" }).click({ timeout: 30_000 });
    await page.waitForSelector(".react-flow__node", { timeout: 30_000 });
    expect(violations, `CSP broke the console: ${violations.join(" | ")}`).toEqual([]);
  });

  test("the workbook is reachable by ops and carries the arc's agents", async ({ request }) => {
    const ops = await token(request, "ops");
    const r = await request.get(`${urls.engine}/v1/agents`, { headers: auth(ops) });
    expect(r.ok(), `workbook unreachable: ${r.status()}`).toBeTruthy();
    const agents = (await r.json()).agents;
    expect(agents.length, "an empty workbook means the prompt source didn't load").toBeGreaterThan(0);
    for (const a of agents) {
      expect(a.stage, "every agent needs a stage the graph can route to").toBeTruthy();
    }
  });

  test("the crisis reply is NOT editable from the workbook", async ({ request }) => {
    /* CLAUDE.md rule 4, asserted against the real workbook the stack loaded: "crisis text
       and takeover routing never move into the editable prompt workbook". A prompt author
       must not be able to edit or disable the crisis reply, so no workbook agent may be
       the crisis responder. */
    const ops = await token(request, "ops");
    const agents = (await (await request.get(`${urls.engine}/v1/agents`, { headers: auth(ops) })).json())
      .agents as { stage: string }[];
    const stages = agents.map((a) => a.stage);
    for (const forbidden of ["safe_response", "safety", "crisis"]) {
      expect(stages, `"${forbidden}" is editable from the workbook — safety must live in code`)
        .not.toContain(forbidden);
    }
  });

  test("the compiled arc is read-only over HTTP", async ({ request }) => {
    // The agent-flow canvas is read-only because routing is code predicates over typed
    // state. If a write side ever appears, the canvas's honesty claim dies with it.
    const ops = await token(request, "ops");
    const r = await request.get(`${urls.engine}/v1/graph`, { headers: auth(ops) });
    expect(r.ok()).toBeTruthy();
    const g = await r.json();
    expect(g.nodes.length).toBeGreaterThan(0);
    expect(g.edges.length).toBeGreaterThan(0);
    for (const method of ["post", "put", "patch", "delete"] as const) {
      const w = await request[method](`${urls.engine}/v1/graph`, { headers: auth(ops), data: {} });
      expect([404, 405], `${method.toUpperCase()} /v1/graph exists — the arc must not be writable`)
        .toContain(w.status());
    }
  });

  test("no one but an operator can reach the workbook", async ({ request }) => {
    /* This spec FAILED on its first run and that was the point: the engine had no role
       checks at all, so a rank-and-file employee could GET /v1/prompts/download and walk
       off with the whole coaching workbook, and any token could PUT a stage — rewriting or
       disabling a coaching agent for EVERY tenant, since the workbook is global. */
    for (const who of ["hr", "member"] as const) {
      const t = await token(request, who);
      const read = await request.get(`${urls.engine}/v1/prompts/core_coaching_agent`, { headers: auth(t) });
      expect(read.status(), `${who} read a coaching prompt`).toBe(403);

      const dl = await request.get(`${urls.engine}/v1/prompts/download`, { headers: auth(t) });
      expect(dl.status(), `${who} downloaded the workbook`).toBe(403);

      const write = await request.put(`${urls.engine}/v1/prompts/core_coaching_agent`, {
        headers: auth(t),
        data: { enabled: false },
      });
      expect(write.status(), `${who} rewrote the GLOBAL workbook`).toBe(403);
    }
  });

  test("a member cannot run the console against the live model", async ({ request }) => {
    // An open runner is a billing hole as well as an IP one.
    const t = await token(request, "member");
    const r = await request.post(`${urls.engine}/v1/console/run`, {
      headers: auth(t),
      data: { system: "x", user: "y" },
    });
    expect(r.status()).toBe(403);
  });

  test("a member cannot read the ops queues", async ({ request }) => {
    const t = await token(request, "member");
    for (const path of ["/v1/safety/escalations", "/v1/nudges", "/v1/agents", "/v1/graph"]) {
      const r = await request.get(`${urls.engine}${path}`, { headers: auth(t) });
      expect(r.status(), `a member reached ${path}`).toBe(403);
    }
  });

  test("but a crisis helpline is never role-gated", async ({ request }) => {
    // The counterweight to every assertion above. Denying someone in crisis a phone
    // number because they are not staff would be the worst outcome in this codebase.
    const t = await token(request, "member");
    const r = await request.get(`${urls.engine}/v1/safety/helplines?region=GB`, { headers: auth(t) });
    expect(r.status(), "a member was refused a helpline").toBe(200);
    expect((await r.json()).helplines.length).toBeGreaterThan(0);
  });

  test("regulated mode is NOT a switch in the console", async ({ page }) => {
    /* SECURITY.md: regulated-mode opt-out is "a contract-level decision with counsel
       sign-off, not an admin toggle" — EU AI Act Art. 5 sits behind it. The API accepts
       the field for an operator acting on a signed contract; a control anyone with the tab
       can flick is a different thing, and this spec is what keeps the two apart. */
    await signIn(page, urls.admin, "ops");
    await page.getByRole("button", { name: "Tenants" }).click({ timeout: 30_000 });
    await page.waitForSelector("tbody tr", { timeout: 30_000 });
    const row = page.locator("tbody tr").first();
    await expect(row.locator("input[type=checkbox]"), "regulated mode became a toggle").toHaveCount(0);
    await expect(page.getByText(/Regulated mode is not/)).toBeVisible();
  });

  test("the tenant's crisis regions come from the engine, not a retyped list", async ({ page, request }) => {
    // A region the engine cannot localise would silently fall back to the international
    // finder, and the operator who picked it would never know it did nothing.
    const ops = await token(request, "ops");
    const served = (await (await request.get(`${urls.engine}/v1/safety/helplines`, { headers: auth(ops) })).json()).regions;
    await signIn(page, urls.admin, "ops");
    await page.getByRole("button", { name: "Tenants" }).click({ timeout: 30_000 });
    await page.waitForSelector("select.mini", { timeout: 30_000 });
    const options = await page.locator("select.mini").first().locator("option").evaluateAll((els) =>
      els.map((e) => (e as HTMLOptionElement).value));
    // "" is a real choice: the engine answers an unknown region with an international
    // directory rather than guessing a country.
    expect(options).toContain("");
    for (const r of served) expect(options, `the console cannot set ${r}`).toContain(r);
  });

  test("seats are editable and cannot drop below the seats in use", async ({ request }) => {
    // Renewals and upsells are a seat change; until now that meant editing Postgres.
    const ops = await token(request, "ops");
    const orgs = await (await request.get(`${urls.platform}/orgs`, { headers: auth(ops) })).json();
    const org = orgs[0];
    const up = await request.patch(`${urls.platform}/orgs/${org.id}`, {
      headers: auth(ops), data: { seats_total: org.seats_total + 5 },
    });
    expect(up.ok(), await up.text()).toBeTruthy();
    expect((await up.json()).seats_total).toBe(org.seats_total + 5);
    // Put it back so the shared stack is unchanged for later specs.
    await request.patch(`${urls.platform}/orgs/${org.id}`, {
      headers: auth(ops), data: { seats_total: org.seats_total },
    });
  });

  test("the safety queue is signal-only — it never carries a disclosure", async ({ request }) => {
    const ops = await token(request, "ops");
    const r = await request.get(`${urls.engine}/v1/safety/escalations`, { headers: auth(ops) });
    expect(r.ok()).toBeTruthy();
    const body = await r.json();
    expect(body).toHaveProperty("armed");
    const text = JSON.stringify(body.escalations ?? []);
    for (const forbidden of ["message", "text", "transcript", "content", "disclosure"]) {
      expect(text.toLowerCase(), `the safety queue leaked "${forbidden}"`).not.toContain(`"${forbidden}"`);
    }
  });

  /* Acknowledging is what makes the queue a queue rather than an ever-growing log. These
     run against the composed stack because the engine unit suite runs on mongomock while
     Postgres is the default store — a divergence that has now produced four bugs that
     passed a green suite (see services/engine/tests/conftest.py). */

  test("an escalation can be acknowledged, and that drains the open queue", async ({ request }) => {
    const ops = await token(request, "ops");
    const open = async (status = "open") =>
      (await (await request.get(`${urls.engine}/v1/safety/escalations?status=${status}`,
        { headers: auth(ops) })).json());

    const before = await open();
    test.skip(before.count === 0, "no escalation in the shared stack's queue to acknowledge");

    const row = before.escalations[0];
    expect(row.id, "an empty id would leave the Resolve button nothing to send").toBeTruthy();

    const r = await request.post(
      `${urls.engine}/v1/safety/escalations/${encodeURIComponent(row.id)}/ack`,
      { headers: auth(ops) },
    );
    expect(r.ok()).toBeTruthy();

    const resolved = (await open("resolved")).escalations.find((e: any) => e.id === row.id);
    expect(resolved, "an acknowledged row left the resolved view").toBeTruthy();
    expect(resolved.acknowledged_by, "the ack recorded nobody").toBeTruthy();
    // Who and when — never what. There is no note/reason/outcome field by design.
    expect(Object.keys(resolved).sort()).toEqual([
      "acknowledged_at", "acknowledged_by", "at", "delivered", "detected_by",
      "id", "org_id", "session_id", "user_id",
    ]);
    expect((await open()).escalations.some((e: any) => e.id === row.id),
      "a handled escalation stayed in the open queue").toBeFalsy();
  });

  test("acking a record that is not yours is a 404, not a 403", async ({ request }) => {
    /* The same answer as an unknown id, deliberately: a 403 would confirm the record
       exists, so an operator could enumerate another tenant's ids by watching the code. */
    const ops = await token(request, "ops");
    const r = await request.post(`${urls.engine}/v1/safety/escalations/no-such-id/ack`,
      { headers: auth(ops) });
    expect(r.status()).toBe(404);
  });

  test("an HR admin cannot reach the ack route", async ({ request }) => {
    /* The queue names which *employees* hit the crisis screen, so their own HR must not
       reach it — that is not "counts, never content", it is worse, because the count is a
       person. The read is already gated; the write must be too. */
    const hr = await token(request, "hr");
    const r = await request.post(`${urls.engine}/v1/safety/escalations/anything/ack`,
      { headers: auth(hr) });
    expect(r.status(), "an org_admin could resolve their employees' crisis records").toBe(403);
  });

  test("the console renders the queue and its open/resolved filter", async ({ page }) => {
    await signIn(page, urls.admin, "ops");
    await page.getByRole("button", { name: "Safety", exact: true }).click();

    await expect(page.getByRole("heading", { name: /Safety queue/ })).toBeVisible();
    // The filter must actually be a control, not three unstyled buttons: `.seg` was
    // referenced by the markup before it existed in globals.css.
    const seg = page.locator(".seg");
    await expect(seg).toBeVisible();
    await expect(seg.getByRole("button", { name: "open", exact: true })).toHaveClass(/active/);

    await seg.getByRole("button", { name: "all", exact: true }).click();
    await expect(seg.getByRole("button", { name: "all", exact: true })).toHaveClass(/active/);

    // The one column that must never exist here (CLAUDE.md rule 5).
    await expect(page.getByRole("columnheader", { name: /excerpt|message|content/i })).toHaveCount(0);
  });
});
