"use client";

import { useState } from "react";

export type FaqEntry = {
  q: string;
  a: string;
  /**
   * An optional link rendered under the answer (audit E25).
   *
   * The two answers most likely to convert a ready visitor — "when does it
   * launch?" and "what platforms?" — named `app.cerebrozen.in` as plain text,
   * so the reader had to retype a URL at the exact moment they had decided to
   * act. It is a separate field rather than markup inside `a` because the same
   * `a` string is serialized into the FAQPage JSON-LD, where an anchor tag would
   * be wrong: structured data wants the answer, not the markup.
   */
  cta?: { label: string; href: string };
};

// `inert` is not in React 18's DOM typings (it landed in React 19), but the
// browser reads the bare attribute fine. Spreading `{ inert: "" }` renders
// `inert=""`; spreading `{}` omits it entirely, which is what an open panel
// needs — `inert={false}` would still render the attribute and trap the answer.
const inertWhenClosed = (open: boolean) =>
  (open ? {} : { inert: "" }) as Record<string, string>;

/**
 * Six disclosures, one open at a time.
 *
 * Deliberately a button + a `grid-template-rows: 0fr → 1fr` panel rather than
 * <details>: the reference's animated open is impossible on a native disclosure
 * (the browser toggles `display`), and the closed panel has to be `inert` +
 * `aria-hidden` so its links never take focus while invisible. The same array
 * feeds the FAQPage JSON-LD on the server, so the two can't drift.
 */
export default function Faq({ items }: { items: FaqEntry[] }) {
  const [open, setOpen] = useState<string | null>(null);

  return (
    <>
      {items.map((f) => {
        const isOpen = open === f.q;
        const panelId = `faq-panel-${f.q.replace(/\W+/g, "-").toLowerCase()}`;
        return (
          <div className="faq-item" key={f.q}>
            <button
              type="button"
              className="faq-q"
              aria-expanded={isOpen}
              aria-controls={panelId}
              onClick={() => setOpen(isOpen ? null : f.q)}
            >
              <span>{f.q}</span>
              <span className="faq-sign" aria-hidden="true">
                +
              </span>
            </button>
            <div
              className="faq-a"
              id={panelId}
              aria-hidden={!isOpen}
              {...inertWhenClosed(isOpen)}
            >
              <div>
                <p>{f.a}</p>
                {f.cta && (
                  <p className="faq-cta">
                    <a href={f.cta.href}>{f.cta.label}</a>
                  </p>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </>
  );
}
