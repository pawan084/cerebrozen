import type { Metadata } from "next";

/**
 * Metadata for a non-home page, with its Open Graph tags actually pointing at it.
 *
 * Audit E13: Next merges `metadata` **shallowly**. A page that exported only
 * `title`, `description` and `alternates.canonical` — which every legal and
 * trust page did — inherited the root `openGraph` object wholesale, so
 * /privacy, /terms, /support, /security, /refunds and the rest all shared to
 * social with the homepage's title and `og:url=https://cerebrozen.in`. Anyone
 * posting a link to the privacy policy was, as far as every preview card was
 * concerned, posting the front page.
 *
 * The fix is a helper rather than a note in a comment, because the failure mode
 * is *omission*: the previous arrangement worked correctly right up until
 * somebody added a page and wrote the obvious three fields. Here the correct
 * thing is the short thing.
 */
export function pageMeta({
  title,
  description,
  path,
}: {
  title: string;
  description: string;
  /** Route path, leading slash, no domain — e.g. "/privacy". */
  path: string;
}): Metadata {
  const fullTitle = `${title} — CereBro`;
  return {
    title: fullTitle,
    description,
    alternates: { canonical: path },
    openGraph: {
      title: fullTitle,
      description,
      url: path,          // resolved against `metadataBase` in the root layout
      siteName: "CereBro",
      type: "website",
      images: [{ url: "/brand/banner-social.jpg", width: 1200, height: 630, alt: fullTitle }],
    },
    twitter: {
      card: "summary_large_image",
      title: fullTitle,
      description,
      images: ["/brand/banner-social.jpg"],
    },
  };
}
