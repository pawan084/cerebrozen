package com.cerebrozen.app

import android.app.KeyguardManager
import android.content.Context
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.cerebrozen.app.net.Session
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * Shared arrangement for the on-device end-to-end walks.
 *
 * These tests drive the real app on real hardware, where three things bite that
 * never bite a JVM suite, and each of them cost a session to learn:
 *
 *  1. **A locked device fails silently.** A keyguard blocks every activity
 *     launch, and instrumentation reports nothing — the run simply never ends
 *     (killed at ten minutes, against seconds unlocked).
 *  2. **Infinite Compose animations hang Espresso**, which waits for an idle
 *     main looper the sheen and the orb never allow. `reduceMotionOverrideForTests`
 *     is the in-app hook for that; a device setting cannot be the answer because
 *     ColorOS refuses `animator_duration_scale` to adb.
 *  3. **`fetchSemanticsNodes` throws before `setContent` runs.** The app opens
 *     on an animated splash, so a poll that treats "no hierarchy yet" as failure
 *     is a coin toss rather than a test.
 */
internal object DeviceE2E {

    /** Fails fast, with the cause, rather than hanging on a keyguard. */
    fun requireUnlocked(context: Context) {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        assertFalse(
            "the device is locked - unlock it and re-run (`adb shell input keyevent " +
                "KEYCODE_WAKEUP` only turns the screen on; the keyguard still blocks " +
                "every activity launch)",
            keyguard?.isKeyguardLocked == true,
        )
    }

    /**
     * Fail — fast, and with the cause — when something else owns the screen.
     *
     * ColorOS opens a full-screen "Security test" scan over every `adb install`
     * and leaves it up: 31 consecutive screenshots of a run showed the scanner,
     * not the app. The activity had launched, so nothing failed loudly; the
     * composition simply stayed on the splash, and the test reported "never
     * appeared on screen" about a screen nobody could see. It is also the
     * likeliest cause of the "no compose hierarchies found" flake in the crisis
     * walk.
     *
     * This only reports it. Clearing it automatically was tried and is worse:
     * Back-pressing until the app owns the root window walks straight past the
     * scanner and out to the launcher, after which the run hangs. Turning the
     * scanner off is not available either — the handset refuses
     * `settings put global verifier_verify_adb_installs 0`, the same
     * WRITE_SECURE_SETTINGS denial that put the reduce-motion hook in the app.
     *
     * The suite that needs a clean screen therefore belongs on the emulator in
     * CI, which has no such scanner; `gradlew connectedDebugAndroidTest` against
     * this handset re-triggers it on every run, while `adb shell am instrument`
     * against an already-installed pair does not.
     */
    fun requireAppInForeground(timeoutMs: Long = 15_000) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // `By.pkg(...).depth(0)` asks who owns the ROOT window, which is the
        // question that matters. `UiDevice.currentPackageName` answered
        // "com.cerebrozen.app" through an entire run that screenshots show was
        // the scanner start to finish: the app owned the task, the OEM owned
        // the screen, and a guard built on that reported success while the test
        // read a splash nobody could see.
        if (device.wait(Until.hasObject(By.pkg(BuildConfig.APPLICATION_ID).depth(0)), timeoutMs)) return
        // Say who ACTUALLY owns the screen instead of guessing. The first
        // version of this message asserted it was ColorOS's scanner — true on
        // the handset it was written against, and wrong the first time the
        // guard fired anywhere else: a CI emulator run failed here on
        // 2026-08-24 with no scanner in existence, and the message sent its
        // reader hunting one. Whatever covered the app there was transient
        // (the activity had reached RESUMED under it), which is why the
        // diagnosis matters more than the timeout.
        val owner = runCatching { device.findObject(By.depth(0))?.applicationPackage }.getOrNull()
        assertTrue(
            "another window is in front of the app under test — the root window " +
                "belongs to ${owner ?: "<no queryable window>"} (currentPackageName says " +
                "${device.currentPackageName}, but that lies: it answered the app's name " +
                "through a full run the OEM scanner owned). Known owners: on CPH2681, " +
                "ColorOS's full-screen \"Security test\" opens over every `adb install`, " +
                "cannot be disabled from adb, and must be dismissed by hand; on a slow " +
                "CI emulator a transient system dialog can cover a RESUMED activity. " +
                "(Pressing Back to clear an overlay is NOT the answer: it walks the app " +
                "out to the launcher and the run then hangs.)",
            false,
        )
    }

    /**
     * Put the app back to a true first run: no session, no guest flag, no cache.
     *
     * Both stores, because the session lives in two: `cerebro` holds the guest
     * flag, `cerebro_secure` (EncryptedSharedPreferences) holds the refresh
     * token. Clearing only the first leaves a signed-in app that skips the very
     * funnel under test.
     *
     * Safe to run on a developer's handset in the sense that matters here:
     * `connectedAndroidTest` uninstalls the app when it finishes, which wipes
     * this data anyway. A device whose state someone is relying on should not
     * be running this task at all.
     */
    fun resetToFirstRun(context: Context) {
        // Sign out BEFORE wiping the prefs. `Session` holds the access token in
        // memory (`@Volatile private var access`) and `init()` does not clear
        // it, so a prefs wipe alone leaves the process still authenticated —
        // the next test gets a signed-in app while believing it has a first
        // run. Nothing noticed while every instrumented test was a guest;
        // adding one that signs in (WriteFlowE2ETest) turned it into
        // `GuestAppE2ETest` failing on every full-suite run while passing
        // alone, which is the shape of an order dependency rather than a flake.
        Session.init(context)
        runCatching { Session.signOut() }
        for (name in listOf("cerebro", "cerebro_secure")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        Session.init(context)
    }
}

