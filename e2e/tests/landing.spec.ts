import { test, expect } from "@playwright/test";

const WEB = process.env.WEB_URL || "http://web:3000";
const APP = process.env.APP_URL || "http://app:3002";

test.describe("Landing site", () => {
  test("renders hero, features, and pricing", async ({ page }) => {
    await page.goto(WEB, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /calmer mind/i })).toBeVisible();
    await expect(page.getByText("becomes agentic")).toBeVisible();
    await expect(page.getByText("Most popular")).toBeVisible();
    await expect(page.getByText("₹499")).toBeVisible();
    await expect(page.getByText(/wellness support, not emergency/i)).toBeVisible();
  });

  // The landing is the only public door to the web app, so these links are
  // load-bearing: if NEXT_PUBLIC_APP_URL is unset at build time every one of
  // them silently points at localhost and the page strands its visitors.
  test("links into the web app from nav, hero, spaces and footer", async ({ page }) => {
    await page.goto(WEB, { waitUntil: "networkidle" });

    await expect(page.getByRole("link", { name: "Open the app" }).first())
      .toHaveAttribute("href", new RegExp(`^${APP}/?$`));
    await expect(page.getByRole("link", { name: /open cerebro in your browser/i }))
      .toHaveAttribute("href", new RegExp(`^${APP}/?$`));

    // Every space card is a door into its matching screen.
    for (const [tab, route] of [
      ["Home", "/home"], ["Sleep", "/sleep"], ["Talk", "/chat"],
      ["Journal", "/journal"], ["You", "/account"],
    ]) {
      await expect(page.getByRole("link", { name: `Open ${tab}` }).first())
        .toHaveAttribute("href", `${APP}${route}`);
    }

    await expect(page.getByRole("link", { name: "Create an account" }))
      .toHaveAttribute("href", `${APP}/signup`);
  });

  test("accepts a waitlist signup via the live API", async ({ page }) => {
    await page.goto(WEB, { waitUntil: "networkidle" });
    const email = `e2e-${Date.now()}@test.app`;
    await page.getByPlaceholder("you@email.com").fill(email);
    await page.getByRole("button", { name: /join the waitlist/i }).click();
    await expect(page.locator(".wl-msg")).toContainText(/in|list/i);
  });
});
