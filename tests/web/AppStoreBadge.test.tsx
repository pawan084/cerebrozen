import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

// The store URL is read once at module load, so each case takes a fresh copy.
async function badge(url?: string) {
  vi.resetModules();
  if (url !== undefined) vi.stubEnv("NEXT_PUBLIC_APP_STORE_URL", url);
  const { default: AppStoreBadge } = await import("../../apps/web/components/AppStoreBadge");
  return render(<AppStoreBadge />);
}

afterEach(() => {
  cleanup();
  vi.unstubAllEnvs();
});

describe("before the app is on the App Store", () => {
  // The whole point of this component. A lookalike Apple badge promises a
  // download that does not exist AND misuses Apple's marketing mark — so until
  // a real listing URL is configured it reads as a status pill in our own
  // palette and points at the waitlist.
  it("says coming soon rather than offering a download", async () => {
    await badge("");
    expect(screen.getByText("Coming soon")).toBeTruthy();
    expect(screen.getByText("iOS app")).toBeTruthy();
  });

  it("never says 'Download on the App Store' when there is nothing to download", async () => {
    const { container } = await badge("");
    expect(container.textContent).not.toMatch(/download/i);
    expect(container.textContent).not.toMatch(/App Store/i);
  });

  it("tells a screen reader the same thing the pill says", async () => {
    // The visible text and the accessible name have to make the SAME promise;
    // an aria-label reading "Download on the App Store" over a "Coming soon"
    // pill is the lie in its most invisible form.
    await badge("");
    const link = screen.getByRole("link");
    expect(link.getAttribute("aria-label")).toBe("iOS coming soon — join the waitlist");
  });

  it("leads to the waitlist, which is the thing that actually exists", async () => {
    await badge("");
    expect(screen.getByRole("link").getAttribute("href")).toBe("#waitlist");
  });

  it("does not wear the live store styling", async () => {
    await badge("");
    expect(screen.getByRole("link").className).not.toContain("is-live");
  });
});

describe("once a real listing is configured", () => {
  const URL = "https://apps.apple.com/in/app/cerebro/id0000000000";

  it("switches to the store treatment", async () => {
    await badge(URL);
    expect(screen.getByText("Download on the")).toBeTruthy();
    expect(screen.getByText(/App Store/)).toBeTruthy();
    expect(screen.getByRole("link").className).toContain("is-live");
  });

  it("points at the listing rather than the waitlist", async () => {
    await badge(URL);
    expect(screen.getByRole("link").getAttribute("href")).toBe(URL);
  });

  it("moves its accessible name with it", async () => {
    await badge(URL);
    expect(screen.getByRole("link").getAttribute("aria-label")).toBe("Download on the App Store");
  });

  it("does not go live on the placeholder alone", async () => {
    // "#waitlist" is the sentinel for "not shipped". Anything that treated a
    // non-empty value as configured would flip the badge to a store link
    // pointing at an anchor on this same page.
    await badge("#waitlist");
    expect(screen.getByRole("link").className).not.toContain("is-live");
    expect(screen.getByText("Coming soon")).toBeTruthy();
  });
});

describe("the mark itself", () => {
  it("stays out of the reading — the text beside it carries the meaning", async () => {
    const { container } = await badge("");
    expect(container.querySelector("svg")!.getAttribute("aria-hidden")).toBe("true");
  });

  it("is drawn inline in the surrounding colour, not fetched as Apple's asset", async () => {
    // Deliberate: dropping in Apple's official badge image is the LAUNCH step,
    // once there is a listing to point at and their guidelines apply.
    const { container } = await badge("");
    const svg = container.querySelector("svg")!;
    expect(svg.getAttribute("fill")).toBe("currentColor");
    expect(container.querySelector("img")).toBeNull();
  });
});
