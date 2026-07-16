"use client";

/* A tiny, safe markdown renderer for coach replies — **bold**, *italic*, `code`,
   bullet/numbered lists, paragraphs, line breaks. Everything goes through React
   text nodes (no dangerouslySetInnerHTML), so model output can't inject HTML. */

import { Fragment, type ReactNode } from "react";

function inline(text: string): ReactNode[] {
  const out: ReactNode[] = [];
  const re = /(\*\*([^*]+)\*\*|\*([^*]+)\*|`([^`]+)`)/g;
  let last = 0;
  let k = 0;
  let m: RegExpExecArray | null;
  while ((m = re.exec(text))) {
    if (m.index > last) out.push(text.slice(last, m.index));
    if (m[2] !== undefined) out.push(<strong key={k++}>{m[2]}</strong>);
    else if (m[3] !== undefined) out.push(<em key={k++}>{m[3]}</em>);
    else if (m[4] !== undefined) out.push(<code key={k++}>{m[4]}</code>);
    last = m.index + m[0].length;
  }
  if (last < text.length) out.push(text.slice(last));
  return out;
}

export function Markdown({ text }: { text: string }) {
  const blocks = text.trim().split(/\n{2,}/);
  return (
    <>
      {blocks.map((block, i) => {
        const lines = block.split("\n");
        if (lines.every((l) => /^\s*[-*]\s+/.test(l)))
          return <ul key={i}>{lines.map((l, j) => <li key={j}>{inline(l.replace(/^\s*[-*]\s+/, ""))}</li>)}</ul>;
        if (lines.every((l) => /^\s*\d+\.\s+/.test(l)))
          return <ol key={i}>{lines.map((l, j) => <li key={j}>{inline(l.replace(/^\s*\d+\.\s+/, ""))}</li>)}</ol>;
        return <p key={i}>{lines.map((l, j) => <Fragment key={j}>{j > 0 && <br />}{inline(l)}</Fragment>)}</p>;
      })}
    </>
  );
}
