// The one place crisis numbers are rendered — chat banner, journal banner and
// the /support page all use it, so "Tele-MANAS first, every number tappable"
// can't drift between surfaces. No hooks, no fetches: safe in server and client
// components alike.

import { CRISIS_LINES, CrisisLine, crisisHref, isWebLink, teleManasFirst } from "@/lib/crisis";

export function CrisisLines({
  lines = CRISIS_LINES,
  compact = false,
}: { lines?: CrisisLine[]; compact?: boolean }) {
  return (
    <ul className={compact ? "crisis-lines compact" : "crisis-lines"}>
      {teleManasFirst(lines).map((l) => {
        const web = isWebLink(l.number);
        return (
          <li key={`${l.name}-${l.number}`}>
            <a
              className="crisis-line"
              href={crisisHref(l.number)}
              aria-label={web ? `Open ${l.name}` : `Call ${l.name} on ${l.number}`}
              {...(web ? { target: "_blank", rel: "noreferrer" } : {})}
            >
              <span className="crisis-line-name">{l.name}</span>
              <span className="crisis-line-num">
                {web ? l.number.replace(/^https?:\/\//, "") : l.number}
              </span>
            </a>
          </li>
        );
      })}
    </ul>
  );
}