/**
 * Poll for [text], treating "there is no Compose hierarchy yet" as *not yet*
 * rather than as failure — see [DeviceE2E] note 3.
 */
internal fun ComposeTestRule.awaitText(text: String, timeoutMs: Long = 25_000): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        // Text OR content description, for the same reason the tapper checks
        // both: an icon-only control carries its label in its description and
        // nowhere else. Checking only text made this wait out the full timeout
        // on the chat's ＋ button, whose visible glyph is not its label.
        val found = runCatching {
            onAllNodes(hasText(text, substring = true) or hasContentDescription(text, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)
        if (found) return true
        // Drive the composition's own clock forward, not just wall time. The
        // app's splash hides itself from a `LaunchedEffect { delay(450) }`, and
        // under a Compose test clock that delay is virtual: polling in real time
        // watched the splash for the full timeout and reported the screen it was
        // stuck on. Wrapped because a rule with a real clock rejects the call.
        runCatching { mainClock.advanceTimeBy(250) }
        Thread.sleep(100)
    }
    return false
}

/**
 * [awaitText], but a missing node is the failure the caller meant to assert.
 *
 * On failure it dumps what the screen DID show. A UI test that only reports the
 * string it wanted sends the next person to a screenshot loop to find out the
 * app was somewhere else entirely — which is how an hour went on the first run
 * of this suite.
 */
internal fun ComposeTestRule.requireText(text: String, timeoutMs: Long = 25_000) {
    if (awaitText(text, timeoutMs)) return
    // The full tree goes to logcat under E2E-TREE: the on-screen text list below
    // says WHERE the walk stopped, and the tree says why — which node carries
    // the click, and whether it is enabled.
    runCatching { onRoot(useUnmergedTree = true).printToLog("E2E-TREE") }
    val onScreen = runCatching {
        onAllNodesWithText("", substring = true).fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .map { it.text }
            .filter { it.isNotBlank() }
            .distinct()
            .take(15)
    }.getOrElse { listOf("<no compose hierarchy: ${it.message}>") }
    assertTrue(
        "never appeared on screen: \"$text\"\nwhat was on screen instead: $onScreen",
        false,
    )
}

/**
 * Wait for [text], then tap the thing that actually handles a tap.
 *
 * Preferring a node that carries a click action matters: in this app the label
 * is usually a `Text` *inside* the clickable row or button, and clicking the
 * text node itself is a click on nothing. The funnel walk sat on the disclosure
 * step through two runs for exactly that reason — the age confirmation looked
 * tapped and was not.
 */
