import type { MetadataRoute } from "next";

export default function sitemap(): MetadataRoute.Sitemap {
  const base = "https://cerebrozen.in";
  // A fixed date, not `new Date()`: the layout forces dynamic rendering, so a
  // request-time timestamp told crawlers every page changed on every fetch.
  // Bump this when the site's content actually changes.
  const updated = new Date("2026-07-29T00:00:00Z");
  return [
    { url: `${base}/`, lastModified: updated, changeFrequency: "weekly", priority: 1 },
    { url: `${base}/privacy`, lastModified: updated, changeFrequency: "monthly", priority: 0.5 },
    { url: `${base}/terms`, lastModified: updated, changeFrequency: "monthly", priority: 0.5 },
    { url: `${base}/support`, lastModified: updated, changeFrequency: "monthly", priority: 0.5 },
  ];
}
