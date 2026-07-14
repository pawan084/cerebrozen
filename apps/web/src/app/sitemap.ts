import type { MetadataRoute } from "next";
import { site } from "@/lib/site";

const routes = [
  "",
  "/platform",
  "/solutions",
  "/security",
  "/evidence",
  "/clients",
  "/about",
  "/contact",
  "/privacy",
  "/terms",
];

export default function sitemap(): MetadataRoute.Sitemap {
  return routes.map((path) => ({
    url: `${site.url}${path}`,
    lastModified: new Date(),
  }));
}
