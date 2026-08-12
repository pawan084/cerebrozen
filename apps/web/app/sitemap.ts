import type { MetadataRoute } from "next";

export default function sitemap(): MetadataRoute.Sitemap {
  const base = "https://cerebrozen.in";
  // A fixed date, not `new Date()`: the layout forces dynamic rendering, so a
  // request-time timestamp told crawlers every page changed on every fetch.
  // Bump this when the site's content actually changes.
  const updated = new Date("2026-08-12T00:00:00Z");
  return [
    { url: `${base}/`, lastModified: updated, changeFrequency: "weekly", priority: 1 },
    // The B2B2C entry point from ref/landing.html — the only page describing the
    // sponsored-access privacy boundary, so it is worth crawling on its own.
    { url: `${base}/organizations`, lastModified: updated, changeFrequency: "monthly", priority: 0.7 },
    { url: `${base}/safety`, lastModified: updated, changeFrequency: "monthly", priority: 0.6 },
    { url: `${base}/accessibility`, lastModified: updated, changeFrequency: "monthly", priority: 0.4 },
    { url: `${base}/privacy`, lastModified: updated, changeFrequency: "monthly", priority: 0.5 },
    { url: `${base}/terms`, lastModified: updated, changeFrequency: "monthly", priority: 0.5 },
    { url: `${base}/support`, lastModified: updated, changeFrequency: "monthly", priority: 0.5 },
    // Linked from the Play listing's data-safety section — must stay crawlable.
    { url: `${base}/delete-account`, lastModified: updated, changeFrequency: "monthly", priority: 0.5 },
    { url: `${base}/security`, lastModified: updated, changeFrequency: "monthly", priority: 0.4 },
    { url: `${base}/refunds`, lastModified: updated, changeFrequency: "monthly", priority: 0.4 },
    { url: `${base}/subprocessors`, lastModified: updated, changeFrequency: "monthly", priority: 0.4 },
  ];
}
