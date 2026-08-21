// Stand-in for `next/link` — see next-navigation.ts for why these are aliased
// rather than mocked.
//
// Renders a plain anchor, which is what next/link renders anyway once its
// prefetching and router plumbing are stripped. Keeping it an <a> matters:
// the tests assert on roles and hrefs, and a <div> here would quietly make
// every "is this reachable" assertion meaningless.

import type { AnchorHTMLAttributes, ReactNode } from "react";

// href is Omitted from the base attributes first: AnchorHTMLAttributes already
// declares it as `string`, so intersecting narrows the object form to `never`
// and next/link's `{ pathname }` shape becomes unreachable.
type Props = Omit<AnchorHTMLAttributes<HTMLAnchorElement>, "href"> & {
  href: string | { pathname?: string };
  children?: ReactNode;
  // next/link props with no meaning in a test, accepted so they do not land on
  // the DOM node and trigger React's unknown-attribute warnings.
  prefetch?: boolean;
  replace?: boolean;
  scroll?: boolean;
  shallow?: boolean;
};

export default function Link({
  href,
  children,
  prefetch: _p,
  replace: _r,
  scroll: _s,
  shallow: _sh,
  ...rest
}: Props) {
  const to = typeof href === "string" ? href : (href?.pathname ?? "#");
  return (
    <a href={to} {...rest}>
      {children}
    </a>
  );
}
