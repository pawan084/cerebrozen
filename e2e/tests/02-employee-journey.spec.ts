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

  test("a crisis turn surfaces helplines in the UI, not just an ordinary reply", async ({ page, request }) => {
    /* The gap this closes: the engine screens every turn before any model call and, on a
       crisis, takes over deterministically — but this client rendered the scripted safety
       message as an ordinary chat bubble, with no way to reach anyone. Pinned end-to-end
       because it spans three services: engine detects -> platform resolves the region ->
       engine serves that region's helplines -> client renders them. */
    const t = await token(request, "member");
    // Put this person in GB so the assertion below is about a REAL region resolution,
    // not the neutral fallback (which would pass even if the wiring were broken).
    await request.patch(`${urls.platform}/users/me`, { headers: auth(t), data: { region: "GB" } });

    await signIn(page, urls.app, "member");
    await page.waitForSelector(".sidebar", { timeout: 30_000 });
    await page.goto(`${urls.app}/coach`, { waitUntil: "domcontentloaded" });

    /* Send, and wait for the turn to actually FINISH.
       `send()` bails while `busy`, and the composer swallows the keypress silently — so a
       test that only waits for the first token races the stream, presses Enter into a busy
       composer, and then waits 40s for a panel whose message was never sent. So we wait on
       a real DOM state rather than sleeping.

       That wait used to be `button.send` + `toBeEnabled`, on the reasoning that the button
       is `disabled={busy || !draft.trim()}`. It stopped being true and the test went red:
       while streaming, the composer now swaps in a **Stop** button — which is also
       `button.send` (`className="send stop"`) and, being a stop control, is deliberately
       ENABLED. So the wait passed instantly mid-stream, Enter went into a busy composer,
       and `send()` dropped it. The product change was right; the assertion was reading the
       wrong thing. `:not(.stop)` is what "not busy" actually looks like now. */
    const say = async (text: string) => {
      await page.locator("textarea").fill(text);
      // The Send button only EXISTS when !busy (busy renders Stop in its place), so this
      // waits out the previous turn. Enabled-ness alone would prove nothing: Stop is
      // enabled too, and an empty draft disables Send.
      await expect(page.locator("button.send:not(.stop)"), "the previous turn never finished")
        .toBeEnabled({ timeout: 40_000 });
      await page.keyboard.press("Enter");
      // send() clears the draft: proof the keypress was accepted rather than swallowed.
      await expect(page.locator("textarea"), "the composer swallowed the message")
        .toHaveValue("");
    };

    // An ordinary turn must NOT cry wolf.
    await say("I keep putting off a hard conversation with my manager.");
    await expect(page.locator(".coach .bubble, .row.coach .bubble").first()).toBeVisible({ timeout: 40_000 });
    await expect(page.locator(".crisis"), "an ordinary turn raised the crisis panel").toHaveCount(0);

    await say("I want to kill myself");
    const panel = page.locator(".crisis");
    await expect(panel, "the crisis screen fired and the UI said nothing").toBeVisible({ timeout: 40_000 });
    // role=alert: a screen reader must announce this the moment it appears.
    await expect(panel).toHaveAttribute("role", "alert");

    const hrefs = await page.locator(".crisis-lines a").evaluateAll((els) =>
      els.map((e) => e.getAttribute("href") ?? ""));
    expect(hrefs, "GB got no local line — the region never reached the engine").toContain("tel:116123");
    expect(hrefs).toContain("https://findahelpline.com");
    expect(hrefs.join(","), "another country's number reached a GB user").not.toContain("14416");
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
