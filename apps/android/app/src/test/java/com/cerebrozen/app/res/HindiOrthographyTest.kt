package com.cerebrozen.app.res

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Hindi strings are text nobody on this project proof-reads by eye.
 *
 * They are also the strings most often touched by tooling rather than typed:
 * every Hindi edit in this repo has gone through a shell heredoc at some point,
 * and that path has corrupted them twice — once by double-encoding (`\uXXXX`
 * parsed as an escape, then re-encoded, so the file held the six literal
 * characters instead of the letter) and once by dropping a byte mid-word.
 * Neither shows up as a build failure. Both render as plausible-looking
 * Devanagari that a Hindi reader sees as wrong and nobody else notices.
 *
 * The bug that prompted this: `verify_email_body` shipped आव़ाज़ for आवाज़ — a
 * nukta on व, which is not a letter Hindi has. It survived a code review, a
 * translation pass and a `:app:check` run, and was caught only by counting
 * codepoints. So this counts codepoints on every build instead.
 *
 * All three checks are mechanical and have no opinion about the translation
 * itself — they catch corruption, not wording.
 */
class HindiOrthographyTest {

    private val hi = File("src/main/res/values-hi/strings.xml")
    private val en = File("src/main/res/values/strings.xml")

    /**
     * Strings deliberately left in English, not a gap.
     *
     * A 2026-07-13 note recorded 123 safety strings — crisis copy, TIPP (a DBT
     * crisis-intervention skill), crisis grounding, underage routing — held
     * back from Hindi on purpose, pending a clinical reviewer, and still
     * externally blocked as of 2026-08-03. A later session (2026-08-24) did not
     * have that note in context, translated everything including these, and
     * shipped it. The conflict was caught and flagged to the owner before
     * anything else changed, who chose to revert: these 88 keys (the ones that
     * session actually touched, matched against that description) are back to
     * English here, unlike the ~667 other strings from the same pass, which
     * were not blocked on anything and stayed.
     *
     * This set exists so the coverage floor below does not re-flag these as a
     * regression, and so a future session sees WHY they are missing instead of
     * "translating" them again into the same unreviewed state. Removing a key
     * from this set is a clinical-review decision, not a translation one — see
     * docs/TODO.md.
     */
    private val PENDING_CLINICAL_REVIEW = setOf(
        "crisis_cached_title", "crisis_call_emergency", "crisis_call_line", "crisis_cannot_call",
        "crisis_cannot_call_detail", "crisis_change_region", "crisis_emergency_detail",
        "crisis_headline", "crisis_immediate_danger", "crisis_mental_generic", "crisis_mental_india",
        "crisis_mental_other", "crisis_not_emergency", "crisis_not_verified", "crisis_open_safety_plan",
        "crisis_reach_plain", "crisis_reach_verified", "crisis_region_language", "crisis_screen_subtitle",
        "crisis_screen_title", "crisis_sources", "crisis_trusted_setup_detail", "crisis_trusted_setup_title",
        "crisis_trusted_title", "crisis_unverified_note", "crisis_verified",
        "explore_support_subtitle", "explore_support_title",
        "guest_gate_safetyplan", "humansupport_line_detail",
        "ob_danger_line", "ob_immediate_danger", "ob_immediate_danger_sub",
        "ob_underage_bar_title", "ob_underage_card", "ob_underage_card_sub", "ob_underage_eyebrow",
        "ob_underage_sub", "ob_underage_title", "ob_underage_urgent", "ob_underage_welcome",
        "ob_urgent_support",
        "ocg_call", "ocg_confirm_body", "ocg_confirm_open", "ocg_confirm_title", "ocg_contact_name",
        "ocg_contact_number", "ocg_contact_required", "ocg_contact_save", "ocg_contact_title",
        "ocg_emergency_disclaimer", "ocg_hear", "ocg_hear_body", "ocg_open_breath", "ocg_see",
        "ocg_see_body", "ocg_slow_breath", "ocg_slow_breath_body", "ocg_smell", "ocg_smell_body",
        "ocg_subtitle", "ocg_taste", "ocg_taste_body", "ocg_title", "ocg_touch", "ocg_touch_body",
        "safetyplan_loading",
        "tipp_done", "tipp_eyebrow", "tipp_intro", "tipp_previous", "tipp_progress",
        "tipp_step1_how", "tipp_step1_title", "tipp_step1_why",
        "tipp_step2_how", "tipp_step2_title", "tipp_step2_why",
        "tipp_step3_how", "tipp_step3_title", "tipp_step3_why",
        "tipp_step4_how", "tipp_step4_title", "tipp_step4_why",
        "tipp_title", "tipp_why",
        "work_crisis_chip",
    )

    /** `<string name="x">body</string>` → name to body. */
    private fun strings(f: File): Map<String, String> =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(f.readText())
            .associate { it.groupValues[1] to it.groupValues[2] }

