"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { NotAnOrgAdminError, hasSession } from "@/lib/api";

/**
 * The pieces that separate a live screen from a sample one.
 *
 * This distinction is the whole risk of connecting the portal: 36 screens were
 * built against `lib/mock.ts`, four of them now read the real `/org` API, and
 * they look identical. An administrator who cannot tell which is which will
 * read invented numbers as their own — so every screen declares itself, and
 * `SampleData` is deliberately loud rather than a footnote.
 */

export function SampleData({ reason }: { reason?: string }) {
  return (
    <div className="notice warn" role="note">
      <span aria-hidden="true">!</span>
      <div>
        <b>Sample data — not your organisation.</b>
        <br />
        {reason ??
          "This screen is the agreed design; the API behind it does not exist yet. Every figure below is a fixed example."}
      </div>
    </div>
  );
}

export function LiveData() {
  return (
    <div className="notice" role="note">
      <span aria-hidden="true">⛨</span>
      <div>
        <b>Live data from your organisation.</b>
        <br />
        Aggregate totals only, with your reporting threshold applied.
      </div>
    </div>
  );
}

/** Shown while a real request is in flight. Honest about the wait. */
export function Loading({ what }: { what: string }) {
  return (
    <div className="card" aria-live="polite">
      <p className="tiny">Loading {what}…</p>
    </div>
  );
}

/**
 * A failed read says what failed and offers a retry — it never falls back to
 * sample data, because a screen that silently substitutes invented numbers for
 * a failed request is worse than one that says it is broken.
 */
export function LoadError({ error, onRetry }: { error: string; onRetry: () => void }) {
  return (
    <div className="notice danger" role="alert">
      <span aria-hidden="true">!</span>
      <div>
        <b>We couldn&rsquo;t load this.</b>
        <br />
        {error}
        <div className="toolbar">
          <button type="button" className="btn secondary" onClick={onRetry}>
            Try again
          </button>
        </div>
      </div>
    </div>
  );
}

/** The 403 an ordinary signed-in user gets: correct, and worth explaining. */
export function NoOrgAccess() {
  return (
    <div className="notice warn" role="alert">
      <span aria-hidden="true">i</span>
      <div>
        <b>This account does not administer an organisation.</b>
        <br />
        Portal access is granted per organisation. If you expected access, ask your benefits
        owner to add you — being a CereBro user is not the same thing.
      </div>
    </div>
  );
}

/**
 * Client-side session guard.
 *
 * The access token lives in memory and the refresh token in localStorage, so
 * there is nothing for a server component to read — the same reason apps/app
 * guards in a client layout. This is a redirect for the signed-out, NOT a
 * security boundary: the boundary is the backend, which answers 403 to anyone
 * who is not an org admin regardless of what the browser renders.
 */
export function RequireSession({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!hasSession()) {
      router.replace("/signin");
      return;
    }
    setReady(true);
  }, [router]);

  if (!ready) return <Loading what="your session" />;
  return <>{children}</>;
}

/** Shared loader state for the four wired screens. */
export function useOrgData<T>(load: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setError(null);
    load()
      .then((d) => {
        if (!cancelled) setData(d);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        if (e instanceof NotAnOrgAdminError) setForbidden(true);
        else setError(e instanceof Error ? e.message : "Something went wrong.");
      });
    return () => {
      cancelled = true;
    };
    // `load` is redefined each render by callers; `tick` is the retry signal.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tick]);

  return { data, error, forbidden, retry: () => setTick((t) => t + 1) };
}

/** Wraps the four live screens: guard, then load, then render. */
export function LiveScreen<T>({
  load,
  what,
  children,
}: {
  load: () => Promise<T>;
  what: string;
  children: (data: T) => React.ReactNode;
}) {
  const { data, error, forbidden, retry } = useOrgData(load);
  if (forbidden) return <NoOrgAccess />;
  if (error) return <LoadError error={error} onRetry={retry} />;
  if (data === null) return <Loading what={what} />;
  return (
    <>
      <LiveData />
      {children(data)}
    </>
  );
}

export function SignOutButton() {
  const router = useRouter();
  return (
    <button
      type="button"
      className="btn ghost small"
      onClick={async () => {
        const { signOut } = await import("@/lib/api");
        await signOut();
        router.replace("/signin");
      }}
    >
      Sign out
    </button>
  );
}

export function SignInLink() {
  return (
    <Link className="btn secondary" href="/signin">
      Sign in
    </Link>
  );
}
