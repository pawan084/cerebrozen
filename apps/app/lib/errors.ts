/**
 * Structured error tracking for the browser client (WC-17).
 *
 * The port of `backend/app/services/errors.py`, and deliberately the same
 * contract rather than a new one — a fingerprint computed differently on each
 * client is three incident counts that cannot be added up.
 *
 * Before this the web app had no error boundary and no `window` handlers at
 * all: a render crash showed Next's default screen and was recorded nowhere,
 * so "how often does the journal blow up on iOS Safari" had no answer.
 *
 * ## The policy is an allow-list, and it is the whole design
 *
 * Nothing is scrubbed OUT of a rich context; a fixed set of fields is copied
 * IN. A deny-list fails open on the field nobody thought of, and in a browser
 * that field is `error.message` — which routinely contains the value that broke
 * the parse, and on this product that value is something a person wrote about
 * their own mind.
 *
 * So an error report carries the exception **name** and never its message, the
 * **route template** rather than the URL (an entry id in a path is not an
 * identifier in a report), stack frames as **positions only**, and no user, no
 * storage contents, no form state, no query string.
 *
 * ## Why no vendor
 *
 * Same reason as the backend: where an error sink lands is a DPDP transfer and
 * retention question before it is a pricing one, and that belongs to the owner.
 * The seam is `addSink`; the default sink writes one structured line to the
 * console, which is what a bug report from a tester actually carries today.
 */

/** Everything allowed to leave the browser about one failure. */
export type ErrorEvent = {
  /** The error's constructor name. Never its message. */
  kind: string;
  /** Stable across occurrences of the same fault, so they can be counted. */
  fingerprint: string;
  /** `route <template>` — where it happened, with ids removed. */
  where: string;
  /** How it reached us: a render boundary, a stray promise, a window error. */
  via: "boundary" | "unhandledrejection" | "window";
  /** `file:line` positions, innermost last. No source text, no variables. */
  frames: string[];
};

export type Sink = (event: ErrorEvent) => void;

/**
 * Path segments that are identifiers rather than route structure.
 *
 * Mirrors `template_path` in the backend. Conservative in one direction only:
 * a segment that MIGHT be an id is replaced, because a false `{id}` costs a
 * little grouping precision and a false passthrough leaks a row key.
 */
const UUID = /^[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}$/i;
const NUMERIC = /^\d+$/;
const OPAQUE = /^[A-Za-z0-9_-]{24,}$/;

export function templatePath(path: string): string {
  return path
    .split("/")
    .map((segment) => {
      if (!segment) return segment;
      if (UUID.test(segment) || NUMERIC.test(segment) || OPAQUE.test(segment)) return "{id}";
      return segment;
    })
    .join("/");
}

/**
 * A stack frame reduced to a position.
 *
 * Browser stacks are free text and differ per engine, so this parses the two
 * shapes that matter (V8's `at fn (url:line:col)` and Firefox/Safari's
 * `fn@url:line:col`) and keeps only the file's basename plus the line. The URL
 * is dropped to its last segment on purpose: a full origin adds nothing to
 * grouping, and a query string on a bundle URL can carry a build token.
 */
export function frameOf(line: string): string | null {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith("Error") || trimmed.startsWith("TypeError")) return null;
  const v8 = /^at\s+(?:(.+?)\s+\()?(.+?):(\d+):(\d+)\)?$/.exec(trimmed);
  const other = /^(.*?)@(.+?):(\d+):(\d+)$/.exec(trimmed);
  const match = v8 ?? other;
  if (!match) return null;
  const fn = (match[1] || "anonymous").trim();
  const file = match[2].split("?")[0].split("/").pop() || "bundle";
  return `${file}:${match[3]} in ${fn}`;
}

export function framesOf(stack: string | undefined, limit = 12): string[] {
  if (!stack) return [];
  const out: string[] = [];
  for (const line of stack.split("\n")) {
    const frame = frameOf(line);
    if (frame) out.push(frame);
  }
  return out.slice(0, limit);
}

/**
 * A stable, short hash. Not cryptographic — it only has to group.
 *
 * FNV-1a rather than anything from `crypto`, because this runs on an error
 * path that must work synchronously, in every browser, with no await.
 */
export function fingerprintOf(kind: string, where: string, innermost: string): string {
  const basis = `${kind}|${where}|${innermost}`;
  let hash = 0x811c9dc5;
  for (let i = 0; i < basis.length; i++) {
    hash ^= basis.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(16).padStart(8, "0");
}

/** The always-on sink: one structured line, no interpolated user content. */
const consoleSink: Sink = (event) => {
  // eslint-disable-next-line no-console
  console.error("error_event", JSON.stringify(event));
};

let sinks: Sink[] = [consoleSink];

export function addSink(sink: Sink): void {
  sinks.push(sink);
}

/** Back to console-only. For tests, and for a clean re-init. */
export function resetSinks(): void {
  sinks = [consoleSink];
}

/**
 * Scrub, fingerprint and dispatch one failure. Never throws.
 *
 * A crash inside the error reporter must not become the error — this runs at
 * the moment the app is already broken.
 */
export function capture(
  error: unknown,
  opts: { via: ErrorEvent["via"]; path?: string },
): ErrorEvent {
  const err = error instanceof Error ? error : undefined;
  // A thrown string or object has no constructor worth naming, and its own
  // contents are exactly what must not travel — so it is reported by shape.
  const kind = err ? err.name || "Error" : `Non-Error(${typeof error})`;
  const path =
    opts.path ?? (typeof window === "undefined" ? "" : window.location.pathname);
  const where = `route ${templatePath(path)}`;
  const frames = framesOf(err?.stack);
  const event: ErrorEvent = {
    kind,
    fingerprint: fingerprintOf(kind, where, frames[frames.length - 1] ?? ""),
    where,
    via: opts.via,
    frames,
  };
  for (const sink of [...sinks]) {
    try {
      sink(event);
    } catch {
      /* a broken sink must not break the page */
    }
  }
  return event;
}

/**
 * Listen for the two failures a React boundary never sees: a rejected promise
 * nobody awaited, and a script error outside the tree.
 *
 * Returns its own teardown so a test — or a remount — cannot stack listeners.
 */
export function installGlobalHandlers(target: Window = window): () => void {
  // Typed as the base `Event` and narrowed inside, rather than cast at the
  // listener boundary: the DOM signature really is `Event`, and asserting
  // otherwise would hide the case where a browser hands us something else.
  const onRejection = (e: Event) => {
    const reason = (e as PromiseRejectionEvent).reason;
    capture(reason, { via: "unhandledrejection" });
  };
  const onError = (e: Event) => {
    const err = e as globalThis.ErrorEvent;
    capture(err.error ?? err.message, { via: "window" });
  };
  target.addEventListener("unhandledrejection", onRejection);
  target.addEventListener("error", onError);
  return () => {
    target.removeEventListener("unhandledrejection", onRejection);
    target.removeEventListener("error", onError);
  };
}
