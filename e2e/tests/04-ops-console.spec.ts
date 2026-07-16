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
});
