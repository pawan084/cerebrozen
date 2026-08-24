package com.cerebrozen.app.res

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No new user-facing English may be hardcoded in Compose.
 *
 * The 54-route Hindi device walk (2026-08-24) found four screens rendering
 * mixed English/Hindi — the check-in sheet's only heading, Explore's whole
 * hero, gratitude, the grounding intro — because their copy lived in
 * `Text("...")` literals. **Neither existing gate can see that class**:
 * `HindiOrthographyTest`'s 100% coverage floor audits `values/` against
 * `values-hi/`, so a string that never reaches `values/` is invisible to it;
 * and lint's `HardcodedText` check only inspects XML layouts, so it reports
 * zero findings against a Compose codebase. Both gates were green while the
 * product's most-used sheet was untranslatable.
 *
 * So this scans the source. Any quoted multi-word English sentence in a
 * Kotlin file under `ui`, outside comments, that is not in the allowlist
 * below fails the build. (The path is spelled out in words because a glob
 * here would contain a slash-star, Kotlin block comments NEST, and this very
 * comment would then swallow the file to the first star-slash string literal
 * — which is exactly how this test's first draft failed to compile.) The heuristic is deliberately narrow — a capitalised word followed by
 * two more words — because a guard that cries wolf gets deleted; single words
 * and identifiers pass freely, and the walk showed sentences are where the
 * real leaks live.
 *
 * The allowlist is not an escape hatch; each entry names a REASON:
 *  - wire values: strings the server receives, contractually English
 *    (`MoodOption.name/note`, `StateOption` goals — see their doc comments)
 *  - match keys: literals compared against server data to pick a resource
 *  - ConsentNotice.kt: the DPDP notice ships its own 13-language map with an
 *    in-app picker, deliberately outside the resource system
 * Adding an entry to cover a rendered string is the bug this test exists to
 * catch — extract to strings.xml (plus values-hi) instead.
 */
class ComposeHardcodedStringTest {

    private val uiRoot = File("src/main/java/com/cerebrozen/app/ui")

    /** Files whose literal English is by design, with the reason above. */
    private val exemptFiles = setOf(
        "ConsentNotice.kt", // hand-shipped 13-language notice, picker-driven
    )

    /** Line-level exemptions: substring → reason (documented, auditable). */
    private val exemptLines = listOf(
        "-> R.string." to "match key mapping a wire literal to a resource",
        "MoodOption(" to "wire taxonomy: name/note are the server contract",
        "StateOption(" to "wire goals/motivations that seed the plan",
        "GameSpec(" to "wire ids for the games registry",
        // `"Wire" -> "Wire"`: a when-arm mapping one wire literal to another
        // (onboardingMoodNote — its doc says "never translated", the note IS
        // the stored wire value, hand-duplicated with iOS/web). A line whose
        // shape is string-arrow-string renders nothing by itself.
        "\" -> \"" to "wire-to-wire mapping in a when expression",
    )

    private val sentence = Regex("\"([A-Z][a-z]+ [a-zA-Z0-9'’,.:—-]+ [a-zA-Z0-9'’,.:—&()/-]+[^\"]*)\"")

    @Test
    fun `no rendered English sentence bypasses the resource system`() {
        val offenders = mutableListOf<String>()
        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in exemptFiles }
            .forEach { f ->
                var inBlockComment = false
                f.readLines().forEachIndexed { idx, raw ->
                    val trimmed = raw.trim()
                    if (inBlockComment) {
                        if ("*/" in trimmed) inBlockComment = false
                        return@forEachIndexed
                    }
                    if (trimmed.startsWith("/*")) {
                        if ("*/" !in trimmed) inBlockComment = true
                        return@forEachIndexed
                    }
                    if (trimmed.startsWith("//") || trimmed.startsWith("*")) return@forEachIndexed
                    val code = raw.substringBefore("//")
                    if (exemptLines.any { (needle, _) -> needle in code }) return@forEachIndexed
                    sentence.findAll(code).forEach { m ->
                        offenders += "${f.name}:${idx + 1}  \"${m.value.trim('"').take(60)}\""
                    }
                }
            }
        assertTrue(
            "hardcoded English sentences in Compose — invisible to the Hindi coverage " +
                "ratchet AND to lint (HardcodedText is XML-only). Extract each to " +
                "strings.xml + values-hi, or if it is genuinely a wire value or match " +
                "key, add a documented exemption:\n  " + offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }
}
