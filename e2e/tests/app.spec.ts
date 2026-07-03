import { test, expect, Page } from "@playwright/test";

const APP = process.env.APP_URL || "http://app:3002";

function tab(page: Page, label: string) {
  return page.locator(".tabs a", { hasText: label });
}

test.describe("Web app (authenticated client)", () => {
  test("signup → mood check-in → journal → sleep diary → session survives reload", async ({ page }) => {
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

    // A reload drops the in-memory access token — the refresh rotation
    // must silently restore the session (docs/WEB_APP_PLAN.md §3).
    await page.reload({ waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: "Sleep diary" })).toBeVisible({ timeout: 20_000 });
    await expect(page.locator(".entry").first()).toBeVisible();
  });
});
