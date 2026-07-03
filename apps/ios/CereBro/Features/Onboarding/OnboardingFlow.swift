import SwiftUI

struct OnboardingFlow: View {
    @EnvironmentObject var state: AppState
    @State private var step = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Theme.background
            Group {
                // "90-second to calm" ordering: the legal/transparency gates stay
                // first (fast taps), language comes early (feeling understood is
                // part of the product), then ONE feeling tap → a real 2-minute
                // breathing reset → the first mini-plan — all BEFORE any account
                // ask. Account, consent (private by default) and the reminder
                // opt-in come only after the user has felt something work.
                switch step {
                case 0: WelcomeScreen(onBegin: next,
                                      // Returning account: originally gated at its own
                                      // onboarding (attestation is on the server), so
                                      // sign-in skips straight into the app.
                                      onSignedIn: { state.hasOnboarded = true })
                case 1: AgeGateScreen(onContinue: next)
                case 2: DisclosureScreen(onContinue: next)
                case 3: LanguageScreen(onContinue: next)
                case 4: StateCheckScreen(onContinue: next)
                case 5: FirstResetScreen(onContinue: next)
                case 6: FirstPlanScreen(onContinue: next)
                case 7: SignupScreen(onContinue: next)
                case 8: ConsentScreen(onContinue: next)
                default: NotificationsScreen(onContinue: { state.hasOnboarded = true })
                }
            }
            .id(step)   // each step is its own view, so the push transition plays
            .transition(reduceMotion ? .opacity : .asymmetric(
                insertion: .move(edge: .trailing).combined(with: .opacity),
                removal: .move(edge: .leading).combined(with: .opacity)))
        }
        .animation(reduceMotion ? .easeInOut : .spring(response: 0.5, dampingFraction: 0.9), value: step)
    }

    private func next() { Haptics.selection(); step += 1 }
}

// Shared progress bar
private struct OnboardingProgress: View {
    let value: Double
    var body: some View {
        GeometryReader { geo in
            ZStack(alignment: .leading) {
                Capsule().fill(Theme.Palette.card)
                Capsule().fill(Theme.Palette.cream).frame(width: geo.size.width * value)
            }
        }
        .frame(height: 7)
    }
}

// MARK: 1 — Welcome
private struct WelcomeScreen: View {
    var onBegin: () -> Void
    /// Returning user signed in from the auth sheet — skip onboarding.
    var onSignedIn: () -> Void
    @EnvironmentObject var backend: BackendService
    @State private var showAuth = false
    var body: some View {
        // No stock hero photo: the brand night gradient + orb carry the welcome
        // (the old photo clashed with the theme and seamed against the background).
        ZStack {   // centered — bottom-anchoring left a large dead zone up top
            VStack(spacing: 16) {
                GlowOrb(size: 96).entrance(0, y: 28)
                Text("Welcome to\nCereBro")
                    .multilineTextAlignment(.center)
                    .displayFont(34).foregroundStyle(Theme.Palette.text)
                    .entrance(1)
                Text("Your quiet space for daily mental fitness, better sleep, and calmer focus.")
                    .multilineTextAlignment(.center)
                    .appFont(14).foregroundStyle(Theme.Palette.muted)
                    .padding(.horizontal, 20)
                    .entrance(2)
                Text("Private by design — nothing is ever shared.")
                    .multilineTextAlignment(.center)
                    .appFont(11.5, weight: .semibold).foregroundStyle(Theme.Palette.muted2)
                    .entrance(2)
                OnboardingProgress(value: 0.08).padding(.top, 8).entrance(3)
                // The promise is immediate value, not a setup marathon (the reset
                // arrives ~4 fast taps in, after the legal gates).
                PrimaryButton(title: "Try a 2-minute reset", systemImage: "wind", action: onBegin).entrance(4)
                // Returning users shouldn't have to walk the setup to reach login.
                Button { showAuth = true } label: {
                    Text("I already have an account")
                        .appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.muted)
                        .frame(minHeight: 34)
                }
                .buttonStyle(.pressable)
                .entrance(5)
            }
            .padding(24)
        }
        .sheet(isPresented: $showAuth) { NavigationStack { CloudSyncView() } }
        .onChange(of: backend.isConnected) { _, connected in
            if connected { showAuth = false; onSignedIn() }
        }
    }
}

