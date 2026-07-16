/* The marketing site (cerebrozen.in). The admin is a separate deployment on its own
   subdomain, so links back to it are real navigations, not routes.
   NEXT_PUBLIC_ → inlined at build time; override to point at a local web dev server. */

export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || "https://cerebrozen.in";

/** Pages the site hosts that a login screen is expected to link to. */
export const siteLinks = [
  { label: "cerebrozen.in", href: SITE_URL },
  { label: "Privacy", href: `${SITE_URL}/privacy` },
  { label: "Terms", href: `${SITE_URL}/terms` },
] as const;
