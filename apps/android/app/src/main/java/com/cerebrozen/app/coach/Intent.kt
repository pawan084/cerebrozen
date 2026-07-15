package com.cerebrozen.app.coach

/**
 * Intent → inline card, for the Coach chat.
 *
 * When what the person is talking about clearly maps to a tool the app already has —
 * sleep, a racing mind, spiralling, an overthought fear, needing a human — the coach can
 * surface that tool as a CARD right in the conversation, one tap from acting on it. This
 * is the small, deterministic cousin of the reference's admin-configured intent router:
 * no model call, no rules server — a keyword screen over the turn, so it is instant,
 * offline, and testable.
 *
 * Deliberately conservative:
 * - **First match wins, at most one card per turn.** A card is an offer, not a reflex;
 *   an ordinary conversational turn stays plain text, which is most turns.
 * - **Crisis is NOT handled here.** Suicide/self-harm routing is the engine's
 *   deterministic takeover (safety is code, not a keyword card). The "talk to a person"
 *   card below is for ordinary loneliness/overwhelm, never a substitute for that.
 *
 * Pure Kotlin (no Android/Compose) so the mapping is JVM-unit-testable; the UI maps
 * [Suggestion.icon]/[Suggestion.accent] keys to real icons and colours.
 */
data class Suggestion(
    val id: String,        // stable key — also used to de-duplicate repeats in a session
    val title: String,
    val subtitle: String,
    val cta: String,       // button label
    val route: String,     // NavHost route the card opens
    val icon: String,      // icon key → ImageVector in the UI
    val accent: String,    // "cyan" | "peri" | "warm"
)

private class Rule(val words: List<String>, val make: () -> Suggestion)

private val RULES: List<Rule> = listOf(
    Rule(
        listOf("can't sleep", "cant sleep", "cannot sleep", "insomnia", "no sleep", "sleepless",
            "up all night", "lying awake", "wired at night", "can't switch off", "cant switch off"),
    ) { Suggestion("sleep", "Wind down for sleep", "A slower evening in four small steps.", "Open", "winddown", "bedtime", "cyan") },
    Rule(
        listOf("anxious", "anxiety", "panic", "panicking", "overwhelmed", "stressed", "so much stress",
            "racing", "on edge", "nervous", "tense", "freaking out", "heart is pounding", "can't breathe",
            "breathing", "take a breath", "deep breath"),
    ) { Suggestion("breathe", "Take a slow minute", "A guided four-in, four-out reset.", "Breathe", "breathe/reset", "air", "cyan") },
    Rule(
        listOf("spiraling", "spiralling", "can't focus", "cant focus", "scattered", "can't think straight",
            "ground me", "grounding", "present moment", "out of my body", "dissociat"),
    ) { Suggestion("ground", "Come back to now", "5-4-3-2-1 — steady through your senses.", "Ground", "toolkit", "spa", "cyan") },
    Rule(
        listOf("worst case", "catastroph", "negative thought", "reframe", "overthinking", "keep thinking",
            "what if", "beating myself", "my fault", "i always", "i never"),
    ) { Suggestion("reframe", "Reframe the thought", "Separate the fact from the story you're telling.", "Try it", "cbt", "psychology", "peri") },
    Rule(
        listOf("so alone", "feel alone", "lonely", "no one to talk", "need to talk to someone",
            "need a human", "real person", "a therapist", "professional help"),
    ) { Suggestion("human", "Talk to a person", "Support paths, for when words aren't enough alone.", "See options", "humansupport", "diversity", "warm") },
)

/**
 * The single best card for this turn, or null when nothing clearly matches.
 * Scans the person's message plus (as a weaker signal) the coach's reply.
 */
fun detectIntent(userText: String, coachText: String = ""): Suggestion? {
    val hay = (userText + "  " + coachText).lowercase()
    return RULES.firstOrNull { rule -> rule.words.any { it in hay } }?.make()
}
