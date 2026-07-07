import { test, expect, Page } from "@playwright/test";

const APP = process.env.APP_URL || "http://app:3002";

// Sidebar nav items are links; scope to .sidebar so a label like "Journal"
// hits the nav rather than a same-named card elsewhere on the page.
function nav(page: Page, label: string) {
  return page.locator(".sidebar").getByRole("link", { name: label, exact: true });
}

test.describe("Web app (authenticated client)", () => {
  // One long journey across the dashboard — needs more than the 30s default
  // (chat may wait on a live LLM when the stack runs with real keys).
  test("signup → check-in → journal → sleep → talk → insights → account → reload", async ({ page }) => {
    test.setTimeout(150_000);
    // Fresh account per run (the e2e stack seeds no app users). /signup funnels
    // into onboarding; create the account via the AuthPanel "Create account" tab.
    const email = `e2e-app-${Date.now()}@test.app`;
    await page.goto(`${APP}/signin`, { waitUntil: "networkidle" });
    await page.getByRole("tab", { name: "Create account" }).click();
    await page.locator('input[autocomplete="name"]').fill("E2E");
    await page.locator('input[type="email"]').fill(email);
    await page.locator('input[type="password"]').fill("password123");
    await page.getByRole("button", { name: "Create my account" }).click();
    await expect(page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }))
      .toBeVisible({ timeout: 20_000 });

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

    // Sleep: the morning check-in still writes server-side.
    await nav(page, "Sleep").click();
    await page.getByRole("radio", { name: "Good" }).click();
    await page.getByRole("button", { name: "Save check-in" }).click();
    await expect(page.getByText(/Saved — one entry per morning/)).toBeVisible();

    // Talk: the landing opens the chat; an assistant bubble must land (Oracle
    // stream with a key, deterministic /chat fallback otherwise).
    await nav(page, "Talk").click();
    await page.getByRole("button", { name: "Type instead" }).click();
    await page.getByLabel("Message").fill("I keep overthinking tomorrow's meeting.");
    await page.getByRole("button", { name: "Send" }).click();
    await expect(page.locator(".msg.user").first()).toBeVisible();
    await expect(page.locator(".msg.ai").first()).toBeVisible({ timeout: 60_000 });

    // Insights dashboard renders (chart + patterns).
    await nav(page, "Insights").click();
    await expect(page.getByRole("heading", { name: "Mood, last 14 days" })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText("Gentle patterns")).toBeVisible();

    // Programs: renders the real seeded catalogue (not a hardcoded list).
    await nav(page, "Programs").click();
    await expect(page.getByRole("heading", { name: "Guided journeys" })).toBeVisible({ timeout: 10_000 });
    // The featured hero (h2) mirrors the first program's title, so target the
    // grid card's h3 specifically to keep the locator unambiguous.
    await expect(
      page.getByRole("heading", { level: 3, name: "Ease work stress" }),
    ).toBeVisible({ timeout: 10_000 });

    // Games: box breathing is genuinely playable — Start flips to Stop and the
    // phase label appears (proves the game actually runs, not a dead button).
    await nav(page, "Games").click();
    await expect(page.getByRole("heading", { name: "Box breathing" })).toBeVisible();
    await page.getByRole("button", { name: "Start" }).click();
    await expect(page.getByRole("button", { name: "Stop" })).toBeVisible();
    await expect(page.getByText("Breathe in")).toBeVisible();

    // Plan + Library (were built but orphaned) are now reachable from the nav.
    await nav(page, "Plan").click();
    await expect(page.getByText(/Agentic plan/)).toBeVisible({ timeout: 15_000 });
    await nav(page, "Library").click();
    await expect(page.getByRole("heading", { name: "Library", exact: true })).toBeVisible({ timeout: 10_000 });

    // Settings (account): consent toggle flips, enforced server-side.
    await nav(page, "Settings").click();
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
    await nav(page, "Programs").click();
    await page.getByRole("button", { name: "Start this journey" }).first().click();
    await expect(page.getByText(/Program · day 1 of 7/)).toBeVisible();
    await nav(page, "Home").click();
    await expect(page.getByText(/Program · day 1 of 7/)).toBeVisible();

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
    await nav(page, "Home").click();
    await page.reload({ waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }))
      .toBeVisible({ timeout: 20_000 });
    await expect(page).toHaveURL(/\/home$/);
  });
});
