import { test, expect, Page } from "@playwright/test";

const APP = process.env.APP_URL || "http://app:3002";

// Guided imagery ships eight prompts; skipping through all of them lands on
// the closing card (mirrors LINES in app/(authed)/games/imagery/page.tsx).
const LINES_IN_IMAGERY = 8;

// Sidebar nav items are links; scope to .sidebar so a label like "Journal"
// hits the nav rather than a same-named card elsewhere on the page.
function nav(page: Page, label: string) {
  return page.locator(".sidebar").getByRole("link", { name: label, exact: true });
}

// Create an account the way a person now does it: tick the 18+ attest, then
// walk the funnel's post-signup steps (consent — all-off is the honest default
// — and notifications). Fresh accounts route through consent since 2026-08-03;
// landing straight on /home without seeing it was the bug.
async function createAccount(page: Page, email: string, opts: { keepTour?: boolean } = {}) {
  await page.goto(`${APP}/signin`, { waitUntil: "networkidle" });
  await page.getByRole("tab", { name: "Create account" }).click();
  await page.locator('input[autocomplete="name"]').fill("E2E");
  await page.locator('input[type="email"]').fill(email);
  await page.locator('input[type="password"]').fill("password123");
  await page.locator(".check-line input").check();
  await page.getByRole("button", { name: "Create my account" }).click();
  // Funnel resumes at the consent step for the fresh session.
  await expect(page).toHaveURL(/\/onboarding/, { timeout: 20_000 });
  await page.getByRole("button", { name: "Continue", exact: true }).click();
  await page.getByRole("button", { name: "Enter CereBro" }).click();
  await expect(page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }))
    .toBeVisible({ timeout: 20_000 });
  // The first-run tour is a modal — it intercepts pointer events until
  // dismissed, so callers that click anything else must clear it first.
  // The main journey keeps it: walking the tour is part of that test.
  if (!opts.keepTour) await page.getByRole("button", { name: "Skip" }).click().catch(() => {});
}

// Leave the session signed out again (sidebar icon button).
async function signOut(page: Page) {
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page).toHaveURL(/\/signin/, { timeout: 10_000 });
}

