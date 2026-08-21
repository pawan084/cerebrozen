import { describe, expect, it } from "vitest";

import { pageMeta } from "../../apps/web/lib/pageMeta";

// Audit E13. Next merges `metadata` SHALLOWLY, so a page exporting only title,
// description and alternates.canonical inherited the root openGraph object
// wholesale: /privacy, /terms, /support, /security and /refunds all shared to
// social as the homepage, with og:url=https://cerebrozen.in. Anyone posting a
// link to the privacy policy was, as far as every preview card was concerned,
// posting the front page.
//
// The failure mode is OMISSION — the old arrangement worked right up until
// somebody added a page and wrote the obvious three fields — so what is worth
// pinning is that the helper fills in the parts a careful person would forget.
const meta = pageMeta({
  title: "Privacy",
  description: "What we store, and what we never do.",
  path: "/privacy",
});

describe("metadata for a non-home page", () => {
  it("suffixes the brand once, not twice", () => {
    expect(meta.title).toBe("Privacy — CereBro");
  });

  it("points the canonical at this page", () => {
    expect(meta.alternates?.canonical).toBe("/privacy");
  });

  it("points og:url at this page, not at the homepage", () => {
    // The whole bug, in one assertion.
    expect(meta.openGraph?.url).toBe("/privacy");
  });

  it("gives Open Graph its own title and description rather than inheriting", () => {
    expect(meta.openGraph?.title).toBe("Privacy — CereBro");
    expect(meta.openGraph?.description).toBe("What we store, and what we never do.");
  });

  it("gives Twitter the same, since it merges shallowly too", () => {
    expect(meta.twitter?.title).toBe("Privacy — CereBro");
    expect(meta.twitter?.description).toBe("What we store, and what we never do.");
    expect((meta.twitter as any)?.card).toBe("summary_large_image");
  });

  it("ships a social image with the dimensions the crawlers want", () => {
    const image = (meta.openGraph as any)?.images?.[0];
    expect(image.url).toBe("/brand/banner-social.jpg");
    expect(image.width).toBe(1200);
    expect(image.height).toBe(630);
    // Alt text on the card, not just on the page.
    expect(image.alt).toBe("Privacy — CereBro");
  });

  it("names the site so a card is attributed", () => {
    expect((meta.openGraph as any)?.siteName).toBe("CereBro");
    expect((meta.openGraph as any)?.type).toBe("website");
  });

  it("keeps canonical and og:url in step for every page it is given", () => {
    // The two are edited in different places when someone moves a route, and
    // disagreeing is how the original bug hid: the canonical was right, so a
    // spot check looked fine, while the card still pointed at the homepage.
    for (const path of ["/terms", "/support", "/security", "/refunds", "/delete-account"]) {
      const m = pageMeta({ title: "T", description: "D", path });
      expect(m.alternates?.canonical).toBe(path);
      expect(m.openGraph?.url).toBe(path);
    }
  });
});
