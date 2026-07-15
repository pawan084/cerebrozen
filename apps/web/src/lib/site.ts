export const site = {
  name: "CereBroZen",
  domain: "cerebrozen.in",
  url: "https://cerebrozen.in",
  tagline: "AI performance coaching for every employee",
  description:
    "CereBroZen is an enterprise AI coaching platform that gives every employee an always-on performance coach — turning hesitation into action and action into measurable results.",
  email: "hello@cerebrozen.in",
  // The admin/HR console lives on its own subdomain. The "Sign in" link points
  // here; the admin serves its login at the root when unauthenticated. Overridable
  // per environment (dev: http://localhost:3001) — NEXT_PUBLIC_ so it's inlined at
  // build time for the client Navbar. Defaults to the production admin domain.
  adminUrl: process.env.NEXT_PUBLIC_ADMIN_URL || "https://admin.cerebrozen.in",
} as const;

export const navLinks = [
  { label: "Platform", href: "/platform" },
  { label: "Solutions", href: "/solutions" },
  { label: "Security", href: "/security" },
  { label: "Evidence", href: "/evidence" },
  { label: "Client stories", href: "/clients" },
  { label: "About", href: "/about" },
] as const;