test.describe("Web app (authenticated client)", () => {
  // One long journey across the dashboard — needs more than the 30s default
  // (chat may wait on a live LLM when the stack runs with real keys).
  test("signup → check-in → journal → sleep → talk → insights → account → reload", async ({ page }) => {
    test.setTimeout(150_000);
    // Fresh account per run (the e2e stack seeds no app users).
    const email = `e2e-app-${Date.now()}@test.app`;
    await createAccount(page, email, { keepTour: true });

    // First-run guided tour (ref GUIDED TOUR OVERLAY): walk one stop, then
    // skip — it must dismiss and never come back this session.
    await expect(page.getByText(/Guided tour · 1 of 4/)).toBeVisible();
    await page.getByRole("button", { name: "Next" }).click();
    await expect(page.getByText("Your plan adapts")).toBeVisible();
    await page.getByRole("button", { name: "Skip" }).click();
    await expect(page.getByText(/Guided tour/)).toBeHidden();

    // Home check-in: tapping an emoji posts the mood and updates the note.
    await page.getByRole("button", { name: "Anxious" }).click();
    await expect(page.getByText(/Loud thoughts are real/)).toBeVisible();

    // TOD-01: the hero names the next step at full volume, and the full day is
    // folded behind "Your day" — so open it before asserting the rows.
    await expect(page.getByText("Your next helpful step")).toBeVisible();
    await page.getByText("Your day", { exact: true }).click();

    // Today's plan renders the served agentic plan (auto-generated on first
    // /plans/active — titles vary when a live LLM writes it, so assert shape:
    // at least two real step rows; the error fallback renders exactly one).
    await expect(page.locator(".plan-row").nth(1)).toBeVisible({ timeout: 20_000 });
    await expect(page.locator(".plan-row .plan-start").first()).toHaveText("START");

    // Journal: open the composer, save, the entry lands in Recent entries.
    await nav(page, "Journal").click();
    await page.getByRole("button", { name: /Write an entry/ }).click();
    await page.locator('input[maxlength="120"]').fill("Meeting pressure");
    await page.locator("textarea").fill("A bit stressed about tomorrow.");
    await page.getByRole("button", { name: "Save entry" }).click();
    await expect(page.locator(".entry-card", { hasText: "A bit stressed" })).toBeVisible();

    // Sleep: the morning check-in still writes server-side, and the CBT-I
    // stimulus-control education cards render (improvement framing, W12).
    //
    // SLP-01 reordered this screen: tonight leads, and the diary, the sounds
    // and last night's check-in fold below it — so the folds are opened here.
    // The wind-down ritual is the one thing that stays at full volume.
    await nav(page, "Sleep").click();
    await expect(page.getByRole("heading", { name: /Tonight's wind-down ritual/ })).toBeVisible();
    await page.getByText("Your rhythm", { exact: true }).click();
    await expect(page.getByRole("heading", { name: "Bed is for sleep" })).toBeVisible();
    await page.getByText("Last night", { exact: true }).click();
    await page.getByRole("radio", { name: "Good" }).click();
    await page.getByRole("button", { name: "Save check-in" }).click();
    await expect(page.getByText(/Saved — one entry per morning/)).toBeVisible();

    // Talk: the landing opens the chat; an assistant bubble must land (Oracle
    // stream with a key, deterministic /chat fallback otherwise).
    await nav(page, "Talk").click();
    await page.getByRole("button", { name: "Start talking" }).click();
    await page.getByLabel("Message").fill("I keep overthinking tomorrow's meeting.");
    await page.getByRole("button", { name: "Send" }).click();
    await expect(page.locator(".msg.user").first()).toBeVisible();
    await expect(page.locator(".msg.ai").first()).toBeVisible({ timeout: 60_000 });

    // Insights dashboard renders (chart + patterns).
    await nav(page, "Insights").click();
    await expect(page.getByRole("heading", { name: "Mood, last 14 days" })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText("Gentle patterns")).toBeVisible();

    // Programs: renders the real seeded catalogue (not a hardcoded list).
    await nav(page, "Programmes").click();
    await expect(page.getByRole("heading", { name: "Guided journeys" })).toBeVisible({ timeout: 10_000 });
    // The featured hero (h2) mirrors the first program's title, so target the
    // grid card's h3 specifically to keep the locator unambiguous.
    await expect(
      page.getByRole("heading", { level: 3, name: "Ease work stress" }),
    ).toBeVisible({ timeout: 10_000 });

    // Toolkit (still at /games): box breathing is genuinely playable — Start
    // flips to Stop and the phase label appears (not a dead button); the
    // 5-4-3-2-1 grounding stepper advances.
    await nav(page, "Toolkit").click();
    await expect(page.getByRole("heading", { name: "Box breathing" })).toBeVisible();
    await page.getByRole("button", { name: "Start", exact: true }).click();
    await expect(page.getByRole("button", { name: "Stop" })).toBeVisible();
    await expect(page.getByText("Breathe in")).toBeVisible();
    await expect(page.getByRole("heading", { name: "5 things you can see" })).toBeVisible();
    await page.getByRole("button", { name: "Next", exact: true }).click();
    await expect(page.getByRole("heading", { name: "4 things you can feel" })).toBeVisible();

    // Ritual builder: the cue (the part with the evidence) round-trips into the
    // plan sentence, reordering actually reorders the run, and the routine runs
    // to a finish that repeats the cue back.
    await page.getByRole("link", { name: "Build yours" }).click();
    await expect(
      page.getByRole("heading", { name: "Attach it to something you already do" }),
    ).toBeVisible();
    await page.getByRole("button", { name: "close my laptop" }).click();
    await expect(page.getByText("After I close my laptop, I'll run this.")).toBeVisible();
    await page.getByRole("checkbox", { name: "One intention" }).check();
    await page.getByRole("checkbox", { name: "Three good things" }).check();
    await expect(page.getByText("2 steps · about 3 min")).toBeVisible();
    await page.getByRole("button", { name: "Move Three good things earlier" }).click();
    await expect(page.getByText("1. Three good things")).toBeVisible();
    await page.getByRole("button", { name: "Save it" }).click();
    await expect(page.getByRole("button", { name: "Saved" })).toBeVisible();
    await page.getByRole("button", { name: "Start the ritual" }).click();
    await expect(page.getByRole("heading", { name: "What went right?" })).toBeVisible();
    await page.getByRole("button", { name: "Skip this one" }).click();
    await expect(page.getByRole("heading", { name: "What is this next stretch for?" })).toBeVisible();
    await page.getByRole("button", { name: "Finish" }).click();
    await expect(page.getByText("That's the ritual.")).toBeVisible();
    await expect(page.getByText("Next time: after you close my laptop.")).toBeVisible();

    // Guided imagery: the caution is on the way IN (not after something goes
    // wrong), and the prompts advance to a close.
    await page.getByRole("link", { name: "Back to the Toolkit" }).click();
    await page.getByRole("link", { name: "Start the visualization" }).click();
    await expect(page.getByText("If it stops feeling calm, stop.")).toBeVisible();
    await page.getByRole("button", { name: "Begin" }).click();
    await expect(page.getByText(/Let your shoulders drop/)).toBeVisible();
    // Guarded rather than a flat 8 clicks: the 15s auto-advance can carry a
    // line while the runner is between clicks, and the control disappears on
    // the closing card.
    const skip = page.getByRole("button", { name: "Skip ahead" });
    for (let i = 0; i < LINES_IN_IMAGERY; i++) {
      if (!(await skip.isVisible())) break;
      await skip.click();
    }
    await expect(page.getByText("The place stays where it is.")).toBeVisible();

    // Plan + Library (were built but orphaned) are now reachable from the nav.
    await nav(page, "Plan").click();
    await expect(page.getByText(/Agentic plan/)).toBeVisible({ timeout: 15_000 });
    await nav(page, "Catalogue").click();
    await expect(page.getByRole("heading", { name: "Library", exact: true })).toBeVisible({ timeout: 10_000 });
    // Rails render the seeded catalogue, and every audio control that renders
    // must carry a real source — no fake/dead players (audio elements only
    // appear at all once narration has been generated, e.g. on keyed stacks).
    await expect(page.getByRole("heading", { name: "Wind down tonight" })).toBeVisible();
    await expect(page.locator("audio:not([src])")).toHaveCount(0);

    // Settings (account): consent toggle flips, enforced server-side.
    await nav(page, "Settings").click();
    // Safety: real human pathways render (Tele-MANAS leads), and the sidebar
    // carries a persistent Support door (crisis ≤2 clicks from anywhere).
    await expect(page.getByRole("heading", { name: "Talk to a human" })).toBeVisible({ timeout: 20_000 });
    // The door is the styled `.support-door`, not a plain nav row, so its
    // accessible name is "Support Real people, 24/7" — matched on the prefix
    // rather than exactly, because what this pins is that a Support door exists
    // in the sidebar, not how its label reads.
    await expect(
      page.locator(".sidebar").getByRole("link", { name: /^Support/ }),
    ).toBeVisible();
    const aiMemory = page.getByRole("switch", { name: "AI memory" });
    await expect(aiMemory).toBeVisible({ timeout: 20_000 });
    const before = await aiMemory.isChecked();
    await aiMemory.click();
    await expect(aiMemory).toBeChecked({ checked: !before });

    // Web Push degrades honestly: the e2e stack runs without VAPID keys, so
    // the browser-notifications toggle must be disabled with the "not
    // configured" note — never a dead-looking live control.
    const browserPush = page.getByRole("switch", { name: "Browser notifications" });
    await expect(browserPush).toBeVisible();
    await expect(browserPush).toBeDisabled();
    await expect(page.getByText(/aren't configured on this server yet/)).toBeVisible();

    // DPDP s.5(3): the consent notice re-renders in an Eighth-Schedule
    // language picked right on the notice (हिन्दी here), then back to English.
    await page.getByLabel("Notice language").selectOption("hi");
    await expect(page.getByText("मूड इतिहास")).toBeVisible();
    await expect(page.getByRole("switch", { name: "AI मेमोरी" })).toBeVisible();
    await page.getByLabel("Notice language").selectOption("en");
    await expect(page.getByText("Mood history")).toBeVisible();

    // Programs: enroll in a journey (ref "PROGRAM · DAY X OF Y") — the active
    // banner appears here and the journey card lands on Home.
    await nav(page, "Programmes").click();
    // Sleep Reset specifically, not .first(): it is the one seeded program that
    // ships day_guides, and the journey path only exists where a week does.
    // Enrolling in "Ease work stress" instead renders no path and no guide,
    // which is correct behaviour and tells us nothing.
    await page
      .locator(".program-card", { hasText: "Sleep Reset" })
      .getByRole("button", { name: "Start this journey" })
      .click();
    await expect(page.getByText(/Program · day 1 of 7/)).toBeVisible();

    // The journey path: the whole week, and none of it gated. Day 1 is today, so
    // it carries aria-current="step"; every other day must still be reachable —
    // that is the product rule, not a nicety, and a regression here would be a
    // lock quietly appearing on a wellness programme.
    await expect(page.getByText("Every day is open — read ahead, or go back. Nothing here locks."))
      .toBeVisible();
    await expect(page.getByRole("button", { name: "Day 1, today" })).toHaveAttribute(
      "aria-current",
      "step",
    );
    const laterDay = page.getByRole("button", { name: "Day 5, later in this program" });
    await expect(laterDay).toBeEnabled();
    await laterDay.click();
    await expect(page.getByText("Day 5 · coming up")).toBeVisible();
    // And back again — nothing one-way.
    await page.getByRole("button", { name: "Day 1, today" }).click();
    await expect(page.getByText("Day 1 · today")).toBeVisible();

    await nav(page, "Today").click();
    await expect(page.getByText(/Program · day 1 of 7/)).toBeVisible();

    // Pattern dashboard with AI MEMORY OFF must not sign the user out.
    // The backend answers 403 for the consent-gated memory route, and
    // authedFetch used to read any 403 as an expired session: visiting
    // /patterns silently destroyed the session of every memory-off user, and
    // buried the page's own explanation behind "unauthorized" (register D1).
    await page.goto(`${APP}/account`, { waitUntil: "networkidle" });
    const memorySwitch = page.getByRole("switch", { name: "AI memory" });
    await expect(memorySwitch).toBeVisible({ timeout: 20_000 });
    if (await memorySwitch.isChecked()) await memorySwitch.click();
    await expect(memorySwitch).not.toBeChecked();

    await page.goto(`${APP}/patterns`, { waitUntil: "networkidle" });
    await expect(page).toHaveURL(/\/patterns$/);
    // Writing a memory with the category off is the path the backend really
    // gates (`_memory_allowed` guards POST/PATCH/DELETE; the GET does not).
    // It must explain itself and leave the session intact — before the fix
    // authedFetch read that 403 as a dead session, so the user was signed out
    // and the page's own message could never appear.
    await page.getByLabel("Add a memory").fill("please remember this");
    await page.getByRole("button", { name: "Remember this" }).click();
    await expect(page.getByText(/is AI memory switched on/)).toBeVisible();
    await expect(page).toHaveURL(/\/patterns$/);

    // Back on, so the rest of the run sees the normal surface.
    await page.goto(`${APP}/account`, { waitUntil: "networkidle" });
    await page.getByRole("switch", { name: "AI memory" }).click();
    await expect(page.getByRole("switch", { name: "AI memory" })).toBeChecked();

    // Pattern dashboard: honest empty state for a fresh account, and the
    // delete-memory two-step actually round-trips.
    await page.goto(`${APP}/patterns`, { waitUntil: "networkidle" });
    await expect(page.getByText(/no guesses, ever/)).toBeVisible();
    await page.getByRole("button", { name: "Delete all memory" }).click();
    await page.getByRole("button", { name: "Click again to confirm" }).click();
    await expect(page.getByText(/Memory cleared/)).toBeVisible();

    // A reload drops the in-memory access token — refresh rotation restores it.
    // Reload on Home specifically: it fires several authed fetches at once, so a
    // regression in the deduped single-use-refresh handling (lib/api) would race,
    // clear the token, and bounce to /signin here.
    await nav(page, "Today").click();
    // The client-side navigation must land before the reload, or the reload
    // happens on /patterns and the whole point (reload ON Home) is lost.
    await expect(page).toHaveURL(/\/home$/);
    await page.reload({ waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }))
      .toBeVisible({ timeout: 20_000 });
    await expect(page).toHaveURL(/\/home$/);
  });

  // The crisis page must work signed-out with a dead API: it's static JSX,
  // Tele-MANAS first, numbers as real tel: links (never auto-called).
  test("crisis page is public and Tele-MANAS-first", async ({ page }) => {
    await page.goto(`${APP}/crisis`, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Urgent support" })).toBeVisible();
    // The three India lines lead, and Tele-MANAS is first of them.
    await expect(page.getByRole("link", { name: /Tele-MANAS/ })).toHaveAttribute("href", "tel:14416");
    await expect(page.getByRole("link", { name: /KIRAN/ })).toBeVisible();
    // SAF-01: "Find a helpline" moved into the outside-India section — behind a
    // native <details> — so it is no longer the India-scoped /in path, and the
    // section has to be opened before the link is in the a11y tree.
    await page.getByText("Outside India", { exact: true }).click();
    await expect(page.getByRole("link", { name: /Find a helpline/ })).toHaveAttribute(
      "href", "https://findahelpline.com");
  });

  // SAF-01's whole point: a badge means a named source AND a check date. The
  // ref/ audit found the opposite bug — Indian numbers badged "Verified" for
  // every country — so the unverified regions must SAY they are unverified.
  test("crisis page badges only what it has actually checked", async ({ page }) => {
    await page.goto(`${APP}/crisis`, { waitUntil: "networkidle" });
    await expect(page.getByText(/India · verified/i)).toBeVisible();
    await expect(page.getByText(/checked 5 August 2026/i).first()).toBeVisible();
    // Other regions are in the markup behind a native <details>; open it.
    await page.getByText("Outside India", { exact: true }).click();
    await expect(page.getByText(/United States · not verified yet/i)).toBeVisible();
    await expect(page.getByText(/we have not verified these/i)).toBeVisible();
  });

  // The page must survive with no JavaScript at all — an emergency number that
  // depends on a bundle loading is not an emergency number. The design mock
  // used a useState region selector; this is why it did not graduate as-is.
  test("crisis page renders every number without JavaScript", async ({ browser }) => {
    const ctx = await browser.newContext({ javaScriptEnabled: false });
    const page = await ctx.newPage();
    await page.goto(`${APP}/crisis`, { waitUntil: "domcontentloaded" });
    await expect(page.getByRole("link", { name: /Tele-MANAS/ })).toHaveAttribute("href", "tel:14416");
    // <details> is native, so the other regions still open with no script.
    await page.getByText("Outside India", { exact: true }).click();
    await expect(page.getByText(/United States · not verified yet/i)).toBeVisible();
    await ctx.close();
  });

  // The landing deep-links into app screens ("Open Sleep →"), which only works
  // if a signed-out visitor is carried through sign-in back to what they
  // clicked. The carrying is a SIGN-IN promise: a fresh signup goes through
  // the consent step instead (a deliberate 2026-08-03 change), so this test
  // creates the account first, signs out, and returns as the deep link does.
  test("a signed-out deep link returns to where it pointed after sign-in", async ({ page }) => {
    test.setTimeout(120_000);
    const email = `e2e-next-${Date.now()}@test.app`;
    await createAccount(page, email);
    await signOut(page);

    await page.goto(`${APP}/sleep`, { waitUntil: "networkidle" });
    await expect(page).toHaveURL(/\/signin\?next=%2Fsleep$/);

    await page.locator('input[type="email"]').fill(email);
    await page.locator('input[type="password"]').fill("password123");
    await page.getByRole("button", { name: "Continue with email" }).click();

    await expect(page).toHaveURL(/\/sleep$/, { timeout: 20_000 });
  });

  // `next` is attacker-controlled, so it is an allow-list: same-origin absolute
  // paths only. A protocol-relative URL must not become an open redirect.
  test("refuses an off-origin next and falls back to Home", async ({ page }) => {
    test.setTimeout(120_000);
    const email = `e2e-evil-${Date.now()}@test.app`;
    await createAccount(page, email);
    await signOut(page);

    await page.goto(`${APP}/signin?next=//example.com/evil`, { waitUntil: "networkidle" });
    await page.locator('input[type="email"]').fill(email);
    await page.locator('input[type="password"]').fill("password123");
    await page.getByRole("button", { name: "Continue with email" }).click();

    await expect(page).toHaveURL(new RegExp(`^${APP}/home`), { timeout: 20_000 });
  });
  test("a check-in made offline is kept, then sent when the network returns", async ({ page, context }) => {
    // The gap this closes: until the outbox existed, a mood tapped with no
    // signal threw, showed "we couldn't save that", and was simply gone —
    // while the Android client had kept the same tap since the metro problem.
    test.setTimeout(90_000);
    const email = `e2e-outbox-${Date.now()}@test.app`;
    await createAccount(page, email);

    // Load the page first, THEN pull the network — going offline before the
    // navigation would just fail to fetch the document, which tests nothing.
    await page.goto(`${APP}/checkin`, { waitUntil: "networkidle" });
    await context.setOffline(true);
    await page.getByRole("button", { name: /Good|Anxious|Low|Tired/ }).first().click();
    await page.getByRole("button", { name: "Save this check-in" }).click();

    // Kept, and said so — not "saved", and not an error either.
    await expect(page.getByText("It will sync the next time you are online.")).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.locator(".outbox-bar")).toContainText("waiting to send");

    // Back online: the queue drains itself and the bar goes away on its own.
    await context.setOffline(false);
    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    await expect(page.locator(".outbox-bar")).toHaveCount(0, { timeout: 20_000 });

    // And the entry really did reach the SERVER, rather than the bar merely
    // clearing itself: Today's presence rail is computed from /users/me/streak,
    // which only counts what the API actually holds.
    await page.goto(`${APP}/home`, { waitUntil: "networkidle" });
    await expect(page.locator(".rail-big")).toContainText("day present", { timeout: 20_000 });
    await expect(page.locator(".rail-big b")).not.toHaveText("0");
  });

  test("the new rooms render, and none of them is a door nobody can find", async ({ page }) => {
    // The orphan trap, ported from Android: the Practice library existed as a
    // route with no way in, so it was dead product nobody could reach. A page
    // that renders is only half of shipped — something has to link to it.
    test.setTimeout(120_000);
    const email = `e2e-rooms-${Date.now()}@test.app`;
    await createAccount(page, email);

    const ROOMS = [
      { path: "/sleep/insights", heading: /Look for the pattern/, from: "/sleep" },
      { path: "/sleep/mixer", heading: /Just rain|Silent|Mostly/, from: "/sleep" },
      { path: "/insights/trends", heading: /How the days read|Reading your logs/, from: "/insights" },
      { path: "/games/bodyscan", heading: /Body Scan/, from: "/games" },
      { path: "/library/cbti", heading: /CBT-I Overview/, from: "/library" },
      { path: "/library/mbct", heading: /MBCT Overview/, from: "/library" },
    ];

    for (const room of ROOMS) {
      await page.goto(`${APP}${room.path}`, { waitUntil: "networkidle" });
      await expect(page.getByText(room.heading).first(), `${room.path} renders`)
        .toBeVisible({ timeout: 20_000 });

      // …and its parent actually offers a way in.
      await page.goto(`${APP}${room.from}`, { waitUntil: "networkidle" });
      await expect(
        page.locator(`a[href="${room.path}"]`).first(),
        `${room.from} has no link to ${room.path} — that is an orphan route`,
      ).toBeVisible({ timeout: 20_000 });
    }
  });

  test("every chip is a tap target, not a decoration", async ({ page }) => {
    // `.chip` and `.ui-chip` are buttons everywhere they appear — chat retry
    // and suggestions, the ritual cue picker, journal tag filters, the
    // appearance picker. They stood 31px and 42px tall against the 48px floor
    // the rest of globals.css keeps, which made the easiest things to mis-tap
    // the ones people reach for while distracted.
    const email = `e2e-tap-${Date.now()}@test.app`;
    await createAccount(page, email);

    // Two screens that render these unconditionally: the ritual cue picker
    // (`.chip`, from a static list) and the appearance picker (`.ui-chip`).
    for (const path of ["/games/ritual", "/account"]) {
      await page.goto(`${APP}${path}`, { waitUntil: "networkidle" });
      const chips = page.locator(".chip:visible, .ui-chip:visible");
      const count = await chips.count();
      expect(count, `${path} rendered no chips — this check would pass vacuously`)
        .toBeGreaterThan(0);
      for (let i = 0; i < count; i++) {
        const chip = chips.nth(i);
        const box = await chip.boundingBox();
        expect(box!.height, `${path} chip "${await chip.innerText()}"`).toBeGreaterThanOrEqual(48);
      }
    }
  });
});