// Generic onboarding step scaffold
private struct StepScaffold<Content: View>: View {
    let eyebrow: String
    let title: String
    /// One warm, step-specific sentence (no boilerplate).
    let caption: String
    var progress: Double
    /// When false, the Continue button is disabled (e.g. an unmet age gate).
    var canContinue: Bool = true
    /// Label for the advance button (the final step says "Enter CereBro").
    var continueTitle: String = "Continue"
    var onContinue: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                Text(eyebrow).eyebrow().entrance(0)
                Text(title).displayFont(28).foregroundStyle(Theme.Palette.text).entrance(1)
                // No per-step stock photo: 17 images over 10 steps meant repeats
                // and off-brand fillers, and every control sat one scroll lower.
                Text(caption)
                    .appFont(13).foregroundStyle(Theme.Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .entrance(3)
                content.entrance(4)
                OnboardingProgress(value: progress).entrance(5)
                PrimaryButton(title: continueTitle, action: onContinue)
                    .disabled(!canContinue)
                    .opacity(canContinue ? 1 : 0.45)
                    .entrance(6)
            }
            .padding(18).padding(.top, 12)
        }
    }
}

// MARK: 7 — Signup (after the value moment, so there's something to save)
/// A REAL account step with the full auth form (Apple / Google / email)
/// embedded right on the page — no sheet. Signing in advances automatically;
/// "Maybe later" defers honestly (the account stays one tap away in You).
private struct SignupScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var backend: BackendService

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Yours to keep").eyebrow().entrance(0)
                Text("Save your space").displayFont(28).foregroundStyle(Theme.Palette.text).entrance(1)
                Text(backend.isConnected
                     ? "Your space is saved — plan, journal and check-ins now sync privately."
                     : "You've shaped your plan — create your private space to keep it. No social feed, no sharing, just you.")
                    .appFont(13).foregroundStyle(Theme.Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .entrance(2)
                if backend.isConnected {
                    PrimaryButton(title: "Continue", action: onContinue).entrance(3)
                } else {
                    AuthForm(initialMode: .signUp).entrance(3)
                }
                OnboardingProgress(value: 0.80).entrance(4)
                if !backend.isConnected {
                    SecondaryButton(title: "Maybe later", systemImage: "arrow.right", action: onContinue)
                        .entrance(5)
                }
            }
            .padding(18).padding(.top, 12)
        }
        .onChange(of: backend.isConnected) { _, connected in
            // Signed in / account created inline → move on.
            if connected {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) { onContinue() }
            }
        }
    }
}

// MARK: 1 — Age gate (kept early: fast legal gate)
private struct AgeGateScreen: View {
    var onContinue: () -> Void
    @State private var confirmed = false
    var body: some View {
        StepScaffold(eyebrow: "For adults only", title: "A quick check",
                     caption: "CereBro is built for adults. A quick check keeps the experience safe and appropriate.",
                     progress: 0.15, canContinue: confirmed, onContinue: onContinue) {
            DangerPanel {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Wellness support, not emergency care.").appFont(14, weight: .bold).foregroundStyle(Theme.Palette.danger)
                    Text("If you are in immediate danger, call emergency services now.").appFont(12).foregroundStyle(Theme.Palette.muted)
                }
            }
            // A required, affirmative tap — Continue stays disabled until confirmed.
            ListRow(title: confirmed ? "Confirmed: I am 18 or older" : "I am 18 or older",
                    subtitle: confirmed ? "Thank you" : "Tap to confirm — required to continue",
                    systemImage: confirmed ? "checkmark.circle.fill" : "checkmark.shield",
                    imageURL: Dummy.Img.privacy, emphasis: confirmed) {
                confirmed.toggle(); Haptics.selection()
            }
            .accessibilityAddTraits(confirmed ? .isSelected : [])
        }
    }
}

// MARK: 2 — AI disclosure (kept early: transparency builds trust before setup)
private struct DisclosureScreen: View {
    var onContinue: () -> Void
    var body: some View {
        StepScaffold(eyebrow: "Honesty first", title: "What CereBro is — and isn't",
                     caption: "Here's exactly what your AI companion can and can't do for you.",
                     progress: 0.25, onContinue: onContinue) {
            HStack(spacing: 10) {
                Card { VStack(alignment: .leading, spacing: 4) {
                    Text("Can help").appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                    Text("Listen, reflect, guide tools, suggest a plan.").appFont(12).foregroundStyle(Theme.Palette.muted)
                }}
                Card { VStack(alignment: .leading, spacing: 4) {
                    Text("Can't do").appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                    Text("Diagnose, prescribe, replace therapy, or handle emergencies.").appFont(12).foregroundStyle(Theme.Palette.muted)
                }}
            }
        }
    }
}

