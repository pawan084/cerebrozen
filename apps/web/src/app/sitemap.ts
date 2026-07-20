import type { MetadataRoute } from "next";
import { site } from "@/lib/site";

const routes = [
  "",
  "/platform",
  "/solutions",
  "/coaching-not-therapy",
  "/security",
  "/sovereignty",
  "/evidence",
  "/pricing",
  "/clients",
  "/about",
  "/contact",
  "/privacy",
  "/terms",
  "/accessibility",
];

export default function sitemap(): MetadataRoute.Sitemap {
  return routes.map((path) => ({
    url: `${site.url}${path}`,
    lastModified: new Date(),
  }));
}
