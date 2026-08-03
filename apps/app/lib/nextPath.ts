/**
 * Validate a `?next=` return path.
 *
 * The landing deep-links into app screens ("Open Sleep →"), so a signed-out
 * visitor has to be sent through sign-in and then back to what they clicked.
 * That return path arrives in the URL, which makes it attacker-controlled —
 * hence an allow-list of shapes rather than a block-list:
 *
 *   - must be a same-origin absolute path ("/sleep")
 *   - "//evil.com" is a protocol-relative URL, not a path
 *   - browsers normalise "\" to "/", so "/\evil.com" would escape too
 *   - returning to an auth screen would just loop
 *
 * Anything else falls back to the caller's default (/home).
 */
const AUTH_ROUTES = ["/signin", "/signup", "/onboarding"];

export function safeNext(raw: string | null | undefined): string | null {
  if (!raw) return null;
  if (!raw.startsWith("/") || raw.startsWith("//") || raw.includes("\\")) return null;
  const path = raw.split(/[?#]/)[0];
  if (AUTH_ROUTES.includes(path)) return null;
  return raw;
}

/** The path a signed-out visitor was trying to reach, for the sign-in link. */
export function currentPath(): string {
  if (typeof window === "undefined") return "/home";
  return `${window.location.pathname}${window.location.search}`;
}
