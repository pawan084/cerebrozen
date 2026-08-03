// Where the authenticated browser client (apps/app) lives.
//
// Same build-time seam as NEXT_PUBLIC_API_URL: inlined into the bundle by
// `next build`, so it is set as a Docker build ARG (see apps/web/Dockerfile and
// the `web` service in docker-compose.yml), not at runtime. Default is the
// local dev port; production sets https://app.cerebrozen.in.
export const APP_URL =
  process.env.NEXT_PUBLIC_APP_URL || "http://localhost:3002";

/**
 * Absolute URL into the web app — `appHref("/sleep")`.
 *
 * Deep links are honest about the sign-in wall: the app bounces a signed-out
 * visitor to /signin carrying `?next=`, then lands them where they clicked
 * (apps/app `(authed)/layout.tsx` + `signin/page.tsx`). Without that, every
 * link here would dump people on /home regardless of what they picked.
 */
export function appHref(path = "/"): string {
  const base = APP_URL.replace(/\/+$/, "");
  if (!path || path === "/") return base || "/";
  return `${base}${path.startsWith("/") ? path : `/${path}`}`;
}
