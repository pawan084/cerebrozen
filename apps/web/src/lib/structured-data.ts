import { site } from "./site";

/**
 * JSON-LD structured data. Kept to facts the site can stand behind — name, URL,
 * description, contact — with no invented ratings, prices, or social handles
 * (CLAUDE.md rule 6: claims map to mechanisms). Emitted from the server layout.
 */

export const organizationSchema = {
  "@context": "https://schema.org",
  "@type": "Organization",
  name: site.name,
  url: site.url,
  description: site.description,
  logo: `${site.url}/icon.svg`,
  email: site.email,
  contactPoint: {
    "@type": "ContactPoint",
    email: site.email,
    contactType: "sales",
  },
} as const;

export const websiteSchema = {
  "@context": "https://schema.org",
  "@type": "WebSite",
  name: site.name,
  url: site.url,
  description: site.description,
  publisher: { "@type": "Organization", name: site.name },
} as const;
