import type { MetadataRoute } from "next";
import { site } from "@/lib/site";

/** Web app manifest — makes the marketing site installable/branded in browser
 *  chrome and on mobile home screens. Icons reference the existing SVG mark. */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: `${site.name} — ${site.tagline}`,
    short_name: site.name,
    description: site.description,
    start_url: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#f56b6b",
    icons: [
      { src: "/icon.svg", sizes: "any", type: "image/svg+xml", purpose: "any" },
    ],
  };
}
