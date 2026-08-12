package com.cerebrozen.app.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every `AppSwitch` must be given an accessible name.
 *
 * A Compose `Switch` has no text of its own — on all fourteen call sites the
 * visible label is a *sibling* `Text`, which is a separate semantics node. A
 * screen reader therefore announced "switch, on" and nothing about what it
 * governed. That was true of the seven DPDP consent toggles, the 18-or-older
 * age gate, the journal lock and the trusted-contact crisis permission: the
 * surfaces where "specific and informed" is a legal standard, not a nicety.
 * A control with no accessible name also fails WCAG 4.1.2 outright.
 *
 * `AppSwitch` now takes `label` as a required parameter, so the compiler
 * catches a bare one. This test guards the second failure mode the compiler
 * cannot see: passing something that is not a real name. It is a source scan
 * rather than a Robolectric render because the point is to cover *every* call
 * site at once, including ones on screens no instrumented test reaches.
 */
class SwitchLabelTest {

    private val uiRoot = File("src/main/java/com/cerebrozen/app/ui")

    /**
     * Each call site as (location, full argument list).
     *
     * Bracket-matched rather than read line by line: a call whose arguments wrap
     * across lines is normal Kotlin, and a single-line scan reported one of these
     * as unlabelled when the label was simply on the next line. A test that is
     * wrong about its own subject is worse than no test.
     */
    private fun callSites(): List<Pair<String, String>> =
        uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { f ->
                val src = f.readText()
                Regex("""AppSwitch\(""").findAll(src)
                    .filterNot { src.startsWith("fun AppSwitch", maxOf(0, it.range.first - 4)) }
                    .map { m ->
                        var depth = 0
                        var end = m.range.last
                        for (i in m.range.last until src.length) {
                            when (src[i]) {
                                '(' -> depth++
                                ')' -> {
                                    depth--
                                    if (depth == 0) { end = i; break }
                                }
                            }
                        }
                        val line = src.take(m.range.first).count { it == '\n' } + 1
                        "${f.name}:$line" to src.substring(m.range.first, end + 1)
                    }
            }
            .toList()

    @Test
    fun `there are call sites to check`() {
        // Guards the scan itself: if the component were renamed, every other
        // assertion here would vacuously pass over an empty list.
        assertTrue("found no AppSwitch call sites — has it been renamed?", callSites().size >= 10)
    }

    @Test
    fun `every switch passes a label`() {
        val unlabelled = callSites().filterNot { (_, line) -> "label = " in line }
        assertTrue(
            "these switches have no accessible name, so a screen reader announces only " +
                "\"switch, on\": $unlabelled",
            unlabelled.isEmpty(),
        )
    }

    @Test
    fun `no switch is labelled with a bare literal`() {
        // A hardcoded English string here would be announced untranslated to a
        // Hindi user and would sidestep the strings.xml externalisation the rest
        // of the app follows. Labels come from stringResource or an already-
        // localized variable.
        val literal = Regex("""label\s*=\s*"""")
        val offenders = callSites().filter { (_, line) -> literal.containsMatchIn(line) }
        assertTrue("switch labels must be localized, not literal: $offenders", offenders.isEmpty())
    }
}
