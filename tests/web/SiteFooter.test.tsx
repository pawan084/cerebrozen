import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { SiteFooter } from "../../apps/web/components/SiteFooter";
import { APP_URL } from "../../apps/web/lib/appUrl";

afterEach(cleanup);

// "It replaced four hand-copied blocks (landing, privacy, terms, support, 404)
// that had already drifted — only one of them was ever going to get new links."
// The e2e suite walks the footer on the rendered landing page; this pins the
// destinations themselves, so a link that disappears fails here rather than in
// a browser run that only visits one page.
const hrefOf = (name: string | RegExp) =>
  screen.getByRole("link", { name }).getAttribute("href");

describe("the trust pages are all reachable", () => {
  it.each([
    ["Safety centre", "/safety"],
    ["Privacy", "/privacy"],
    ["Terms", "/terms"],
    ["Accessibility", "/accessibility"],
    ["Security", "/security"],
    ["Refunds", "/refunds"],
    ["Subprocessors", "/subprocessors"],
    ["Delete your account", "/delete-account"],
  ])("%s points at %s", (label, href) => {
    render(<SiteFooter />);
    expect(hrefOf(label)).toBe(href);
  });

  it("offers crisis support from every page", () => {
    // The footer is the one thing on every marketing page, so it is the only
    // place a route to help is guaranteed to exist.
    render(<SiteFooter />);
    expect(hrefOf("Crisis support")).toBe("/support");
  });

  it("names the deletion route the Play listing depends on", () => {
    // Play Console requires a reachable URL where someone can request account
    // and data deletion WITHOUT installing the app. Losing this link from the
    // footer would not break a page — it would break a store listing.
    render(<SiteFooter />);
    expect(hrefOf("Delete your account")).toBe("/delete-account");
  });
});

describe("no page is a dead end", () => {
  // "before this, every page except the landing offered no way into the app."
  it("links all five app spaces", () => {
    render(<SiteFooter />);
    for (const label of ["Today", "Explore", "Talk", "Journal", "You"]) {
      expect(screen.getByRole("link", { name: label }), `${label} is missing`).toBeTruthy();
    }
  });

  it("sends them to the app's own origin, not to a marketing route", () => {
    // These cross an origin — /journal on the marketing site is a 404.
    render(<SiteFooter />);
    expect(hrefOf("Journal")).toBe(`${APP_URL}/journal`);
    expect(hrefOf("Today")).toBe(`${APP_URL}/home`);
  });

  it("offers both doors for an account", () => {
    render(<SiteFooter />);
    expect(hrefOf("Sign in")).toContain(APP_URL);
    expect(hrefOf("Create an account")).toContain(APP_URL);
  });

  it("keeps the in-page anchors as anchors", () => {
    render(<SiteFooter />);
    expect(hrefOf("Pricing")).toBe("/#pricing");
    expect(hrefOf("iOS waitlist")).toBe("/#waitlist");
  });

  it("says where the organisation boundary is written down", () => {
    render(<SiteFooter />);
    expect(hrefOf("Sponsored access")).toBe("/organizations");
    expect(hrefOf("The privacy boundary")).toBe("/organizations#boundary");
  });
});

describe("its structure", () => {
  it("groups the links under named navigations", () => {
    // Four unlabelled <nav>s in a footer is four identical landmarks to anyone
    // navigating by them.
    render(<SiteFooter />);
    // The aria-labels are fuller than the visible headings ("Trust and
    // support" over a column headed "Trust"), which is the right way round:
    // the landmark list is read without the heading beside it.
    for (const label of ["Open the app", "Your account", "Trust and support", "For organizations"]) {
      expect(screen.getByRole("navigation", { name: label }), `no nav named ${label}`).toBeTruthy();
    }
  });

  it("gives every link a destination", () => {
    render(<SiteFooter />);
    for (const link of screen.getAllByRole("link")) {
      const href = link.getAttribute("href");
      expect(href, `"${link.textContent}" has no href`).toBeTruthy();
      expect(href).not.toBe("#");
    }
  });

  it("leads back to the homepage", () => {
    render(<SiteFooter />);
    expect(screen.getAllByRole("link").some((a) => a.getAttribute("href") === "/")).toBe(true);
  });
});
