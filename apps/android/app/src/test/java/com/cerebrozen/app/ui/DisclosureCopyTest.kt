package com.cerebrozen.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CHAT_SPEC §1.4 / §10.99 — the app must not call itself a **companion**.
 *
 * This is a compliance test, not a style one. CA SB243 and NY GBL art. 47 attach their
 * obligations to *companion* systems; the engine ships a code-held guardrail forbidding
 * companion framing (`guardrails.NON_COMPANION`) and `/v1/governance` attests
 * "non-companion by design". Until 2026-07-21 the Android disclosure pill read
 * "AI companion — not a therapist or crisis service" — so the one sentence written to
 * satisfy those statutes used the exact word that invokes them, and contradicted an
 * attestation we serve over an API.
 *
 * A device pass found it; nothing mechanical would have. Hence this: it reads the shipped
 * XML rather than a `R.string` constant, so it covers **every locale we ship**, and it
 * fails on the next string as well as this one — the failure mode is a new sentence
 * written months from now by someone who never read the spec.
 *
 * What it deliberately does NOT police: resource **ids** (`companion_title`,
 * `you_companion_*`) and the `companion` profile field they mirror. That field is a
 * cross-stack contract (CLAUDE.md rule 7 — `PATCH /users/me`, engine-side persona
 * selection); renaming it is a protocol change, not a copy fix, and no user ever sees it.
 * The line this test draws is the one the statutes draw: what the product *says it is*.
 */
class DisclosureCopyTest {

    /** Gradle runs unit tests with the module dir as CWD; tolerate the repo root too. */
    private fun resDir(): File =
        listOf("src/main/res", "app/src/main/res", "apps/android/app/src/main/res")
            .map(::File)
            .firstOrNull { it.isDirectory }
            ?: error("cannot locate res/ from ${File(".").absolutePath}")

    /** Every shipped locale, as (`values-hi`, its strings.xml). */
    private fun stringFiles(): List<Pair<String, File>> =
        resDir().listFiles { f: File -> f.isDirectory && f.name.startsWith("values") }
            .orEmpty()
            .mapNotNull { dir -> File(dir, "strings.xml").takeIf(File::isFile)?.let { dir.name to it } }
            .sortedBy { it.first }

    /** `<string name="x">body</string>` → x to body. Attribute-order-tolerant, good enough
     *  for a copy scan and far cheaper than standing up a parser. */
    private val entry = Regex("""<string [^>]*name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    @Test
    fun no_shipped_string_calls_the_product_a_companion() {
        val files = stringFiles()
        assertTrue("found no strings.xml at all — the scan would vacuously pass", files.isNotEmpty())

        val offenders = files.flatMap { (locale, file) ->
            entry.findAll(file.readText())
                .filter { it.groupValues[2].contains("companion", ignoreCase = true) }
                .map { "$locale/${it.groupValues[1]} = \"${it.groupValues[2]}\"" }
        }

        assertTrue(
            "These strings call the product a companion — the word CA SB243 / NY GBL art. 47 " +
                "attach to, and which /v1/governance attests we are not. Say \"AI coach\":\n" +
                offenders.joinToString("\n") { "  · $it" },
            offenders.isEmpty(),
        )
    }

    @Test
    fun the_disclosure_pill_still_says_what_it_is_and_what_it_is_not() {
        // Deleting the word "companion" must not be achievable by deleting the disclosure.
        val en = File(resDir(), "values/strings.xml").readText()
        val pill = entry.findAll(en).first { it.groupValues[1] == "talk_disclosure_pill" }.groupValues[2]

        assertTrue("the disclosure must name what it is: $pill", pill.contains("AI", ignoreCase = false))
        assertTrue("the disclosure must still disclaim therapy: $pill", pill.contains("therapist", ignoreCase = true))
        assertTrue("the disclosure must still disclaim crisis service: $pill", pill.contains("crisis", ignoreCase = true))
        assertFalse("a disclosure may not claim humanity: $pill", pill.contains("human", ignoreCase = true))
    }

    @Test
    fun every_locale_that_translates_the_style_setting_agrees_it_is_coaching() {
        // The Hindi feature name was "साथी की शैली" — साथी is *companion*. A locale is where
        // a renamed concept quietly survives, because the reviewer cannot read it.
        val translated = stringFiles().filter { it.first != "values" }
        val stale = translated.flatMap { (locale, file) ->
            entry.findAll(file.readText())
                .filter { it.groupValues[1] in setOf("companion_title", "you_companion_title") }
                .filter { it.groupValues[2].contains("साथी") }
                .map { "$locale/${it.groupValues[1]}" }
        }
        assertTrue("still calls the style setting a companion's: $stale", stale.isEmpty())
    }
}