// MARK: 8 — Consent (after signup: choices for the data you're about to create)
/// Private by default: nothing is pre-ticked (EDPB/ICO — silence isn't consent).
/// One recommended card opts into personalization with a single explicit tap.
private struct ConsentScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState

    private var remembering: Bool { state.consent.moodHistory && state.consent.aiMemory }

    var body: some View {
        StepScaffold(eyebrow: "Privacy choices", title: "What CereBro remembers",
                     caption: "Private by default — CereBro remembers nothing you don't switch on. Change any of this later in Settings.",
                     progress: 0.88, onContinue: onContinue) {
            ListRow(title: remembering ? "Remembering your patterns" : "Remember my patterns",
                    subtitle: remembering ? "Thank you — plans and reflections will tune to you"
                                          : "Recommended — better plans and reflections over time",
                    systemImage: remembering ? "checkmark.circle.fill" : "sparkles",
                    emphasis: remembering) {
                let on = !remembering
                state.consent.moodHistory = on
                state.consent.aiMemory = on
                Haptics.selection()
            }
            .accessibilityAddTraits(remembering ? .isSelected : [])
            SettingsGroup {
                ToggleRow(title: "Mood history", subtitle: "Used for insights", isOn: $state.consent.moodHistory); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: "AI memory", subtitle: "Goals and preferences", isOn: $state.consent.aiMemory); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: "Voice storage", subtitle: "Off by default", isOn: $state.consent.voiceStorage)
            }
        }
        .onAppear {
            // First-run default is everything OFF — consent must be an action.
            state.consent = Consent(moodHistory: false, aiMemory: false,
                                    voiceStorage: false, modelTraining: false)
        }
    }
}

// MARK: 9 — Language
private struct LanguageScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    @State private var selection: Set<String> = ["English", "Hinglish"]
    var body: some View {
        StepScaffold(eyebrow: "Speak your language", title: "Language",
                     caption: "Talk and reflect in the language you think in. Mix more than one if that's you.",
                     progress: 0.35, onContinue: persistAndContinue) {
            ChipRow(options: Dummy.languages, selection: $selection)
        }
        .onAppear {
            let saved = Set(state.language.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) })
            if !saved.isEmpty { selection = saved }
        }
    }
    private func persistAndContinue() {
        if !selection.isEmpty {
            // Preserve the catalogue order so the display string is stable.
            state.language = Dummy.languages.filter(selection.contains).joined(separator: ", ")
        }
        onContinue()
    }
}

// MARK: 4 — One-tap state check (the whole "assessment" is a single feeling tap;
// richer context accrues later through actual behaviour, not a questionnaire)
private struct StateCheckScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    @State private var picked: String?

    /// Each feeling maps into the existing cross-stack taxonomy, so the plan,
    /// starters and server personalization all keep working unchanged.
    private static let states: [(label: String, symbol: String, motivation: String, goal: String)] = [
        ("Stressed and tense",        "wind",              "Calm",       "Reduce stress"),
        ("Can't switch off at night", "moon.zzz",          "Calm",       "Sleep better"),
        ("Overthinking everything",   "arrow.2.squarepath", "Focus",      "Stop overthinking"),
        ("Doubting myself",           "person.crop.circle.badge.questionmark", "Confidence", "Build confidence"),
        ("Feeling distant from people", "person.2",        "Connection", "Feel less alone"),
        ("Can't stay consistent",     "flag.checkered",    "Discipline", "Strengthen willpower"),
    ]

    var body: some View {
        StepScaffold(eyebrow: "One tap is enough", title: "What feels most true right now?",
                     caption: "No questionnaire — just pick the one that fits today. CereBro shapes your first reset and plan around it.",
                     progress: 0.45, canContinue: picked != nil, onContinue: onContinue) {
            VStack(spacing: 10) {
                ForEach(Self.states, id: \.label) { s in
                    ListRow(title: s.label,
                            subtitle: picked == s.label ? "That's what we'll start with" : "",
                            systemImage: s.symbol,
                            emphasis: picked == s.label) {
                        pick(s)
                    }
                    .accessibilityAddTraits(picked == s.label ? .isSelected : [])
                }
            }
        }
    }

    private func pick(_ s: (label: String, symbol: String, motivation: String, goal: String)) {
        picked = s.label
        state.selectedMotivations = [s.motivation]
        state.selectedGoals = [s.goal]
        state.hasAssessment = true   // a real answer — safe to sync from now on
        // Cached in BackendService — pushed now if connected, else at the next
        // connect, so the server plan/insights personalize either way.
        backend.saveAssessment(motivations: state.selectedMotivations, goals: state.selectedGoals)
        Haptics.selection()
        // One tap is the answer — advance without demanding a second tap.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) { onContinue() }
    }
}

