"use client";

import { useEffect } from "react";
import Link from "next/link";

/**
 * Route-level error boundary. Keeps the nav/footer (unlike global-error) and
 * offers a real recovery path rather than a white screen. `reset()` re-renders
 * the segment; the Home link is the escape hatch when it can't.
 */
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Surfaced in the browser console; a real deploy would forward this to its
    // error sink. No PII is in a marketing-page error.
    console.error(error);
  }, [error]);

  return (
    <section className="mx-auto flex max-w-3xl flex-col items-center px-6 py-28 text-center sm:py-36">
      <h1 className="font-[family-name:var(--font-heading)] text-4xl font-bold tracking-tight text-brand-900 sm:text-5xl text-balance">
        Something went wrong
      </h1>
      <p className="mt-4 max-w-md text-brand-600">
        A hiccup on our end, not yours. Try again, or head back home.
      </p>
      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <button
          type="button"
          onClick={reset}
          className="rounded-full bg-brand-900 px-6 py-3 text-[13.5px] font-semibold text-white transition hover:bg-brand-800"
        >
          Try again
        </button>
        <Link
          href="/"
          className="rounded-full border-2 border-zen-500 px-6 py-3 text-[13.5px] font-semibold text-zen-600 transition hover:bg-zen-500 hover:text-white"
        >
          Back to home
        </Link>
      </div>
    </section>
  );
}
