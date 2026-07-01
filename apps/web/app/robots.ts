import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: "https://cerebro.app/sitemap.xml",
    host: "https://cerebro.app",
  };
}
