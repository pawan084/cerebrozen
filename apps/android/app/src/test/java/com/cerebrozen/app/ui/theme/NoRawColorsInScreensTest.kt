package com.cerebrozen.app.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The hard rule, enforced instead of remembered: **screens read design tokens
 * only.**
 *
 * CLAUDE.md has stated this since the iOS client ("never raw hex in screens"),
 * and on 2026-08-20 a walk of all 58 Android routes found 37 opaque literals
 * that had accumulated in `ui/screens/` anyway. Two of them were shipped bugs
 * a human could not read:
 *
 *  * the crisis screen's "CereBro is not an emergency service" disclaimer —
 *    `Color(0xFF542D34)`, 10.02:1 on Dawn and **1.27:1** on Night, sitting
 *    under the call-112 banner;
 *  * the Explore hero's eyebrow — `Color(0xFF6C2768)`, 9.01:1 on Dawn and
 *    **1.89:1** on Night.
 *
 * Both had the same shape: a colour authored while looking at the light theme,
 * which is invisible in the dark one and which no token test can see, because
 * a literal never enters the token graph. [ContrastTest] gates pairings; it
 * cannot gate a colour that was never given a name. This test is the other
 * half — it gates the *absence* of names.
 *
 * **Translucent literals are allowed.** A colour with alpha below `FF` is an
 * overlay that composes over whatever is beneath it, so it cannot be
 * light-theme-only by construction: the two that remain are a 15% white
 * highlight and a 19% plum stroke on a decorative canvas. Only fully opaque
 * literals — the ones that replace a surface rather than tint it — are the bug
 * this catches.
 */
class NoRawColorsInScreensTest {

    /** `Color(0xFFRRGGBB)`: opaque, and therefore theme-blind. */
    private val opaqueLiteral = Regex("""Color\(0xFF[0-9A-Fa-f]{6}\)""")

    private fun screenSources(): List<File> {
        // The test's working directory is the Gradle module (`app/`), but do
        // not rely on it: walk up until the source root is found, so this keeps
        // working from an IDE runner or a different invocation directory.
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "src/main/java/com/cerebrozen/app/ui/screens").isDirectory) {
            dir = dir.parentFile
        }
        val root = requireNotNull(dir) { "could not locate the app module from ${File("").absolutePath}" }
        return File(root, "src/main/java/com/cerebrozen/app/ui/screens")
            .walkTopDown().filter { it.extension == "kt" }.toList()
    }

    @Test
    fun screens_carry_no_opaque_color_literals() {
        val sources = screenSources()
        // A gate over an empty file list passes vacuously and tells you nothing.
        assertTrue(
            "found no screen sources to check — the path in this test is wrong",
            sources.size > 5,
        )

        val offenders = sources.flatMap { file ->
            file.readLines().mapIndexedNotNull { i, line ->
                opaqueLiteral.find(line)?.let { "${file.name}:${i + 1}  ${it.value}" }
            }
        }

        assertTrue(
            buildString {
                append("screens must read design tokens only, but ")
                append(offenders.size)
                append(" opaque colour literal(s) are hardcoded:\n")
                offenders.forEach { append("    ").append(it).append('\n') }
                append("\nAdd the colour to ui/theme/Color.kt as a per-theme token and use that.\n")
                append("A literal here is invisible to ContrastTest and ships as a Night-mode bug.")
            },
            offenders.isEmpty(),
        )
    }
}
