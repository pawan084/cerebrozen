import { test, expect } from "@playwright/test";

const WEB = process.env.WEB_URL || "http://web:3000";
const APP = process.env.APP_URL || "http://app:3002";

test.describe("Landing site", () => {
  test("renders hero, the section spine, and pricing", async ({ page }) => {
    await page.goto(WEB, { waitUntil: "networkidle" });
    await expect(page.getByRole("heading", { name: /calmer mind/i })).toBeVisible();
    // One landmark per band of the v2 structure, in page order — hero,
    // outcomes, the dark product-tour panel, the loop, sleep, trust, compare.
    await expect(page.getByRole("heading", { name: /moment you are actually in/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /five calm spaces/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /one manageable action/i })).toBeVisible();
    await expect(page.getByRole("heading", { name: /trust should be visible/i })).toBeVisible();
    await expect(page.getByText("Most popular")).toBeVisible();
    await expect(page.getByText("₹499")).toBeVisible();
    await expect(page.getByText(/wellness support, not emergency/i)).toBeVisible();
  });

  // Six disclosures that are buttons, not <details>: the closed panel must stay
  // inert and aria-hidden so its copy never reaches the accessibility tree, and
  // opening one must close the previous.
  test("FAQ discloses one answer at a time and hides the rest", async ({ page }) => {
    await page.goto(WEB, { waitUntil: "networkidle" });
    const first = page.getByRole("button", { name: /is cerebro a therapist/i });
    const second = page.getByRole("button", { name: /is my data private/i });

    const firstPanel = page.locator(".faq-item", { has: first }).locator(".faq-a");

    await expect(first).toHaveAttribute("aria-expanded", "false");
    await expect(firstPanel).toHaveAttribute("aria-hidden", "true");
    await expect(firstPanel).toHaveAttribute("inert", "");

    await first.click();
    await expect(first).toHaveAttribute("aria-expanded", "true");
    await expect(firstPanel).toHaveAttribute("aria-hidden", "false");
    await expect(firstPanel).not.toHaveAttribute("inert", /.*/);
    await expect(page.getByText(/never diagnoses, prescribes/i)).toBeVisible();

    await second.click();
    await expect(first).toHaveAttribute("aria-expanded", "false");
    await expect(second).toHaveAttribute("aria-expanded", "true");
    await expect(firstPanel).toHaveAttribute("inert", "");
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
      ["Today", "/home"], ["Explore", "/explore"], ["Talk", "/chat"],
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
