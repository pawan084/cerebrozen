package com.cerebrozen.app.res

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every declared string is referenced by something.
 *
 * The V3–V5 redesign retired whole screens — the old Today, the intro tour,
 * three games, the voice landing — and their strings stayed behind: by
 * 2026-08-24 the file carried **309 dead keys (~15%)**, every one dutifully
 * translated, some hand-repaired for encoding, all of it effort spent on copy
 * no screen could ever show. Lint's UnusedResources sees them but sits among
 * hundreds of warnings nobody reads; nothing FAILED.
 *
 * This fails. A key with no `R.string.` reference in main/test/androidTest
 * sources and no `@string/` reference in res or the manifest is a corpse, and
 * the build says so by name.
 *
 * One boundary this scan cannot see, learned the expensive way: OTHER STACKS
 * may read this strings.xml as a fixture. The first sweep deleted the retired
 * tour's keys — correctly, per every rule above — and the WEB's cross-client
 * copy-parity suite (tests/app/GuidedTour.test.tsx) broke, because it parsed
 * this file for keys of a feature Android no longer ships. The resolution was
 * to fix THAT test's premise (parity sources must be features, not files),
 * not to resurrect dead keys here. Before deleting a swept key, a repo-wide
 * grep for its name is cheap insurance; a red web suite after a sweep means a
 * stale fixture, not a wrong sweep. Staging keys ahead of a screen that hasn't landed
 * is the one legitimate exception — stage them WITH the screen in one commit,
 * or add a temporary allowlist entry carrying the commit that will consume it.
 */
class StringGraveyardTest {

    /** Keys declared ahead of consuming code, each with its reason. Empty is
     * the healthy state; entries should not survive their own follow-up. */
    private val staged: Set<String> = emptySet()

    private val refPattern = Regex("""R\.string\.([a-z0-9_]+)""")
    private val xmlRefPattern = Regex("""@string/([a-z0-9_]+)""")
    private val declPattern = Regex("""<string name="([a-z0-9_]+)"""")

    @Test
    fun `no string is declared that nothing references`() {
        val refs = mutableSetOf<String>()
        listOf("src/main/java", "src/test/java", "src/androidTest/java").forEach { root ->
            File(root).walkTopDown()
                .filter { it.isFile && (it.extension == "kt" || it.extension == "xml") }
                .forEach { f ->
                    val s = f.readText()
                    refPattern.findAll(s).forEach { refs += it.groupValues[1] }
                }
        }
        (File("src/main/res").walkTopDown().filter { it.isFile && it.extension == "xml" } +
            sequenceOf(File("src/main/AndroidManifest.xml")))
            .forEach { f ->
                xmlRefPattern.findAll(f.readText()).forEach { refs += it.groupValues[1] }
            }

        val declared = declPattern.findAll(File("src/main/res/values/strings.xml").readText())
            .map { it.groupValues[1] }.toSet()
        val dead = (declared - refs - staged).sorted()
        assertTrue(
            "${dead.size} string(s) declared that no code, resource or manifest references — " +
                "retire them from values/ AND values-hi/, or stage them with the screen " +
                "that consumes them:\n  " + dead.joinToString("\n  "),
            dead.isEmpty(),
        )
    }
}