internal fun ComposeTestRule.tapText(text: String, exact: Boolean = false): SemanticsNodeInteraction {
    requireText(text)
    // Content description as well as text: a control's label is not always its
    // text. The funnel's 18+ attestation is a Switch whose label lives only in
    // its contentDescription, with the visible sentence as a sibling Text — so
    // a text-only matcher clicked the sentence, the switch stayed off, and
    // Continue stayed [Disabled] through three runs.
    val clickable = onAllNodes(
        (hasText(text, substring = !exact) or hasContentDescription(text, substring = !exact)) and
            hasClickAction(),
    )
    // Prefer a node that is actually on screen. `performClick` dispatches at the
    // node's centre, so a match that sits outside the viewport is a tap into
    // nowhere — which is how the same walk passed on a 720x1604 handset and
    // missed on a taller emulator, twice, with nothing but "the next screen
    // never arrived" to show for it.
    val displayed = clickable.fetchSemanticsNodes().indexOfFirst {
        runCatching { clickable[clickable.fetchSemanticsNodes().indexOf(it)].assertIsDisplayed(); true }
            .getOrDefault(false)
    }
    val node = when {
        clickable.fetchSemanticsNodes().isEmpty() && exact -> onNodeWithText(text, substring = false)
        clickable.fetchSemanticsNodes().isEmpty() -> onAllNodesWithText(text, substring = true)[0]
        displayed >= 0 -> clickable[displayed]
        else -> clickable[0]
    }
    return node.also {
        // Bring it into view when it lives in a scrollable; a control below the
        // fold is a legitimate target, not a missing one.
        runCatching { it.performScrollTo() }
        it.performClick()
        // Let the click's recomposition (and any transition it starts) actually
        // run before the next assertion looks at the screen.
        runCatching { mainClock.advanceTimeBy(400) }
        runCatching { waitForIdle() }
    }
}

/** Tap a node whose text matches exactly — for labels that are substrings of others. */
internal fun ComposeTestRule.tapExactText(text: String): SemanticsNodeInteraction = tapText(text, exact = true)

/**
 * Switch [matcher]'s toggle on, and wait for it to actually read as on.
 *
 * A click and the state it produces are two different frames. Asserting the
 * toggle immediately after `performClick` reads the composition before the
 * recomposition the click caused: the handset happened to have re-run by then
 * and the CI emulator had not, so the same switch was On in one place and Off
 * in the other with the click identical in both. Clicked once, then waited for
 * — never clicked again in the loop, which would toggle it back off.
 */
internal fun ComposeTestRule.turnOn(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) =
    setToggle(matcher, on = true, timeoutMs = timeoutMs)

/**
 * Switch [matcher]'s toggle OFF, and wait for it to actually read as off.
 *
 * The mirror of [turnOn], and every word of its reasoning applies unchanged —
 * a click and the state it produces are still two different frames, and the
 * semantics action is still the thing to invoke rather than a coordinate tap.
 * Consent toggles need this direction: switching a category off is the half of
 * "you are in control" that actually costs the product something, so it is the
 * half worth proving.
 */
internal fun ComposeTestRule.turnOff(matcher: SemanticsMatcher, timeoutMs: Long = 10_000) =
    setToggle(matcher, on = false, timeoutMs = timeoutMs)

private fun ComposeTestRule.setToggle(
    matcher: SemanticsMatcher,
    on: Boolean,
    timeoutMs: Long = 10_000,
) {
    val want = if (on) ToggleableState.On else ToggleableState.Off
    val wanted = if (on) "On" else "Off"
    val node = onNode(matcher)
    runCatching { node.performScrollTo() }
    fun state() = runCatching {
        node.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ToggleableState)
    }.getOrNull()
    if (state() == want) return

    fun settle(untilMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + untilMs
        while (System.currentTimeMillis() < deadline) {
            runCatching { mainClock.advanceTimeBy(250) }
            runCatching { waitForIdle() }
            if (state() == want) return true
            Thread.sleep(100)
        }
        return false
    }

    // Invoke the control's OWN click handler rather than tapping coordinates.
    // A coordinate tap and a semantics action are different things: the tap has
    // to survive hit-testing, and on the CI emulator — whose AVD reports 160dpi,
    // so this Switch is a 52x32 node on a screen the app lays out as if it were
    // 1080dp wide — clicking its centre toggled nothing, ten seconds of waiting
    // included, while the identical call worked on the handset.
    runCatching { node.performSemanticsAction(SemanticsActions.OnClick) }
    if (settle(timeoutMs / 2)) return
    // Fall back to the tap, in case a control carries no OnClick action.
    runCatching { node.performClick() }
    if (settle(timeoutMs / 2)) return

    val where = runCatching { node.fetchSemanticsNode().boundsInRoot.toString() }.getOrDefault("<unreadable>")
    val displayed = runCatching { node.assertIsDisplayed(); true }.getOrDefault(false)
    assertTrue(
        "the toggle never read as $wanted - neither its own OnClick action nor a tap moved it " +
            "(state: ${state()}, displayed: $displayed, bounds: $where)",
        false,
    )
}

