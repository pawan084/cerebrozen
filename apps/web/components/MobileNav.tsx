"use client";

import { useEffect, useRef } from "react";

/**
 * The mobile disclosure menu, with the three dismissals it was missing.
 *
 * Audit E21: this is a native `<details>` — chosen for good reasons (no JS, no
 * motion, works if a script fails) — but nothing ever closed it. Choosing an
 * in-page anchor like #features left the panel sitting open **over the very
 * content the reader had just navigated to**, and there was no outside-click or
 * Escape either. The menu could only be closed by finding "Menu" again
 * underneath the panel covering it.
 *
 * The fix stays inside that original choice rather than replacing it: the
 * markup is still a `<details>`, still renders and toggles server-side-free, and
 * with JavaScript disabled behaves exactly as it does today. This wrapper only
 * adds the closing.
 */
export default function MobileNav({
  children,
  label = "Menu",
  className = "nav-menu",
}: {
  children: React.ReactNode;
  label?: string;
  className?: string;
}) {
  const ref = useRef<HTMLDetailsElement>(null);

  const close = () => {
    if (ref.current) ref.current.open = false;
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === "Escape" && ref.current?.open) {
        close();
        // Return focus to the control that opened it, or the reader is left
        // with no idea where they are in the document.
        ref.current.querySelector("summary")?.focus();
      }
    };
    const onPointerDown = (e: PointerEvent) => {
      if (!ref.current?.open) return;
      if (!ref.current.contains(e.target as Node)) close();
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("pointerdown", onPointerDown);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("pointerdown", onPointerDown);
    };
  }, []);

  return (
    <details
      className={className}
      ref={ref}
      // Delegated rather than wired onto each link: the panel's contents are
      // passed in as children from a server component, so this closes whatever
      // links it is given — including any added later, which is the version of
      // this bug that would otherwise come back.
      onClick={(e) => {
        if ((e.target as HTMLElement).closest("a")) close();
      }}
    >
      <summary>{label}</summary>
      <div className="nav-menu-panel">{children}</div>
    </details>
  );
}
