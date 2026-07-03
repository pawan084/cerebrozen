import { test, expect, Page } from "@playwright/test";

const APP = process.env.APP_URL || "http://app:3002";

function tab(page: Page, label: string) {
  return page.locator(".tabs a", { hasText: label });
}

test.describe("Web app (authenticated client)", () => {
  // One long journey across every page — needs more than the 30s default
  // (chat may wait on a live LLM when the stack runs with real keys).
  test("signup → mood check-in → journal → sleep diary → session survives reload", async ({ page }) => {
    test.setTimeout(150_000);
    // Fresh account per run (the e2e stack seeds no app users).
    const email = `e2e-app-${Date.now()}@test.app`;
    await page.goto(`${APP}/signup`, { waitUntil: "networkidle" });
    await page.locator('input[autocomplete="name"]').fill("E2E");
    await page.locator('input[type="email"]').fill(email);
    await page.locator('input[type="password"]').fill("password123");
    await page.getByRole("button", { name: "Create account" }).click();
    await expect(page.getByRole("heading", { name: /Good (morning|afternoon|evening)/ }))
      .toBeVisible({ timeout: 20_000 });

    // Mood check-in surfaces in the recent list.
    await page.getByRole("button", { name: /Anxious/ }).click();
    await page.getByRole("button", { name: "Check in", exact: true }).click();
    await expect(page.getByText("Checked in — noted gently.")).toBeVisible();
    await expect(page.getByRole("heading", { name: "Recent check-ins" })).toBeVisible();
    // The server-computed streak starts with the first check-in.
    await expect(page.getByRole("heading", { name: "1-day streak" })).toBeVisible({ timeout: 15_000 });

    // Journal entry lands in History.
    await tab(page, "Journal").click();
    await page.locator('input[maxlength="120"]').fill("Meeting pressure");
    await page.locator("textarea").fill("A bit stressed about tomorrow.");
    await page.getByRole("button", { name: "Save entry" }).click();
    await expect(page.locator(".entry", { hasText: "Meeting pressure" })).toBeVisible();

    // Sleep diary: log the morning, summary stays honest at one night.
    await tab(page, "Sleep").click();
    await page.getByRole("radio", { name: "Good" }).click();
    await page.getByRole("button", { name: "Save check-in" }).click();
    await expect(page.getByText(/Saved — one entry per morning/)).toBeVisible();
    await expect(page.getByText(/Log 2 more mornings/)).toBeVisible();

    // Chat replies (Oracle stream when a key is present, deterministic /chat
    // fallback otherwise — either way an assistant bubble must land).
    await tab(page, "Chat").click();
    await page.getByLabel("Message").fill("I keep overthinking tomorrow's meeting.");
    await page.getByRole("button", { name: "Send" }).click();
    await expect(page.locator(".msg.user").first()).toBeVisible();
    // First Oracle call on a fresh stack pays checkpointer init + two LLM
    // round-trips (safety scan + graph) — give it a real window.
    await expect(page.locator(".msg.ai").first()).toBeVisible({ timeout: 60_000 });

    // Plan lazily generates; the first step toggles and persists server-side.
    await tab(page, "Plan").click();
    const firstStep = page.locator('input[type="checkbox"]').first();
    await expect(firstStep).toBeVisible({ timeout: 20_000 });
    await firstStep.click(); // controlled input: click, then assert the re-render
    await expect(firstStep).toBeChecked({ timeout: 10_000 });

    // Insights compute the five real metrics (incl. the Sleep metric).
    await tab(page, "Insights").click();
    await expect(page.locator('[aria-label="Weekly metrics"] .entry')).toHaveCount(5, { timeout: 20_000 });
    await expect(page.getByLabel("Weekly metrics").getByText("Sleep", { exact: true })).toBeVisible();

    // Library renders the served catalogue (incl. the wind-down guide).
    await tab(page, "Library").click();
    await expect(page.getByRole("heading", { name: "Wind down tonight" })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText("Keep a steady wake time")).toBeVisible();

    // Account: consent toggles render and flip (enforced server-side).
    await tab(page, "Account").click();
    const aiMemory = page.getByRole("switch", { name: "AI memory" });
    await expect(aiMemory).toBeVisible({ timeout: 20_000 });
    const before = await aiMemory.isChecked();
    await aiMemory.click();
    await expect(aiMemory).toBeChecked({ checked: !before });

    // A reload drops the in-memory access token — the refresh rotation
    // must silently restore the session (docs/WEB_APP_PLAN.md §3).
    await tab(page, "Sleep").click();
    await page.reload({ waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Sleep diary" })).toBeVisible({ timeout: 20_000 });
    await expect(page.locator(".entry").first()).toBeVisible();
  });
});