    /**
     * A nukta (U+093C) may only sit on a base that actually takes one.
     *
     * Devanagari composes क़ ख़ ग़ ज़ ड़ ढ़ फ़ य़ this way; every other base is a
     * typo or a mangled byte. Written as escapes rather than literals so the
     * test means the same thing whatever encoding this file is read in — which
     * is exactly the failure mode it is guarding against.
     */
    @Test
    fun `every nukta sits on a letter that takes one`() {
        val nukta = '़'
        val legal = setOf(
            'क', // क  -> क़
            'ख', // ख  -> ख़
            'ग', // ग  -> ग़
            'ज', // ज  -> ज़
            'ड', // ड  -> ड़
            'ढ', // ढ  -> ढ़
            'फ', // फ  -> फ़
            'य', // य  -> य़
        )
        val offenders = strings(hi).flatMap { (name, body) ->
            body.mapIndexedNotNull { i, c ->
                if (c == nukta && i > 0 && body[i - 1] !in legal) {
                    "$name: U+%04X does not take a nukta (…%s…)".format(
                        body[i - 1].code,
                        body.substring(maxOf(0, i - 6), minOf(body.length, i + 4)),
                    )
                } else {
                    null
                }
            }
        }
        assertTrue(
            "these Devanagari letters carry a nukta they cannot have:\n  " +
                offenders.joinToString("\n  "),
            offenders.isEmpty(),
        )
    }

    /**
     * No leftovers from an encoding round-trip.
     *
     * U+FFFD is a byte that failed to decode — always corruption, never intent.
     *
     * The escape check is deliberately narrow. `\uXXXX` is legal in an Android
     * string resource and aapt2 unescapes it, so banning escapes outright is
     * wrong: this file uses `\u00b7` for a middle dot on purpose, and the first
     * version of this test failed on it. What is never intentional is a
     * *Devanagari* letter written as an escape — nobody hand-writes `\u0905` for
     * a string they can type — so that, and only that, is the signature of the
     * encode/decode round-trip that mangled this file before.
     */
    @Test
    fun `the file holds Devanagari, not escapes or replacement characters`() {
        val text = hi.readText()
        assertEquals("the file contains U+FFFD — a byte failed to decode", 0, text.count { it == '�' })
        val devanagari = Regex("""\\u09[0-7][0-9a-fA-F]""", RegexOption.IGNORE_CASE)
            .findAll(text).map { it.value }.toList()
        assertTrue(
            "Devanagari letters are written as escapes rather than as letters, which is " +
                "what an encode/decode round-trip leaves behind: $devanagari",
            devanagari.isEmpty(),
        )
    }

    /**
     * A translated string must take the same format arguments as its original.
     *
     * This one is not cosmetic: `String.format` throws when a `%1$s` goes
     * missing, so a dropped placeholder is a crash on the screen that shows it,
     * in Hindi only — the configuration least likely to be exercised before
     * release. `verify_email_body` interpolates the address and is exactly the
     * shape that fails this way.
     */
    @Test
    fun `format placeholders match the English original`() {
        val spec = Regex("""%(\d+\$)?[a-z]""")
        val english = strings(en)
        val mismatches = strings(hi).mapNotNull { (name, body) ->
            val original = english[name] ?: return@mapNotNull null
            val want = spec.findAll(original).map { it.value }.sorted().toList()
            val got = spec.findAll(body).map { it.value }.sorted().toList()
            if (want != got) "$name: English has $want, Hindi has $got" else null
        }
        assertTrue(
            "translated strings dropped or invented a format argument:\n  " +
                mismatches.joinToString("\n  "),
            mismatches.isEmpty(),
        )
    }

    /**
     * Hindi is complete outside [PENDING_CLINICAL_REVIEW], and stays that way.
     *
     * A device walk on 2026-08-24 found 758 of 2025 strings (37%) with no Hindi
     * at all. The gap tracked recency rather than importance — the oldest
     * screens were done, while the sleep module, Health Connect, onboarding,
     * the crisis screen and every guided module had shipped their strings into
     * `values/` and stopped. On the Home tab the only call to action rendered
     * as "Start" inside an otherwise Hindi screen.
     *
     * Everything except the 88 keys in [PENDING_CLINICAL_REVIEW] is translated,
     * so the floor is 100 over the remaining set: a new string without its
     * Hindi fails the build. `MissingTranslation` does not catch this — lint
     * was clean the whole time the app was 37% English — so this is the only
     * thing standing between the product and a slow relapse to a
     * half-translated UI.
     *
     * Four strings outside the pending set are legitimately identical to
     * English and counted as translated: `app_name` and `talk_companion_name`
     * (the brand), `auth_email_placeholder` (an example address), and
     * `ground_counter` (5 · 4 · 3 · 2 · 1).
     *
     * Never lower this floor, and never shrink [PENDING_CLINICAL_REVIEW] to
     * make a build green — only a clinical-review decision does that.
     */
    @Test
    fun `hindi coverage does not go backwards`() {
        val floor = 100.0
        val english = strings(en).keys - PENDING_CLINICAL_REVIEW
        val hindi = strings(hi)
        val covered = english.count { it in hindi }
        val pct = 100.0 * covered / english.size
        assertTrue(
            "Hindi coverage fell to %.1f%% (%d of %d strings, excluding the %d pending clinical " +
                "review); the floor is %.1f%%. A new screen was added without its values-hi strings.".format(
                    pct, covered, english.size, PENDING_CLINICAL_REVIEW.size, floor,
                ),
            pct >= floor,
        )
    }
}
