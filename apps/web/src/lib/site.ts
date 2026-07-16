export const site = {
  name: "CereBroZen",
  domain: "cerebrozen.in",
  url: "https://cerebrozen.in",
  tagline: "AI performance coaching for every employee",
  description:
    "CereBroZen is an enterprise AI coaching platform that gives every employee an always-on performance coach — turning hesitation into action and action into measurable results.",
  email: "hello@cerebrozen.in",
  // The two product surfaces, each on its own subdomain; both serve their login at
  // the root when unauthenticated. "Sign in" offers both rather than guessing —
  // employees and HR admins are different people and land in different places.
  // Overridable per environment (dev: :3002 / :3001) — NEXT_PUBLIC_ so they're
  // inlined at build time for the client Navbar. Default to the production domains.
  appUrl: process.env.NEXT_PUBLIC_APP_URL || "https://app.cerebrozen.in",
  adminUrl: process.env.NEXT_PUBLIC_ADMIN_URL || "https://admin.cerebrozen.in",
} as const;

/** Where "Sign in" can take you. Employees first: there are far more of them.
 *  `host` is derived from the URL rather than written out, so the menu shows the
 *  host it will actually send you to (localhost in dev, not a prod domain it isn't
 *  using). Falls back to the raw value if the override isn't a parseable URL. */
const hostOf = (url: string) => {
  try {
    return new URL(url).host;
  } catch {
    return url;
  }
};

export const signInTargets = [
  { who: "Employee", what: "Talk with your coach", href: site.appUrl, host: hostOf(site.appUrl) },
  { who: "HR / admin", what: "Console & analytics", href: site.adminUrl, host: hostOf(site.adminUrl) },
] as const;

export const navLinks = [
  { label: "Platform", href: "/platform" },
  { label: "Solutions", href: "/solutions" },
  { label: "Security", href: "/security" },
  { label: "Evidence", href: "/evidence" },
  { label: "Client stories", href: "/clients" },
  { label: "About", href: "/about" },
] as const;
