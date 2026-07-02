import type { MetadataRoute } from "next";

export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/" },
    sitemap: "https://cerebrozen.in/sitemap.xml",
    host: "https://cerebrozen.in",
  };
}
