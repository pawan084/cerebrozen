/**
 * Today's hero — the one decision at full volume (TOD-01, ref/web.html).
 *
 * CROSS-STACK CONTRACT, hand-duplicated per CLAUDE.md. The authority is
 * `apps/android/.../ui/screens/TodayScreen.kt`:
 *   heroKindFor()      → TodayScreen.kt:778
 *   OFFLINE_HERO_ROUTES → TodayScreen.kt:791
 *   heroWhyRes()       → TodayScreen.kt:808
 * Change one side and change the other in the same commit.
 *
 * There is no unit-test runner in apps/app, so unlike the Android twin these
 * functions are only covered by e2e. Keep them pure so that stays cheap.
 */

export type HeroKind = "loading" | "fallback" | "plan-step" | "plan-done";

/**
 * Which hero to show. Mirrors heroKindFor() exactly, including the order of
 * the branches — `!planLoaded && !hasPlan` first, so a slow fetch reads as
 * loading rather than as "you have no plan".
 */
export function heroKindFor(
  planLoaded: boolean,
  hasPlan: boolean,
  hasNextStep: boolean,
): HeroKind {
  if (!planLoaded && !hasPlan) return "loading";
  if (!hasPlan) return "fallback";
  return hasNextStep ? "plan-step" : "plan-done";
}

/**
 * Routes that genuinely run with no network — the only ones the hero may badge
 * "Works offline".
 *
 * `sounds` is deliberately absent: a soundscape streams unless it was
 * downloaded first, and `talk` needs a model on the other end. A chip that
 * promises offline and then fails on the Mumbai local is worse than no chip.
 *
 * These are the WEB routes, so they differ in spelling from the Android set
 * while meaning the same thing (Android: "breathe/reset", web: "/games").
 */
const OFFLINE_HERO_ROUTES: ReadonlySet<string> = new Set([
  "/games",
  "/games/imagery",
  "/games/ritual",
  "/safety-plan",
]);

export function heroWorksOffline(route: string): boolean {
  return OFFLINE_HERO_ROUTES.has(route);
}

/**
 * The provenance sentence — what the recommendation read, and what it did not.
 *
 * This MUST branch on the plan's real generator. The rule generator never sees
 * the journal at all; the AI planner sends recent journal *titles* (never
 * bodies, and only under journal_memory consent — backend
 * services/agentic.py::_recent_signals). A flat "it did not read your journal"
 * would therefore be a false privacy statement half the time, which is exactly
 * the class of claim CLAIMS_MAP exists to stop.
 *
 * Wording is kept in step with android values/strings.xml
 * `today_hero_why_rule` / `today_hero_why_ai`.
 */
export function heroWhy(source: string | undefined | null): string {
  return source && source.toLowerCase() === "ai"
    ? "Shaped from your goal, your recent check-ins, your sleep diary and your journal titles. It did not read the entries themselves."
    : "Chosen from your goal, your recent check-ins and your sleep diary. It did not read your journal.";
}
