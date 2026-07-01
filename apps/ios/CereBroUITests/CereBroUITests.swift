import XCTest

/// Headless UI walk-through of CereBro. Each flow launches the app, navigates by
/// tapping real controls, and captures a screenshot attachment at every stop.
/// Runs from the CLI with `xcodebuild test` — no Simulator GUI needed.
final class CereBroUITests: XCTestCase {

    override func setUp() {
        super.setUp()
        continueAfterFailure = true   // keep walking even if one stop is missing
    }

    // MARK: - Helpers

    private func snapshot(_ app: XCUIApplication, _ name: String) {
        let att = XCTAttachment(screenshot: app.screenshot())
        att.name = name
        att.lifetime = .keepAlways
        add(att)
    }

    /// Launch straight into the main app (skip onboarding). `-resetState YES`
    /// clears any persisted user data so every test starts from seeded defaults
    /// and screenshots stay deterministic regardless of run order.
    private func launchIntoApp(_ app: XCUIApplication) {
        app.launchArguments += ["-hasOnboarded", "YES", "-resetState", "YES"]
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 12), "Tab bar never appeared")
    }

    /// Tap a tab-bar button resiliently. The tab bar can be mid-transition when
    /// XCUITest fires, and `tap()` then attempts an AX "scroll to visible" that
    /// intermittently fails on the Simulator. We wait for the button to settle
    /// (hittable) and fall back to a coordinate tap, which bypasses that action.
    @discardableResult
    private func tapTabButton(_ app: XCUIApplication, _ name: String) -> Bool {
        let b = app.tabBars.buttons[name]
        guard b.waitForExistence(timeout: 6) else { return false }
        let deadline = Date().addingTimeInterval(4)
        while !b.isHittable && Date() < deadline { usleep(100_000) }
        if b.isHittable { b.tap() }
        else { b.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap() }
        return true
    }

    private func openTab(_ app: XCUIApplication, _ name: String) {
        tapTabButton(app, name)
        _ = app.staticTexts.firstMatch.waitForExistence(timeout: 3)
    }

    /// Tap the first button whose label contains `label`, scrolling to find it.
    @discardableResult
    private func tap(_ app: XCUIApplication, _ label: String, timeout: TimeInterval = 3) -> Bool {
        let q = app.buttons.containing(NSPredicate(format: "label CONTAINS[c] %@", label)).firstMatch
        if q.waitForExistence(timeout: timeout) {
            for _ in 0..<4 {
                if q.isHittable { q.tap(); return true }
                app.swipeUp()
            }
        }
        let exact = app.buttons[label]
        if exact.exists && exact.isHittable { exact.tap(); return true }
        return false
    }

    /// Tap the button whose label is *exactly* `label` (scrolling to reach it).
    /// Use for controls like "Continue" whose text also appears as a substring
    /// in other elements (e.g. a "Required to continue" row subtitle).
    @discardableResult
    private func tapExact(_ app: XCUIApplication, _ label: String, timeout: TimeInterval = 3) -> Bool {
        let b = app.buttons[label]
        if b.waitForExistence(timeout: timeout) {
            for _ in 0..<4 {
                if b.isHittable { b.tap(); return true }
                app.swipeUp()
            }
        }
        return false
    }

    /// Pop the current screen and wait for the dismiss to finish before returning,
    /// so the next tap acts on a settled parent screen (not the outgoing one).
    private func back(_ app: XCUIApplication) {
        let b = app.buttons["Back"]
        if b.waitForExistence(timeout: 3) && b.isHittable {
            b.tap()
            _ = b.waitForNonExistence(timeout: 3)   // confirm the pop actually happened
        }
        _ = app.staticTexts.firstMatch.waitForExistence(timeout: 2)
    }

    /// Assert an onboarding step's title is on screen (proves the step order),
    /// snapshotting it for the record.
    @discardableResult
    private func expectStep(_ app: XCUIApplication, _ title: String, shot: String, timeout: TimeInterval = 6) -> Bool {
        let ok = app.staticTexts[title].waitForExistence(timeout: timeout)
        XCTAssertTrue(ok, "Onboarding step '\(title)' did not appear in the expected order")
        if ok { snapshot(app, shot) }
        return ok
    }

    /// Tap a selectable chip / row by exact label, scrolling it into reach.
    private func selectChip(_ app: XCUIApplication, _ label: String) {
        let b = app.buttons[label]
        for _ in 0..<5 {
            if b.exists && b.isHittable { b.tap(); return }
            app.swipeUp()
        }
    }

    // MARK: - Onboarding (Welcome → all steps → First plan)

    func testOnboardingWalkthrough() {
        let app = XCUIApplication()
        app.launchArguments += ["-hasOnboarded", "NO", "-resetState", "YES"]
        app.launch()

        XCTAssertTrue(app.buttons["Begin private setup"].waitForExistence(timeout: 10),
                      "Welcome screen did not appear")
        snapshot(app, "onb-00-welcome")

        tap(app, "Begin private setup")

        // Age gate now requires an affirmative tap before Continue is enabled.
        _ = app.staticTexts["Age Gate"].waitForExistence(timeout: 6)
        tap(app, "I am 18 or older")

        // Step through each "Continue" screen, shooting as we go. Match the
        // button exactly so we don't accidentally hit a row whose subtitle
        // reads "Required to continue".
        for i in 1...9 {
            _ = app.staticTexts.firstMatch.waitForExistence(timeout: 3)
            snapshot(app, String(format: "onb-%02d", i))
            if !tapExact(app, "Continue", timeout: 4) { break }
        }

        _ = app.staticTexts.firstMatch.waitForExistence(timeout: 3)
        snapshot(app, "onb-10-firstplan")
        tapExact(app, "Enter CereBro")

        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 8),
                      "Did not reach the main app after onboarding")
        snapshot(app, "onb-11-entered")
    }

    /// Thorough walk of the *re-sequenced* onboarding: asserts every step's title
    /// in the new value-first order (legal gates → self-reflection → baseline →
    /// companion → signup → consent → language → reminders → first plan) and
    /// exercises the interactive selections along the way.
    func testOnboardingSequencedFlow() {
        let app = XCUIApplication()
        app.launchArguments += ["-hasOnboarded", "NO", "-resetState", "YES"]
        app.launch()

        // 0 — Welcome (value shown before friction)
        XCTAssertTrue(app.buttons["Begin private setup"].waitForExistence(timeout: 10),
                      "Welcome screen did not appear")
        snapshot(app, "seq-00-welcome")
        tap(app, "Begin private setup")

        // 1 — Age gate (kept early: fast legal gate) — requires affirmative tap.
        expectStep(app, "Age Gate", shot: "seq-01-agegate")
        tap(app, "I am 18 or older")
        tapExact(app, "Continue")

        // 2 — AI disclosure (kept early: transparency before setup)
        expectStep(app, "AI Disclosure", shot: "seq-02-disclosure")
        tapExact(app, "Continue")

        // 3 — Self-reflection (the value moment — now before account/consent)
        expectStep(app, "What matters now", shot: "seq-03-selfreflection")
        selectChip(app, "Discipline")        // add a motivation
        selectChip(app, "Build confidence")  // add a goal
        tapExact(app, "Continue")

        // 4 — Baseline
        expectStep(app, "Baseline Check", shot: "seq-04-baseline")
        tapExact(app, "Continue")

        // 5 — Companion (choose the non-default persona)
        expectStep(app, "AI Companion", shot: "seq-05-companion")
        tap(app, "Scientific")
        tapExact(app, "Continue")

        // 6 — Signup (now that there's something to save)
        expectStep(app, "Save your space", shot: "seq-06-signup")
        tapExact(app, "Continue")

        // 7 — Consent (after signup) — flip a privacy switch
        expectStep(app, "Consent", shot: "seq-07-consent")
        let sw = app.switches.element(boundBy: 0)
        if sw.waitForExistence(timeout: 3) && sw.isHittable { sw.tap() }
        tapExact(app, "Continue")

        // 8 — Language
        expectStep(app, "Language", shot: "seq-08-language")
        tapExact(app, "Continue")

        // 9 — Notifications (kept last: highest-leverage retention opt-in)
        expectStep(app, "Notifications", shot: "seq-09-notifications")
        tapExact(app, "Continue")

        // 10 — First plan → into the app
        expectStep(app, "First Plan", shot: "seq-10-firstplan")
        tapExact(app, "Enter CereBro")

        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 8),
                      "Did not reach the main app after onboarding")
        XCTAssertTrue(app.tabBars.buttons["Home"].exists, "Home tab missing after onboarding")
        snapshot(app, "seq-11-entered")
    }

    /// The Welcome screen's "Preview app" escape hatch should skip straight into
    /// the app (value-first: let people look around before committing to setup).
    func testOnboardingPreviewSkips() {
        let app = XCUIApplication()
        app.launchArguments += ["-hasOnboarded", "NO", "-resetState", "YES"]
        app.launch()
        XCTAssertTrue(app.buttons["Preview app"].waitForExistence(timeout: 10),
                      "Welcome screen did not appear")
        tap(app, "Preview app")
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 8),
                      "Preview app did not enter the main app")
        snapshot(app, "onb-preview-entered")
    }

    /// End-to-end persistence: a motivation chosen during onboarding's
    /// self-reflection must flow all the way into the Talk tab's conversation
    /// starters. Live-backend test (self-skips when no API is reachable, like the
    /// other cloud tests); the hermetic backend's deterministic topic generator
    /// makes the grounding assertable.
    func testStartersReflectOnboardingMotivation() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-hasOnboarded", "NO", "-resetState", "YES"]
        app.launch()

        // Walk to the self-reflection step and pick a DISTINCTIVE selection.
        XCTAssertTrue(app.buttons["Begin private setup"].waitForExistence(timeout: 10),
                      "Welcome screen did not appear")
        tap(app, "Begin private setup")
        _ = app.staticTexts["Age Gate"].waitForExistence(timeout: 6)        // settle each push
        tap(app, "I am 18 or older")       // affirmative age confirmation
        tapExact(app, "Continue")          // Age gate → AI disclosure
        _ = app.staticTexts["AI Disclosure"].waitForExistence(timeout: 6)
        tapExact(app, "Continue")          // AI disclosure → self-reflection
        XCTAssertTrue(app.staticTexts["What matters now"].waitForExistence(timeout: 6),
                      "self-reflection step did not appear")
        selectChip(app, "Confidence")        // motivation
        selectChip(app, "Build confidence")  // goal
        tapExact(app, "Continue")          // → Baseline

        // Finish the remaining steps (Baseline → … → Notifications) and enter.
        for _ in 0..<6 {
            _ = app.staticTexts.firstMatch.waitForExistence(timeout: 3)
            if !tapExact(app, "Continue", timeout: 5) { break }
        }
        _ = app.staticTexts.firstMatch.waitForExistence(timeout: 3)
        tapExact(app, "Enter CereBro")
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 8),
                      "Did not reach the main app after onboarding")

        // Connect cloud sync with a fresh signup so starters generate live.
        rootYou(app)
        guard tap(app, "Sign in") else { throw XCTSkip("Cloud sync row missing") }
        app.buttons["Create account"].tap()
        let unique = "ui-mot-\(Int(Date().timeIntervalSince1970))@test.app"
        guard app.textFields["Email"].waitForExistence(timeout: 5) else { throw XCTSkip("auth form missing") }
        clearAndType(app.textFields["Name"], "UI Tester")
        clearAndType(app.textFields["Email"], unique)
        clearAndType(app.secureTextFields["Password"], "password123")
        _ = tap(app, "Create my account")
        let connected = app.staticTexts
            .containing(NSPredicate(format: "label CONTAINS[c] %@", "Connected as")).firstMatch
        guard connected.waitForExistence(timeout: 20) else { throw XCTSkip("Backend not reachable") }

        // Talk → text chat → starters built from the SELECTED motivation/goal.
        openTab(app, "Talk")
        guard tap(app, "Switch to chat") else { throw XCTSkip("chat unavailable") }
        let starter = app.buttons
            .containing(NSPredicate(format: "label CONTAINS[c] %@", "Talk about:")).firstMatch
        XCTAssertTrue(starter.waitForExistence(timeout: 30),
                      "conversation starters did not appear after onboarding")
        snapshot(app, "starters-from-onboarding-motivation")

        // Grounding: the Confidence / Build-confidence pick surfaces its seeds
        // (deterministic, key-free backend). Proves the selection persisted.
        let grounded = app.buttons.containing(NSPredicate(format:
            "label CONTAINS[c] 'doubt' OR label CONTAINS[c] 'sell myself' OR label CONTAINS[c] 'decision' OR label CONTAINS[c] 'win' OR label CONTAINS[c] 'confidence'")).firstMatch
        XCTAssertTrue(grounded.waitForExistence(timeout: 6),
                      "starters did not reflect the onboarding-selected motivation")
    }

    /// Crisis resources adapt to the device region. Forcing a US locale must
    /// surface the US lines (988) rather than the India default.
    func testCrisisResourcesLocalized() {
        let app = XCUIApplication()
        app.launchArguments += ["-hasOnboarded", "YES", "-resetState", "YES",
                                "-AppleLocale", "en_US", "-AppleLanguages", "(en)"]
        app.launch()
        XCTAssertTrue(app.tabBars.firstMatch.waitForExistence(timeout: 12), "Tab bar never appeared")

        rootYou(app)
        guard tap(app, "Urgent support") else { return XCTFail("Urgent support row missing") }

        let line988 = app.staticTexts
            .containing(NSPredicate(format: "label CONTAINS %@", "988")).firstMatch
        XCTAssertTrue(line988.waitForExistence(timeout: 8),
                      "US locale did not surface the 988 crisis line")
        snapshot(app, "crisis-us-988")
    }

    /// The settings region picker overrides which crisis lines are shown.
    func testCrisisRegionOverride() {
        let app = XCUIApplication()
        launchIntoApp(app)                       // default region (no override)
        rootYou(app)
        guard tap(app, "Urgent support") else { return XCTFail("Urgent support row missing") }
        XCTAssertTrue(app.staticTexts["You're not alone"].waitForExistence(timeout: 6),
                      "crisis screen did not load")   // let the push settle
        guard tap(app, "Crisis region") else { return XCTFail("Crisis region row missing") }
        guard tap(app, "United States") else { return XCTFail("US option missing") }

        // The live preview reflects the chosen region.
        let preview988 = app.staticTexts
            .containing(NSPredicate(format: "label CONTAINS %@", "988")).firstMatch
        XCTAssertTrue(preview988.waitForExistence(timeout: 6),
                      "selecting US did not surface 988 in the preview")
        snapshot(app, "crisis-region-picker-us")

        // …and the override carries back into the crisis screen itself.
        back(app)
        let crisis988 = app.staticTexts
            .containing(NSPredicate(format: "label CONTAINS %@", "988")).firstMatch
        XCTAssertTrue(crisis988.waitForExistence(timeout: 6),
                      "region override did not apply to the crisis screen")
    }

    // MARK: - Tab overview

    func testTabsOverview() {
        let app = XCUIApplication()
        launchIntoApp(app)
        snapshot(app, "tab-Home")
        for tab in ["Sleep", "Talk", "Journal", "You"] {
            openTab(app, tab)
            snapshot(app, "tab-\(tab)")
        }
    }

    // MARK: - Home flow

    func testHomeFlow() {
        let app = XCUIApplication()
        launchIntoApp(app)
        openTab(app, "Home")
        snapshot(app, "home-00")

        // Search (trailing header button)
        if app.buttons["Search"].waitForExistence(timeout: 3) {
            app.buttons["Search"].tap()
            snapshot(app, "home-01-search")
            back(app)
        }

        tap(app, "Check how you feel");  snapshot(app, "home-02-mood");     back(app)
        tap(app, "Today's plan");        snapshot(app, "home-03-plan");     back(app)
        tap(app, "Programs");            snapshot(app, "home-04-programs"); back(app)
    }

    // MARK: - Sleep flow

    func testSleepFlow() {
        let app = XCUIApplication()
        launchIntoApp(app)
        openTab(app, "Sleep")
        snapshot(app, "sleep-00")

        tap(app, "Meditation library")
        snapshot(app, "sleep-01-library")
        back(app)

        // First sleep content row → Player. Tap the NavRow by its title rather
        // than a positional index: the only button on the hero is its "Play" CTA,
        // so the row whose label contains the track title is unambiguous and
        // reliably lands on the "Now playing" PlayerView.
        openTab(app, "Sleep")               // ensure we're settled on the Sleep root
        if tap(app, "Rain over quiet hills") {
            _ = app.staticTexts["Now playing"].waitForExistence(timeout: 4)
            snapshot(app, "sleep-02-player")
            back(app)
        }
    }

    // MARK: - Talk flow (→ SOS → Breathing, and Chat)

    func testTalkFlow() {
        let app = XCUIApplication()
        launchIntoApp(app)
        openTab(app, "Talk")
        // Let the Talk screen settle (orb/waveform animate) before tapping, so
        // the scroll-to-find doesn't race the entrance transition. (Signed-out
        // title is "Your voice companion".)
        _ = app.staticTexts["Your voice companion"].waitForExistence(timeout: 6)
        snapshot(app, "talk-00")

        if tap(app, "Quick SOS reset") {
            snapshot(app, "talk-01-sos")
            if tap(app, "2-minute breathing") {
                snapshot(app, "talk-02-breathing")
                tap(app, "Continue")               // fires the completion celebration
                snapshot(app, "talk-03-breathing-done")
                back(app)
            }
            back(app)
        }

        openTab(app, "Talk")
        _ = app.staticTexts["Your voice companion"].waitForExistence(timeout: 6)
        if tap(app, "Switch to chat") {
            snapshot(app, "talk-04-chat")
            back(app)
        }
    }

    // MARK: - Journal flow

    func testJournalFlow() {
        let app = XCUIApplication()
        launchIntoApp(app)
        openTab(app, "Journal")
        snapshot(app, "journal-00")

        if tap(app, "New entry") {
            snapshot(app, "journal-01-entry")
            if tap(app, "See AI reflection") {
                snapshot(app, "journal-02-insight")
                back(app)
            }
            back(app)
        }

        tap(app, "History");      snapshot(app, "journal-03-history"); back(app)
        tap(app, "Private mode"); snapshot(app, "journal-04-private"); back(app)
    }

    // MARK: - Profile flow + app-state screens

    func testProfileAndStates() {
        let app = XCUIApplication()
        launchIntoApp(app)
        rootYou(app)
        snapshot(app, "you-00")

        // Insights → Pattern dashboard (re-anchor on the You root afterwards so a
        // stuck nested back() can't strand the rest of the flow on Insights).
        if tap(app, "Weekly insights") {
            snapshot(app, "you-01-insights")
            if tap(app, "Pattern dashboard") {
                snapshot(app, "you-02-patterns")
            }
        }

        // Each item navigates from a freshly-popped You root.
        visit(app, "Privacy & memory", "you-03-privacy")
        visit(app, "Premium plan",     "you-04-premium")
        visit(app, "Urgent support",   "you-05-crisis")
        visit(app, "Human support",    "you-06-human")

        // App-state demo screens (lower on the page → tap() scrolls to them).
        visit(app, "Offline mode",   "you-07-offline")
        visit(app, "Empty journal",  "you-08-empty")
        visit(app, "Voice loading",  "you-09-voiceloading")
        visit(app, "Voice error",    "you-10-voiceerror")
        visit(app, "Free limit",     "you-11-freelimit")
    }

    // MARK: - Phase 2: data layer (record → persist → surface)

    /// Exercises the newly-wired write paths and captures evidence that recorded
    /// data flows back into the UI: a mood check-in surfaces on Home, a saved
    /// journal entry appears in History, and a toggled plan step updates progress.
    func testPhase2DataLayer() {
        let app = XCUIApplication()
        launchIntoApp(app)

        // 1) Record a mood, then confirm Home reflects it.
        openTab(app, "Home")
        if tap(app, "Check how you feel") {
            tap(app, "Anxious")                 // select a mood
            tap(app, "Start gentle support")    // persist it (fires celebration)
            snapshot(app, "p2-01-mood-recorded")
            back(app)
        }
        openTab(app, "Home")
        snapshot(app, "p2-02-home-after-mood")   // subtitle now "Last check-in: Anxious"

        // 2) Save a journal entry, then confirm it shows up in History.
        openTab(app, "Journal")
        if tap(app, "New entry") {
            // The editor starts as an honest blank page — type before saving.
            let editor = app.textViews["Journal editor"].firstMatch
            if editor.waitForExistence(timeout: 3) {
                editor.tap()
                editor.typeText("Felt anxious about tomorrow's meeting.")
            }
            tap(app, "Save / Continue")          // inserts entry, auto-dismisses
            _ = app.staticTexts["Journal"].waitForExistence(timeout: 4)
        }
        if tap(app, "History") {
            snapshot(app, "p2-03-journal-history")  // new entry at the top
            back(app)
        }

        // 3) Toggle a daily-plan step and confirm progress updates.
        openTab(app, "Home")
        if tap(app, "Today's plan") {
            tap(app, "Mark Night journal done")
            snapshot(app, "p2-04-plan-toggled")
            back(app)
        }
    }

    // MARK: - Cloud sync (live backend; self-skips if unreachable)

    /// Connects the app to the running FastAPI backend via the seeded demo user
    /// and confirms the agentic plan loads. Skips gracefully if the API isn't up,
    /// so the screenshot suite never hard-depends on the backend.
    func testCloudSync() throws {
        let app = XCUIApplication()
        launchIntoApp(app)
        rootYou(app)
        guard tap(app, "Sign in") else { throw XCTSkip("Cloud sync row missing") }
        _ = app.staticTexts["Sign in"].waitForExistence(timeout: 4)
        snapshot(app, "signin-form")   // modern layout: Apple · Google · email

        // Default mode is Sign in with the seeded demo credentials pre-filled.
        _ = tap(app, "Continue with email")

        let connected = app.staticTexts
            .containing(NSPredicate(format: "label CONTAINS[c] %@", "Connected as")).firstMatch
        if connected.waitForExistence(timeout: 20) {
            // The server-driven agentic plan loads a moment after connect (extra
            // round trip); wait generously so it's settled before we snapshot.
            let planLoaded = app.staticTexts["Your agentic plan"].waitForExistence(timeout: 25)
            snapshot(app, "cloud-connected")
            XCTAssertTrue(planLoaded, "Connected but the agentic plan did not load from the server")
        } else {
            // Backend not running — don't fail the offline-capable suite.
            throw XCTSkip("Backend not reachable from simulator")
        }
    }

    /// Live chat + server plan-step toggle (skips if backend unreachable).
    func testCloudChatAndPlan() throws {
        let app = XCUIApplication()
        launchIntoApp(app)
        rootYou(app)
        guard tap(app, "Sign in") else { throw XCTSkip("Cloud sync row missing") }
        _ = tap(app, "Continue with email")
        let connected = app.staticTexts
            .containing(NSPredicate(format: "label CONTAINS[c] %@", "Connected as")).firstMatch
        guard connected.waitForExistence(timeout: 20) else { throw XCTSkip("Backend not reachable") }

        // Toggle the first server plan step → backend PATCH /plans/steps.
        XCTAssertTrue(app.staticTexts["Your agentic plan"].waitForExistence(timeout: 25),
                      "agentic plan did not load")
        if tap(app, "Mark Breathing reset done") {
            let toggled = app.buttons
                .containing(NSPredicate(format: "label CONTAINS[c] %@", "not done")).firstMatch
            XCTAssertTrue(toggled.waitForExistence(timeout: 8), "plan step did not toggle on the server")
            snapshot(app, "cloud-plan-toggled")
        }

        // Live voice companion UI (connected state: orb + "Tap to talk").
        openTab(app, "Talk")
        _ = app.staticTexts["I'm listening"].waitForExistence(timeout: 4)
        snapshot(app, "talk-05-voice-connected")

        // Live chat → backend AI companion.
        guard tap(app, "Switch to chat") else { throw XCTSkip("chat unavailable") }
        let field = app.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5), "chat field missing")

        // Personalized conversation starters appear in the empty state (from the
        // onboarding self-reflection). Soft check — the demo user may already have
        // chat history, in which case the empty-state rail is intentionally hidden.
        let starter = app.buttons.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "Talk about:")).firstMatch
        if starter.waitForExistence(timeout: 6) { snapshot(app, "cloud-chat-starters") }

        field.tap()
        field.typeText("I feel really anxious and overwhelmed")
        XCTAssertTrue(tap(app, "Send message"), "send button missing")

        // Anxiety surfaces an inline activity (deterministic router, or the Oracle's
        // suggest_activity tool when streaming). The activity card is a button labeled
        // "Start activity: …" — robust to whichever activity the agent picks.
        let widget = app.buttons.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "Start activity")).firstMatch
        XCTAssertTrue(widget.waitForExistence(timeout: 25), "inline activity widget did not appear")
        snapshot(app, "cloud-chat-reply")
    }

    /// Signs up a brand-new user (empty chat history) and confirms the
    /// personalized conversation starters render in the chat empty state and seed
    /// the conversation when tapped. A fresh account guarantees the empty state,
    /// which the demo-user chat test can't (its history hides the rail).
    func testCloudStartersFreshUser() throws {
        let app = XCUIApplication()
        launchIntoApp(app)
        rootYou(app)
        guard tap(app, "Sign in") else { throw XCTSkip("Cloud sync row missing") }

        // Switch to "Create account" and fill a unique signup.
        app.buttons["Create account"].tap()
        let unique = "ui-\(Int(Date().timeIntervalSince1970))@test.app"
        guard app.textFields["Email"].waitForExistence(timeout: 5) else { throw XCTSkip("auth form missing") }
        clearAndType(app.textFields["Name"], "UI Tester")
        clearAndType(app.textFields["Email"], unique)
        clearAndType(app.secureTextFields["Password"], "password123")
        _ = tap(app, "Create my account")

        let connected = app.staticTexts
            .containing(NSPredicate(format: "label CONTAINS[c] %@", "Connected as")).firstMatch
        guard connected.waitForExistence(timeout: 20) else { throw XCTSkip("Backend not reachable") }

        // Fresh user → empty chat → starters are guaranteed to show.
        openTab(app, "Talk")
        guard tap(app, "Switch to chat") else { throw XCTSkip("chat unavailable") }

        // Topic generation may hit the LLM, so wait generously.
        let starter = app.buttons.containing(
            NSPredicate(format: "label CONTAINS[c] %@", "Talk about:")).firstMatch
        XCTAssertTrue(starter.waitForExistence(timeout: 30), "conversation starters did not appear for a fresh user")
        snapshot(app, "cloud-chat-starters-fresh")

        // Tapping a starter seeds the chat with that topic as a user message.
        let topic = starter.label.replacingOccurrences(of: "Talk about: ", with: "")
        starter.tap()
        XCTAssertTrue(app.staticTexts[topic].waitForExistence(timeout: 10),
                      "tapping a starter did not seed the conversation")
        snapshot(app, "cloud-chat-starter-tapped")
    }

    /// Apple-compliance surfaces + the games hub (no backend needed).
    func testComplianceAndGames() {
        let app = XCUIApplication()
        launchIntoApp(app)

        // Games hub from Home → play a game.
        if tap(app, "Calm games") {
            XCTAssertTrue(app.staticTexts["Calm games"].waitForExistence(timeout: 4), "games hub did not open")
            snapshot(app, "games-hub")
            if tap(app, "Bubble pop") {
                _ = app.staticTexts.firstMatch.waitForExistence(timeout: 3)
                snapshot(app, "game-bubble-pop")
                back(app)
            }
            back(app)
        }

        // Privacy policy (App Store requirement).
        rootYou(app)
        if tap(app, "Privacy policy") {
            XCTAssertTrue(app.staticTexts["Privacy Policy"].waitForExistence(timeout: 4), "privacy policy did not open")
            snapshot(app, "privacy-policy")
            back(app)
        }

        // Account deletion screen — the destructive button stays disabled until
        // the user types DELETE (App Store 5.1.1(v) in-app deletion).
        rootYou(app)
        if tap(app, "Delete account") {
            XCTAssertTrue(app.staticTexts["Delete Account"].waitForExistence(timeout: 4), "delete screen did not open")
            let del = app.buttons["Delete my account"]
            XCTAssertTrue(del.waitForExistence(timeout: 3))
            XCTAssertFalse(del.isEnabled, "delete must be gated until DELETE is typed")
            snapshot(app, "delete-account")
            back(app)
        }

        // Sign in with Apple is offered alongside email auth.
        rootYou(app)
        if tap(app, "Sign in") {
            XCTAssertTrue(app.buttons["Sign in with Apple"].waitForExistence(timeout: 4), "Sign in with Apple button missing")
            snapshot(app, "cloud-apple")
        }
    }

    /// Tap a field, clear any pre-filled value, then type fresh text.
    private func clearAndType(_ field: XCUIElement, _ text: String) {
        field.tap()
        if let current = field.value as? String, !current.isEmpty {
            field.typeText(String(repeating: XCUIKeyboardKey.delete.rawValue, count: current.count))
        }
        field.typeText(text)
    }

    // MARK: - World-class showcase (screenshots of the upgraded flows)

    func testWorldClassShowcase() {
        let app = XCUIApplication()
        launchIntoApp(app)

        // Home — time-of-day greeting, single next-action hero, streak.
        openTab(app, "Home")
        snapshot(app, "ws-01-home")
        if tap(app, "Today's plan") { settle(app, "Daily Plan"); snapshot(app, "ws-02-daily-plan"); back(app) }

        // Sleep — real elapsed player, volume slider, favorite + timer transport.
        openTab(app, "Sleep")
        snapshot(app, "ws-03-sleep-home")
        if tap(app, "Rain over quiet hills") {
            settle(app, "Now playing"); snapshot(app, "ws-04-sleep-player"); back(app)
        }

        // Journal — guided prompt + honest blank editor, then history search.
        openTab(app, "Journal")
        if tap(app, "New entry") { settle(app, "Journal Entry"); snapshot(app, "ws-05-journal-entry"); back(app) }
        if tap(app, "History") { settle(app, "Journal History"); snapshot(app, "ws-06-journal-history"); back(app) }

        // You — reminders, premium paywall, sign-in, trusted contact.
        visitSettled(app, "Daily reminder", "Daily Reminder", "ws-07-reminders")
        visitSettled(app, "Premium plan", "CereBro Premium", "ws-08-premium")
        visitSettled(app, "Sign in", "Not connected", "ws-09-signin")
        rootYou(app)
        if tap(app, "Urgent support") {
            settle(app, "You're not alone")
            if tap(app, "Notify a trusted contact") { settle(app, "Trusted Contact"); snapshot(app, "ws-10-trusted-contact") }
        }
    }

    /// Wait for a destination's known text so a screenshot lands on the settled
    /// screen, not mid-push.
    private func settle(_ app: XCUIApplication, _ text: String, timeout: TimeInterval = 5) {
        _ = app.staticTexts[text].waitForExistence(timeout: timeout)
    }

    /// Like `visit`, but waits for the destination before snapshotting.
    private func visitSettled(_ app: XCUIApplication, _ row: String, _ settleText: String, _ shot: String) {
        rootYou(app)
        if tap(app, row) { settle(app, settleText); snapshot(app, shot) }
    }

    /// Return to the You tab's root (re-tapping the active tab pops its stack).
    private func rootYou(_ app: XCUIApplication) {
        tapTabButton(app, "You")                           // switch to / pop You
        tapTabButton(app, "You")                           // second tap pops to root
        _ = app.staticTexts["You"].waitForExistence(timeout: 3)
    }

    /// From the You root: open a row, screenshot it, then return to the root.
    private func visit(_ app: XCUIApplication, _ row: String, _ shot: String) {
        rootYou(app)
        if tap(app, row) { snapshot(app, shot) }
    }
}
