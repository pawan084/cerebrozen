// The durable write queue behind "your check-in is saved even with no signal".
//
// Android has had one since the metro problem: a mood logged without a signal
// simply failed, the POST threw, and the tap was gone. The browser client had
// no equivalent — reads are cached by the browser, but writes, the half the
// user actually authored, had nothing. A check-in tapped on a train was lost.
//
// The shape mirrors `net/Outbox.kt` deliberately, because the server contract
// is shared:
//
//  * **Persisted, not in-memory.** localStorage, so a closed tab or a killed
//    browser does not lose the writes the queue exists to protect.
//  * **The idempotency key is minted when the item is QUEUED**, never at send
//    time. A retry after a crash must reuse the key of an attempt that may
//    already have reached the server, or the replay creates a second check-in.
//    See `backend/app/services/idempotency.py`.
//  * **Order is preserved and one failure stops the drain.** The queue is a
//    journal of what the person did, in the order they did it.
//  * **A 4xx is rethrown, never queued.** The request is wrong; hiding that
//    behind "saved, will sync" is a lie discovered later. Only 408/429/5xx and
//    outright network failures are retryable — the same predicate as Android.

import { FreeLimitError, api } from "./api";

// Kept in sync by hand with `PERSONAL_KEYS` in lib/api.ts, which is what
// clears it on sign-out. A queue that outlives its author is a queue that
// posts one person's check-ins into the next person's account.
const QUEUE_KEY = "cerebro_app_outbox";
/** Bounded so a long offline spell cannot fill the origin's storage quota and
 *  break the app it is meant to protect. Oldest-first, like the drain. */
const MAX_ITEMS = 50;

export type OutboxItem = {
  key: string;
  path: string;
  body: unknown;
  queuedAt: number;
};

function read(): OutboxItem[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = window.localStorage.getItem(QUEUE_KEY);
    return raw ? (JSON.parse(raw) as OutboxItem[]) : [];
  } catch {
    return [];
  }
}

function write(items: OutboxItem[]): void {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(QUEUE_KEY, JSON.stringify(items.slice(-MAX_ITEMS)));
  } catch {
    // A full or blocked store must not take the app down with it.
  }
}

export function pendingCount(): number {
  return read().length;
}

function newKey(): string {
  const c = globalThis.crypto;
  if (c && "randomUUID" in c) return c.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function enqueue(item: OutboxItem): void {
  write([...read(), item]);
  announce(0);
}

/** One event, two audiences: the shell shows "n waiting to send", and a screen
 *  that was showing server data can refetch once `sent > 0` means the server
 *  has finally heard about it. */
export const OUTBOX_EVENT = "cerebro:outbox";

function announce(sent: number): void {
  if (typeof window === "undefined") return;
  window.dispatchEvent(
    new CustomEvent(OUTBOX_EVENT, { detail: { pending: pendingCount(), sent } }),
  );
}

/**
 * Send one write now, queueing it when the network refuses.
 *
 * Returns the server's response, or `null` when the write was queued — the
 * caller shows the entry either way, because from the person's side it did
 * happen.
 */
export async function send<T = any>(path: string, body: unknown): Promise<T | null> {
  const item: OutboxItem = { key: newKey(), path, body, queuedAt: Date.now() };
  try {
    return await api<T>(path, {
      method: "POST",
      body: JSON.stringify(body),
      headers: { "Idempotency-Key": item.key },
    });
  } catch (e) {
    if (!queueable(e)) throw e;
    enqueue(item);
    return null;
  }
}

/**
 * Is this failure worth keeping for later?
 *
 * Only two cases are: the network never answered at all (fetch rejects with a
 * TypeError and there is no status), and a status the server itself says is
 * temporary. Everything else — a 4xx, a dead session, the free-tier cap — is
 * the server having decided, and "saved, will sync" would be a lie the person
 * discovers when the entry never appears.
 */
function queueable(e: unknown): boolean {
  if (e instanceof FreeLimitError) return false;
  const status = statusOf(e);
  if (status === null) return true;
  return status >= 500 || status === 408 || status === 429;
}

function statusOf(e: unknown): number | null {
  const s = (e as { status?: unknown } | null)?.status;
  return typeof s === "number" ? s : null;
}

/**
 * Drain oldest-first. Safe to call often — an empty queue costs one read and
 * no network. Stops at the first failure so order is never shuffled, and drops
 * an item the server refuses outright (a 4xx will never succeed on retry, and
 * retrying it forever would block every write behind it).
 */
export async function flush(): Promise<number> {
  let items = read();
  let sent = 0;
  while (items.length) {
    const [head, ...rest] = items;
    try {
      await api(head.path, {
        method: "POST",
        body: JSON.stringify(head.body),
        headers: { "Idempotency-Key": head.key },
      });
      sent += 1;
      items = rest;
      write(items);
    } catch (e) {
      if (!queueable(e)) {
        items = rest;
        write(items);
        continue;
      }
      break;
    }
  }
  announce(sent);
  return sent;
}

/** Flush on load and whenever the browser says the network is back. Returns a
 *  disposer so the caller's effect can clean up rather than stacking a listener
 *  per mount. */
export function startOutbox(): () => void {
  if (typeof window === "undefined") return () => {};
  void flush();
  const onOnline = () => void flush();
  window.addEventListener("online", onOnline);
  return () => window.removeEventListener("online", onOnline);
}

export const __testing = { queueable, statusOf, QUEUE_KEY };