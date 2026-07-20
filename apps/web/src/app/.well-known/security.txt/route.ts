import { site } from "@/lib/site";

// RFC 9116 security.txt. A route handler (not a static file) so the mandatory `Expires`
// field stays ~1 year out on every deploy rather than silently going stale.
export const dynamic = "force-dynamic";

export function GET() {
  const expires = new Date();
  expires.setUTCFullYear(expires.getUTCFullYear() + 1);
  const body =
    [
      `Contact: mailto:${site.email}`,
      `Expires: ${expires.toISOString()}`,
      "Preferred-Languages: en",
      `Canonical: ${site.url}/.well-known/security.txt`,
      `Policy: ${site.url}/security`,
    ].join("\n") + "\n";

  return new Response(body, {
    headers: {
      "content-type": "text/plain; charset=utf-8",
      "cache-control": "public, max-age=86400",
    },
  });
}