/**
 * Wait for a gated control to actually enable, then tap it.
 *
 * A disabled node still exists and still matches by label, so tapping one is a
 * silent no-op that leaves the walk on the same screen with nothing to explain
 * it. The funnel's Continue is gated on the 18+ attestation, and the gap
 * between the toggle and the button enabling is real on slower hardware: the
 * phone passed this and the CI emulator failed it at exactly that step, which
 * is the difference between a test and a coin toss.
 */
internal fun ComposeTestRule.tapWhenEnabled(text: String, exact: Boolean = true, timeoutMs: Long = 15_000) {
    requireText(text)
    val matcher = (hasText(text, substring = !exact) or hasContentDescription(text, substring = !exact)) and
        hasClickAction()
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        val enabled = runCatching {
            onAllNodes(matcher).fetchSemanticsNodes()
                .any { !it.config.contains(SemanticsProperties.Disabled) }
        }.getOrDefault(false)
        if (enabled) {
            val target = onAllNodes(matcher and !isNotEnabled())[0]
            runCatching { target.performScrollTo() }
            target.performClick()
            runCatching { mainClock.advanceTimeBy(400) }
            runCatching { waitForIdle() }
            return
        }
        runCatching { mainClock.advanceTimeBy(250) }
        Thread.sleep(100)
    }
    // Say what was matched. "Never became enabled" alone cannot distinguish a
    // gate that stayed shut from a gate whose opener was never tapped, and on a
    // remote runner there is no screen to look at.
    val matched = runCatching { onAllNodes(matcher).fetchSemanticsNodes().size }.getOrDefault(-1)
    val toggles = runCatching {
        onAllNodes(isToggleable()).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.ToggleableState).toString() }
    }.getOrDefault(listOf("<unreadable>"))
    assertTrue(
        "\"$text\" never became enabled ($matched clickable nodes matched it; " +
            "toggles on screen: $toggles)",
        false,
    )
}

/**
 * Type into the field carrying [label].
 *
 * `AppTextField` renders its label as an `OutlinedTextField` label, so the
 * editable node is the one whose semantics contain that text — targeting the
 * label Text alone finds a node that cannot receive input. Scrolls first,
 * because a composer's second field is usually below the fold on a 720x1604
 * screen.
 */
internal fun ComposeTestRule.typeInto(label: String, text: String) {
    waitUntil(20_000) {
        mainClock.advanceTimeBy(300)
        onAllNodes(hasSetTextAction() and hasText(label, substring = true))
            .fetchSemanticsNodes().isNotEmpty()
    }
    val field = onAllNodes(hasSetTextAction() and hasText(label, substring = true))
        .onFirst()
    runCatching { field.performScrollTo() }
    field.performClick()
    field.performTextInput(text)
    mainClock.advanceTimeBy(300)
}

/**
 * Wait until [text] is gone from every EDITABLE node.
 *
 * The trap this closes: after typing a title and tapping Add, asserting that
 * the title is "on screen" matches the draft still sitting in the input, so the
 * assertion passes instantly and the test races the POST behind it. Goals clear
 * the draft on SUCCESS (`GoalsScreen`, register B89), so an empty field is the
 * screen's own statement that the server answered — which is the thing worth
 * waiting for.
 */
internal fun ComposeTestRule.awaitFieldCleared(text: String, timeoutMs: Long = 25_000) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
        mainClock.advanceTimeBy(300)
        val stillTyped = runCatching {
            onAllNodes(hasSetTextAction() and hasText(text, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(true)
        if (!stillTyped) return
        Thread.sleep(150)
    }
    throw AssertionError(
        "the draft still holds \"$text\" after ${timeoutMs}ms — the save never reported success",
    )
}
