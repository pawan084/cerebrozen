import { test, expect, Page } from "@playwright/test";

const ADMIN = process.env.ADMIN_URL || "http://admin:3001";
const API = process.env.API_URL || "http://api:8000";

async function login(page: Page) {
  await page.goto(ADMIN, { waitUntil: "networkidle" });
  // Type the seeded creds explicitly — production builds no longer pre-fill them.
  await page.locator('input[type="email"]').fill("admin@cerebro.app");
  await page.locator('input[type="password"]').fill("admin12345");
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible({ timeout: 20_000 });
}

function nav(page: Page, label: string) {
  return page.locator(".navitem", { hasText: label });
}

test.describe("Admin dashboard", () => {
  test.beforeEach(async ({ page }) => login(page));

  test("overview shows live stat cards", async ({ page }) => {
    await expect(page.locator(".stat")).toHaveCount(5);
    await expect(page.locator(".stat .n").first()).toBeVisible();
  });

  test("users tab finds the seeded admin via search", async ({ page }) => {
    await nav(page, "Users").click();
    await page.getByPlaceholder(/Search by email/).fill("admin@cerebro.app");
    await expect(page.locator("tr", { hasText: "admin@cerebro.app" })).toBeVisible({ timeout: 10_000 });
  });

  test("analytics tab shows first-party aggregates", async ({ page }) => {
    await nav(page, "Analytics").click();
    await expect(page.getByText("Active today")).toBeVisible();
    await expect(page.getByRole("heading", { name: /Activation funnel/ })).toBeVisible();
    await expect(page.getByRole("heading", { name: /Retention/ })).toBeVisible();
  });

  test("user details show counts, never content", async ({ page }) => {
    await nav(page, "Users").click();
    await page.getByPlaceholder(/Search by email/).fill("admin@cerebro.app");
    await page
      .locator("tr", { hasText: "admin@cerebro.app" })
      .getByRole("button", { name: "Details" })
      .click();
    await expect(page.getByRole("heading", { name: "Account details" })).toBeVisible();
    await expect(page.getByText(/moods ·/)).toBeVisible();
    await expect(page.getByText(/contents never leave/)).toBeVisible();
  });

  test("content can be created, edited and deleted", async ({ page }) => {
    // Real narration generation (keyed dev machine) takes ~30s on its own, and the
    // W17 day-guide round-trip added steps — the default 30s test budget is too tight.
    test.setTimeout(90_000);
    await nav(page, "Content").click();
    await page.getByRole("button", { name: /new item/i }).click();
    const title = `E2E item ${Date.now()}`;
    const script = "Settle in and let the day soften.";
    await page.locator(".cform input[type=text]").first().fill(title);
    await page.locator(".cform textarea").fill(script);
    await page.getByRole("button", { name: /create item/i }).click();

    const row = page.locator("tr", { hasText: title });
    await expect(row).toBeVisible();

    // Full edit: open the pre-filled form, rename, save, see the new title.
    // The narration script must round-trip through the pre-fill.
    await row.getByRole("button", { name: "Edit" }).click();
    await expect(page.locator(".cform textarea")).toHaveValue(script);
    const edited = `${title} edited`;
    await page.locator(".cform input[type=text]").first().fill(edited);
    // W17: program day guides are edited in the same form and saved by the same PATCH.
    await page.getByRole("button", { name: "+ Add day" }).click();
    await page.getByPlaceholder("Day title").fill("Day 1 — Arrive");
    await page.getByPlaceholder(/What this day asks/).fill("Settle in tonight.");
    await page.getByRole("button", { name: /save changes/i }).click();
    const editedRow = page.locator("tr", { hasText: edited });
    await expect(editedRow).toBeVisible();

    // The saved guide round-trips through the pre-filled form.
    await editedRow.getByRole("button", { name: "Edit" }).click();
    await expect(page.getByPlaceholder("Day title")).toHaveValue("Day 1 — Arrive");
    await expect(page.getByPlaceholder(/What this day asks/)).toHaveValue("Settle in tonight.");
    await page.getByRole("button", { name: "Close", exact: true }).click();

    // Scripted items offer narration generation. The e2e api inherits
    // backend/.env, so a keyed dev machine really generates (row gains the
    // `narrated` tag) while keyless CI surfaces the honest 503 copy — both
    // outcomes are correct; a crash or silence is not.
    await editedRow.getByRole("button", { name: "Generate audio" }).click();
    await expect(
      editedRow.getByText("narrated", { exact: true }).or(page.getByText(/isn't configured/i)),
    ).toBeVisible({ timeout: 30_000 });

    // Deleting content is a two-step confirm (destructive = two-step + Danger).
    await editedRow.getByRole("button", { name: "Delete", exact: true }).click();
    await editedRow.getByRole("button", { name: "Yes, delete" }).click();
    await expect(page.locator("tr", { hasText: edited })).toHaveCount(0);
  });

  test("prompt registry: edit goes live as a version, revert restores the code default", async ({ page }) => {
    await nav(page, "Prompts").click();
    await expect(page.getByRole("heading", { name: "Prompt registry" })).toBeVisible();
    // The four production prompts register at import time — served from code.
    const card = page.locator(".card", { hasText: "assessment_topics" });
    await expect(card.getByText("code default")).toBeVisible();

    // Save an override: becomes v1 and active.
    await card.getByRole("button", { name: "Edit" }).click();
    await card.getByLabel("Template for assessment_topics").fill(`E2E registry prompt ${Date.now()}`);
    await card.getByRole("button", { name: /save as new version/i }).click();
    await expect(card.getByText(/v\d+ active/)).toBeVisible();

    // Revert: the code default serves again; history stays listed.
    await card.getByRole("button", { name: /revert to code default/i }).click();
    await expect(card.getByText("code default")).toBeVisible();
    await expect(card.locator("tr", { hasText: "v1" }).first()).toBeVisible();
  });

  test("nudges can be authored for all active users", async ({ page }) => {
    await nav(page, "Nudges").click();
    const title = `E2E announcement ${Date.now()}`;
    await page.locator(".cform input").first().fill(title);
    await page.locator(".cform input").nth(1).fill("Something gentle is new.");
    await page.getByRole("button", { name: /queue for all active users/i }).click();
    await expect(page.getByText(/Queued for \d+ user/)).toBeVisible();
    await expect(page.locator("tr", { hasText: title }).first()).toBeVisible();
  });

  test("safety review queue renders flagged events", async ({ page }) => {
    await nav(page, "Safety").click();
    await expect(page.getByRole("heading", { name: "Safety review" })).toBeVisible();
  });

  test("waitlist tab renders", async ({ page }) => {
    await nav(page, "Waitlist").click();
    await expect(page.getByText(/signups from the landing/i)).toBeVisible();
  });

  // ── Session, tabs and sign-out ────────────────────────────────────────
  // These exist because the nine tests above could all pass while the session
  // mechanism was completely broken. Every one of them signs in fresh, and the
  // access token lives in memory — so within a single test the refresh
  // credential is never needed, and a cross-origin Set-Cookie that the browser
  // silently dropped would look exactly like success.

  // ── Media catalogue ─────────────────────────────────────────────────────
  //
  // The tab had no test at all, and it is the pipeline that decides whether a
  // premium narration is a real file or silence: `services/media.playback_url`
  // hands back "" for an un-entitled item, and every `ambience.*` key still
  // ships with an empty url, which is why the web mixer had to synthesise its
  // four layers. What an operator uploads here is what clients play.

  test("uploading an asset fills a key, and clearing it hands the key back", async ({ page, request }) => {
    await nav(page, "Media").click();
    await expect(page.getByRole("heading", { name: "Media catalogue" })).toBeVisible();

    // Work on a key nothing else depends on being empty, and put it back.
    const row = page.locator("tr", { hasText: "ambience.wind" }).first();
    await expect(row).toBeVisible({ timeout: 10_000 });

    // A tiny but REAL file: the endpoint reads the upload, so an empty buffer
    // would test the form and not the pipeline.
    await row.locator('input[type="file"]').setInputFiles({
      name: "wind.mp3",
      mimeType: "audio/mpeg",
      // Content is arbitrary on purpose: the endpoint validates the file
      // EXTENSION and rejects an empty body, and checks nothing else - so this
      // is a real non-empty upload without smuggling binary into a test file.
      buffer: Buffer.from("e2e-media-fixture".repeat(64)),
    });
    await expect(page.getByText(/Uploaded wind\.mp3/)).toBeVisible({ timeout: 20_000 });

    // The catalogue is what CLIENTS read, so assert there rather than on the
    // row: this endpoint is public and is the one the app actually calls.
    const filled = await (await request.get(`${API}/media/catalog?kind=ambience`)).json();
    const wind = filled.find((a: any) => a.key === "ambience.wind");
    expect(wind?.url, "the key still has no url after an upload").toBeTruthy();

    // Clear points the key back at nothing — deliberately not a delete, since
    // removing the row would remove the KEY and the clients' fallback with it.
    await row.getByRole("button", { name: "Clear" }).click();
    // The label flips Replace -> Upload when the key empties. Asserting on the
    // hidden <input> instead never passes: it lives inside a <label class="btn">
    // and is hidden by design.
    await expect(row.getByText("Upload", { exact: true })).toBeVisible({ timeout: 15_000 });

    const cleared = await (await request.get(`${API}/media/catalog?kind=ambience`)).json();
    const after = cleared.find((a: any) => a.key === "ambience.wind");
    expect(after, "Clear removed the key itself — clients lose their fallback").toBeTruthy();
    expect(after.url, "the key still points at an asset after Clear").toBeFalsy();
  });

  // ── Revoking access ─────────────────────────────────────────────────────

  test("disabling an account needs a reason, and actually locks the person out", async ({ page, request }) => {
    // The one admin action that changes what a real person can do. The dialog
    // promises "They'll be signed out and locked out" — this proves the second
    // half, which nothing did: the route was named in no test at all.
    const email = `e2e-disable-${Date.now()}@cerebro.app`;
    const password = "lock-me-out-12345";
    const signup = await request.post(`${API}/auth/signup`, {
      data: { email, password, name: "E2E Disable" },
    });
    expect(signup.ok(), "could not create the account to disable").toBeTruthy();

    const canSignIn = async () =>
      (await request.post(`${API}/auth/login`, {
        form: { username: email, password },
      })).ok();
    expect(await canSignIn(), "the fresh account could not sign in to begin with").toBeTruthy();

    await nav(page, "Users").click();
    await page.getByPlaceholder(/Search by email/).fill(email);
    const row = page.locator("tr", { hasText: email });
    await expect(row).toBeVisible({ timeout: 10_000 });
    await row.getByRole("button", { name: "Disable" }).click();

    // Two steps and a reason, on purpose — revoking access should not be one
    // stray click. The confirm stays disabled until a reason is typed.
    await expect(page.getByText(/They'll be signed out and locked out/)).toBeVisible();
    const confirm = page.getByRole("button", { name: "Yes, disable access" });
    await expect(confirm, "the confirm was live before a reason was given").toBeDisabled();
    await page.getByLabel(/Why are you disabling this account/).fill("e2e verification");
    await expect(confirm).toBeEnabled();
    await confirm.click();

    await expect(row.getByRole("button", { name: "Enable" })).toBeVisible({ timeout: 15_000 });
    expect(
      await canSignIn(),
      "a disabled account can still sign in — the dialog promised a lock-out it did not deliver",
    ).toBeFalsy();

    // ...and enabling gives access back, so a mistake is recoverable.
    await row.getByRole("button", { name: "Enable" }).click();
    await expect(row.getByRole("button", { name: "Disable" })).toBeVisible({ timeout: 15_000 });
    expect(await canSignIn(), "re-enabling did not restore access").toBeTruthy();
  });

  test("the Oracle tab holds up with the agent switched off", async ({ page }) => {
    // The one tab of ten that no test opened. It is also the tab most likely to
    // break in exactly the configuration CI runs in: four separate admin reads
    // (/status, /pending, /audit, /agent-actions) on a stack with ORACLE_ENABLED
    // unset and no LLM key. "Everything degrades without keys" is a hard rule
    // here, and an operator page that errors when the feature is simply off is
    // the most ordinary way to break it.
    await nav(page, "Oracle").click();
    await expect(page.getByRole("heading", { name: "Oracle", exact: true })).toBeVisible();

    // Five stat cards, and the agent honestly reported as Off rather than blank.
    await expect(page.locator(".stat")).toHaveCount(5);
    await expect(page.locator(".stat", { hasText: "Agent" }).locator(".n")).toHaveText("Off");

    // Both tables render — the tool-acceptance stats (register E56: this data
    // existed and was reachable only by curl) and the audit trail.
    await expect(page.getByRole("heading", { name: /How often each tool is accepted/i })).toBeVisible();

    // Nothing errored. `Problem` is the shared error state for every failed
    // admin read, so its absence is the assertion that all four calls answered
    // — a per-panel check would miss whichever one regressed.
    await expect(page.locator(".state")).toHaveCount(0);

    // The promise printed on the page itself: arguments are recorded by name
    // only. If a table ever starts rendering values, this sentence becomes a
    // lie told to the operator reading it.
    await expect(page.getByText(/by name only — never their values/i)).toBeVisible();
  });

  test("a reload keeps the operator signed in", async ({ page }) => {
    // The refresh token moved from localStorage to an httpOnly cookie, which
    // means the browser holds the only copy and the console cannot read it.
    // A reload is the sole path that proves the cookie was accepted, is sent
    // back, and rotates into a working access token — the one thing the
    // backend's own tests cannot show, because they never involve a browser.
    await page.reload({ waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("button", { name: "Sign in" })).toHaveCount(0);

    // And the credential is genuinely out of reach of any script on the page —
    // which is the entire point of moving it.
    const readable = await page.evaluate(() =>
      JSON.stringify({ ls: { ...window.localStorage }, cookie: document.cookie }),
    );
    expect(readable).not.toContain("cerebro_refresh");
    expect(readable.toLowerCase()).not.toContain("refresh_token");
  });

  test("a tab is a real URL — reload and deep link both land on it", async ({ page }) => {
    await nav(page, "Safety").click();
    await expect(page).toHaveURL(/#safety$/);

    // Refreshing mid-triage used to drop the operator back on Overview.
    await page.reload({ waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Safety review" })).toBeVisible({ timeout: 20_000 });

    // A colleague pasting the link lands where they were sent.
    await page.goto(`${ADMIN}/#waitlist`, { waitUntil: "networkidle" });
    await expect(page.getByText(/signups from the landing/i)).toBeVisible({ timeout: 20_000 });

    // Nonsense in the fragment must not render an empty shell.
    await page.goto(`${ADMIN}/#not-a-tab`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Overview" })).toBeVisible({ timeout: 20_000 });
  });

  test("signing out ends the session for real", async ({ page }) => {
    await page.locator(".navitem", { hasText: "Sign out" }).click();
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible({ timeout: 20_000 });

    // The half that matters: a reload must NOT walk back in. Clearing the
    // client while leaving a valid cookie on the machine would look identical
    // to signing out, right up until the next person opened the browser.
    await page.reload({ waitUntil: "networkidle" });
    await expect(page.getByRole("button", { name: "Sign in" })).toBeVisible({ timeout: 20_000 });
    await expect(page.getByRole("heading", { name: "Overview" })).toHaveCount(0);
  });
});
