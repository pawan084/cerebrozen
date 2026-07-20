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

// Emitted only from /pricing. Prices are REAL, not invented — they mirror the platform's
// single source of truth (services/platform billing.PRICES, GET /billing/prices), so this
// stays inside rule 6. Update both if the price changes.
export const plusProductSchema = {
  "@context": "https://schema.org",
  "@type": "Product",
  name: "CereBro Plus",
  description:
    "The paid tier of CereBroZen: unlimited AI coaching, voice, every guided program, sleep tracking, weekly insights, the pattern dashboard, and ambient soundscapes.",
  brand: { "@type": "Brand", name: site.name },
  offers: [
    {
      "@type": "Offer",
      name: "CereBro Plus — yearly",
      price: "59.99",
      priceCurrency: "USD",
      url: `${site.url}/pricing`,
      availability: "https://schema.org/InStock",
    },
    {
      "@type": "Offer",
      name: "CereBro Plus — monthly",
      price: "9.99",
      priceCurrency: "USD",
      url: `${site.url}/pricing`,
      availability: "https://schema.org/InStock",
    },
  ],
} as const;
