import { statSync } from "node:fs";
import { join } from "node:path";
import type { MetadataRoute } from "next";

const base = "https://cerebrozen.in";

/** Fallback when a page's file cannot be stat'd — the last hand-bumped value. */
const FALLBACK = new Date("2026-08-12T00:00:00Z");

/**
 * When this route's own source file last changed.
 *
 * Audit E17: `lastModified` was one hand-maintained constant with a comment
 * asking the next person to "bump this when the site's content actually
 * changes". That is a process, and processes of that shape fail silently — the
 * first time someone forgets, all eleven URLs start mis-signalling freshness to
 * crawlers, and nothing anywhere goes red to say so.
 *
 * Reading the page file's mtime cannot go stale: editing a page changes its
 * date, and editing nothing changes nothing. It is not perfect — a container
 * build that checks the tree out fresh gives every file the checkout time, so
 * the dates collapse to "when this was deployed" — but that is a defensible
 * answer, and it is arrived at rather than remembered.
 *
 * `new Date()` is still deliberately not used (the original comment's point
 * stands): the layout forces dynamic rendering, so a request-time timestamp
 * would tell crawlers every page changed on every fetch.
 */
function lastModified(route: string): Date {
  const file = join(process.cwd(), "app", route, "page.tsx");
  try {
    return statSync(file).mtime;
  } catch {
    return FALLBACK;
  }
}

export default function sitemap(): MetadataRoute.Sitemap {
  const pages: { route: string; path: string; changeFrequency: "weekly" | "monthly"; priority: number }[] = [
    { route: "", path: "/", changeFrequency: "weekly", priority: 1 },
    // The B2B2C entry point from ref/landing.html — the only page describing the
    // sponsored-access privacy boundary, so it is worth crawling on its own.
    { route: "organizations", path: "/organizations", changeFrequency: "monthly", priority: 0.7 },
    { route: "safety", path: "/safety", changeFrequency: "monthly", priority: 0.6 },
    { route: "accessibility", path: "/accessibility", changeFrequency: "monthly", priority: 0.4 },
    { route: "privacy", path: "/privacy", changeFrequency: "monthly", priority: 0.5 },
    { route: "terms", path: "/terms", changeFrequency: "monthly", priority: 0.5 },
    { route: "support", path: "/support", changeFrequency: "monthly", priority: 0.5 },
    // Linked from the Play listing's data-safety section — must stay crawlable.
    { route: "delete-account", path: "/delete-account", changeFrequency: "monthly", priority: 0.5 },
    { route: "security", path: "/security", changeFrequency: "monthly", priority: 0.4 },
    { route: "refunds", path: "/refunds", changeFrequency: "monthly", priority: 0.4 },
    { route: "subprocessors", path: "/subprocessors", changeFrequency: "monthly", priority: 0.4 },
  ];

  return pages.map((p) => ({
    url: `${base}${p.path === "/" ? "/" : p.path}`,
    lastModified: lastModified(p.route),
    changeFrequency: p.changeFrequency,
    priority: p.priority,
  }));
}