// MARK: 5 — First reset (the first felt benefit, BEFORE any account ask)
private struct FirstResetScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Your first reset").eyebrow().entrance(0)
                Text("Let's steady your body").displayFont(28).foregroundStyle(Theme.Palette.text).entrance(1)
                Text("Two minutes of guided breathing — follow the orb for a few cycles, or skip ahead if now isn't the moment.")
                    .appFont(13).foregroundStyle(Theme.Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .entrance(2)
                BreathingPacer(accent: Theme.Accent.breathe)
                    .frame(maxWidth: .infinity)
                    .entrance(3)
                OnboardingProgress(value: 0.58).entrance(4)
                PrimaryButton(title: "I feel steadier") {
                    state.recordActivity()   // the first win starts the streak
                    onContinue()
                }
                .entrance(5)
                SecondaryButton(title: "Skip for now", systemImage: "arrow.right", action: onContinue)
                    .entrance(6)
            }
            .padding(18).padding(.top, 12)
        }
        .toolAmbience(.wind)
    }
}

// MARK: 10 — Notifications (kept last: highest-leverage retention opt-in)
private struct NotificationsScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    @State private var selection: Set<String> = ["Evening 7 PM"]
    var body: some View {
        StepScaffold(eyebrow: "Gentle reminders", title: "Notifications",
                     caption: "You've had your first win — want a quiet nudge to come back tomorrow? Never noisy, always easy to turn off.",
                     progress: 0.96, continueTitle: "Enter CereBro", onContinue: persistAndContinue) {
            ChipRow(options: Dummy.reminderTimes, selection: $selection)
        }
    }

    /// Persist the chosen reminder time so Profile reflects it (no contradiction).
    /// The OS permission prompt is skipped under UI tests so the suite doesn't hang.
    private func persistAndContinue() {
        if selection.contains("Morning 9 AM") { state.reminderHour = 9 }
        else if selection.contains("Evening 7 PM") { state.reminderHour = 19 }
        let wants = !selection.isEmpty && !selection.contains("No reminders")
        let underTest = ProcessInfo.processInfo.arguments.contains("-resetState")
        if wants && !underTest {
            Task {
                let ok = await ReminderManager.requestAuthorization()
                await MainActor.run {
                    state.reminderEnabled = ok
                    if ok { ReminderManager.scheduleDaily(hour: state.reminderHour) }
                }
            }
        }
        onContinue()
    }
}

// MARK: 6 — First plan (the mini-plan lands right after the first win, and
// gives the account step that follows its reason to exist: "save this")
private struct FirstPlanScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    @State private var done = false

    /// Headline the plan around the user's first chosen goal.
    private var planTitle: String {
        switch state.selectedGoals.first {
        case "Sleep better":      return "Sleep deeper"
        case "Reduce stress":     return "Ease today's stress"
        case "Stop overthinking": return "Quiet the noise"
        case "Build confidence":  return "Steady confidence"
        case "Feel less alone":   return "Feel more connected"
        case "Strengthen willpower": return "Small promises, kept"
        default:                  return "A calmer day"
        }
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                Text("Made around you").eyebrow()
                Text("First Plan").displayFont(28).foregroundStyle(Theme.Palette.text)
                HeroCard(tag: "Today", title: planTitle,
                         subtitle: "A light plan: one thing now, one tonight, one tomorrow — tuned to what you picked.",
                         cta: "Looks right", imageURL: Dummy.Img.plan) {
                    done.toggle()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { onContinue() }
                }
                ForEach(Dummy.planSteps) { s in
                    // Static preview rows — no chevron, no fake tap affordance.
                    RowLabel(title: s.title, subtitle: s.detail, systemImage: s.symbol, emphasis: s.done, chevron: false)
                }
                OnboardingProgress(value: 0.70)
                PrimaryButton(title: "Keep going", systemImage: "arrow.right.circle.fill") {
                    done.toggle()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { onContinue() }
                }
            }
            .padding(18).padding(.top, 12)
        }
        .celebration(trigger: $done)
    }
}

// Reusable settings container
struct SettingsGroup<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        VStack(spacing: 0) { content }
            .background(Theme.Palette.card)
            .clipShape(RoundedRectangle(cornerRadius: 19, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 19, style: .continuous).stroke(Theme.Palette.line))
    }
}
