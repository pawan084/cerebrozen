import { expect, test } from "@playwright/test";
import { urls } from "../playwright.config";
import { auth, signIn, token } from "./helpers";

/* The employee journey, end to end: sign in → talk to the coach over SSE → the commitment
 * and the journal land → and none of it is reachable from the org's database.
 *
 * The stack runs the MOCK LLM provider (zero keys — docs/ENGINEERING.md rule 3), so these
 * assert the PLUMBING, not coaching quality: the SSE vocabulary on the wire, which service
 * answers, and the privacy boundary. Quality is the evals harness's job, off the merge path.
 */

test.describe("employee journey", () => {
  test("the app signs a member in and shows them their own home", async ({ page }) => {
    await signIn(page, urls.app, "member");
    // The auth gate replaced by the real shell = the token round-tripped platform → client.
    await expect(page.locator(".sidebar")).toBeVisible({ timeout: 30_000 });
    await expect(page.locator('input[name="password"]')).toHaveCount(0);
  });

  test("the coach streams a reply using the agreed SSE vocabulary", async ({ request }) => {
    /* THE contract this suite exists to gate. ENGINEERING.md: an SSE-vocabulary change
       "additionally require[s] an e2e run against the composed stack" — this is that run.
       ARCHITECTURE.md pins the event names to `status`/`node`/`token`/`done`, consumed by
       Android and the web app; a rename needs a simultaneous client release. Reading the
       raw stream (not a client's parse of it) is what makes that enforceable. */
    const t = await token(request, "member");
    // Exactly the call apps/app/lib/coach.ts makes: start?stream=true with {text}.
    const start = await request.post(`${urls.engine}/v1/sessions/start?stream=true`, {
      headers: { ...auth(t), Accept: "text/event-stream" },
      data: { text: "I keep putting off a hard conversation with my manager." },
      timeout: 60_000,
    });
    expect(start.ok(), `could not start a session: ${start.status()} ${await start.text()}`).toBeTruthy();

    /* Wire format, verified against the engine rather than assumed: frames are `data:`
       only — there is no SSE `event:` line — and the vocabulary rides in the JSON's
       `type` field. That is exactly how apps/app/lib/coach.ts reads it, so parsing it
       any other way here would test a stream no client consumes. */
    const body = await start.text();
    const frames = [...body.matchAll(/^data:\s*(.+)$/gm)].map((m) => JSON.parse(m[1]));
    expect(frames.length, `the stream carried no frames at all. body: ${body.slice(0, 300)}`).toBeGreaterThan(0);
    const events = frames.map((f) => f.type);

    // Every event name must be in the vocabulary. An unknown name means a client that
    // hasn't shipped support for it is being sent it — which is the whole reason the
    // vocabulary is a contract.
    const VOCABULARY = new Set(["status", "node", "token", "done", "error"]);
    for (const e of events) expect(VOCABULARY, `undeclared SSE event "${e}"`).toContain(e);
    expect(events, "a stream that never completes leaves a client spinning").toContain("done");
    expect(events).not.toContain("error");

    // The `done` frame carries the session_id the client stores for every later turn.
    const done = frames.find((f) => f.type === "done");
    expect(done.session_id, "done must carry the session_id — clients key every turn on it").toBeTruthy();
  });

  test("a journal entry is served by the ENGINE and is invisible to the platform", async ({ request }) => {
    /* "Counts, never content" as a property of the schema, asserted across the wire:
       the same bearer token that writes a journal to the engine must find nothing to read
       on the platform — the database an HR admin's token reaches. */
    const t = await token(request, "member");
    const wrote = await request.post(`${urls.engine}/v1/wellness/journal`, {
      headers: auth(t),
      data: { title: "e2e", body: "a private entry", tags: [], symbol: "book" },
    });
    // A consent refusal (403) is a legitimate answer — it is not a plumbing failure.
    expect([200, 201, 403]).toContain(wrote.status());

    const platformMe = await request.get(`${urls.platform}/users/me`, { headers: auth(t) });
    const text = JSON.stringify(await platformMe.json());
    expect(text, "journal content surfaced on the platform").not.toContain("a private entry");
    for (const leak of ["journal", "mood_history", "sleep"]) {
      expect(text.toLowerCase(), `the platform's /users/me exposes "${leak}"`).not.toContain(`"${leak}"`);
    }
  });

  test("crisis helplines come from the engine and are region-neutral when unknown", async ({ request }) => {
    // The client must never hold a country's numbers (ARCHITECTURE.md §Cross-stack
    // contracts). Pinned here because it is a *cross-service* promise: the engine owns the
    // directory, the platform resolves the region, the clients only render.
    const t = await token(request, "member");
    const unknown = await request.get(`${urls.engine}/v1/safety/helplines?region=ZZ`, { headers: auth(t) });
    expect(unknown.ok()).toBeTruthy();
    const rows = (await unknown.json()).helplines;
    expect(rows.length, "an empty crisis screen is the worst outcome available").toBeGreaterThan(0);
    expect(rows.map((h: { target: string }) => h.target)).toEqual(["https://findahelpline.com"]);

    const gb = await request.get(`${urls.engine}/v1/safety/helplines?region=GB`, { headers: auth(t) });
    const gbTargets = (await gb.json()).helplines.map((h: { target: string }) => h.target);
    expect(gbTargets).toContain("116123");
    expect(gbTargets.join(","), "another country's number leaked into GB").not.toContain("14416");
  });
});
