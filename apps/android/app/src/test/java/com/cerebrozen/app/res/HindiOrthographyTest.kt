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
     * Hindi coverage may improve; it may not regress.
     *
     * A device walk on 2026-08-24 found 758 of 2025 strings (37%) with no Hindi
     * at all, and the gap is not evenly spread — it tracks recency. The oldest
     * screens are fully translated; the sleep module, Health Connect and the
     * V3 home hero are largely not, because each shipped its strings into
     * `values/` and stopped. So the Home tab renders in Hindi with an English
     * "Start" as its only call to action.
     *
     * Translating the long tail is its own piece of work and is tracked in
     * docs/TODO.md. What this test does is stop the number getting worse in the
     * meantime: adding a screen without its Hindi now fails the build, which is
     * the only reason the gap grew to 37% unnoticed in the first place.
     *
     * The floor is a RATCHET, not a target. Raise it when coverage improves —
     * never lower it to make a build green.
     */
    @Test
    fun `hindi coverage does not go backwards`() {
        val floor = 62.0
        val english = strings(en)
        val hindi = strings(hi)
        val covered = english.keys.count { it in hindi }
        val pct = 100.0 * covered / english.size
        assertTrue(
            "Hindi coverage fell to %.1f%% (%d of %d strings); the floor is %.1f%%. ".format(
                pct, covered, english.size, floor,
            ) + "A new screen was added without its values-hi strings.",
            pct >= floor,
        )
    }
}
