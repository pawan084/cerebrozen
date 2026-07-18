"use client";

import { useEffect, useState } from "react";

/** Thin coral reading-progress bar pinned to the top of the viewport. Decorative
 *  (aria-hidden); it reflects scroll position, so it stays useful even under
 *  reduced motion where the width transition is neutralized. */
export default function ScrollProgress() {
  const [pct, setPct] = useState(0);

  useEffect(() => {
    const onScroll = () => {
      const el = document.documentElement;
      const max = el.scrollHeight - el.clientHeight;
      setPct(max > 0 ? (el.scrollTop / max) * 100 : 0);
    };
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      window.removeEventListener("scroll", onScroll);
      window.removeEventListener("resize", onScroll);
    };
  }, []);

  return (
    <div className="fixed inset-x-0 top-0 z-[60] h-0.5" aria-hidden="true">
      <div
        className="h-full bg-zen-500 transition-[width] duration-75 ease-out"
        style={{ width: `${pct}%` }}
      />
    </div>
  );
}
