import SwiftUI

struct OnboardingFlow: View {
    @EnvironmentObject var state: AppState
    @State private var step = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            Theme.background
            Group {
                // Value-first ordering: a quick legal/transparency gate (age + AI
                // disclosure), then the personalization "aha" (self-reflection →
                // baseline → companion) BEFORE we ask anyone to create an account.
                // Account + consent + locale come once the user is invested, with
                // the reminder opt-in deliberately last (highest-leverage retention).
                switch step {
                case 0: WelcomeScreen(onBegin: next, onPreview: { state.hasOnboarded = true })
                case 1: AgeGateScreen(onContinue: next)
                case 2: DisclosureScreen(onContinue: next)
                case 3: GoalsScreen(onContinue: next)
                case 4: BaselineScreen(onContinue: next)
                case 5: CompanionScreen(onContinue: next)
                case 6: SignupScreen(onContinue: next)
                case 7: ConsentScreen(onContinue: next)
                case 8: LanguageScreen(onContinue: next)
                case 9: NotificationsScreen(onContinue: next)
                default: FirstPlanScreen(onFinish: { state.hasOnboarded = true })
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
    var onPreview: () -> Void
    var body: some View {
        ZStack(alignment: .bottom) {
            Photo(url: Dummy.Img.welcome, symbol: "sparkles")
                .frame(maxWidth: .infinity).frame(height: 320)
                .overlay(LinearGradient(colors: [.clear, Theme.Palette.night], startPoint: .top, endPoint: .bottom))
                .frame(maxHeight: .infinity, alignment: .top)
                .ignoresSafeArea()
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
                OnboardingProgress(value: 0.14).padding(.top, 8).entrance(3)
                PrimaryButton(title: "Begin private setup", systemImage: "sparkles", action: onBegin).entrance(4)
                // Skipping setup would bypass the age gate + AI disclosure, so the
                // preview shortcut ships only in debug builds (used by UI tests).
                #if DEBUG
                SecondaryButton(title: "Preview app", systemImage: "house", action: onPreview).entrance(5)
                #endif
            }
            .padding(24)
        }
    }
}

// Generic onboarding step scaffold
private struct StepScaffold<Content: View>: View {
    let eyebrow: String
    let title: String
    let image: String
    /// One warm, step-specific sentence (no boilerplate).
    let caption: String
    var progress: Double
    /// When false, the Continue button is disabled (e.g. an unmet age gate).
    var canContinue: Bool = true
    var onContinue: () -> Void
    @ViewBuilder var content: Content

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                Text(eyebrow).eyebrow().entrance(0)
                Text(title).displayFont(28).foregroundStyle(Theme.Palette.text).entrance(1)
                Photo(url: image, symbol: "sparkles")
                    .frame(height: 132).frame(maxWidth: .infinity)
                    .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
                    .entrance(2)
                Text(caption)
                    .appFont(13).foregroundStyle(Theme.Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                    .entrance(3)
                content.entrance(4)
                OnboardingProgress(value: progress).entrance(5)
                PrimaryButton(title: "Continue", action: onContinue)
                    .disabled(!canContinue)
                    .opacity(canContinue ? 1 : 0.45)
                    .entrance(6)
            }
            .padding(18).padding(.top, 12)
        }
    }
}

// MARK: 7 — Signup (after the value moment, so there's something to save)
private struct SignupScreen: View {
    var onContinue: () -> Void
    var body: some View {
        StepScaffold(eyebrow: "Account creation", title: "Save your space", image: Dummy.Img.chat,
                     caption: "You've shaped your plan — create your private space to keep it. No social feed, no sharing, just you.",
                     progress: 0.68, onContinue: onContinue) {
            ListRow(title: "Private by design", subtitle: "Email, Apple, or Google", systemImage: "lock", imageURL: Dummy.Img.privacy, emphasis: true)
            ListRow(title: "Personalized next step", subtitle: "CereBro adapts your plan", systemImage: "heart", imageURL: Dummy.Img.meditate)
            ListRow(title: "You stay in control", subtitle: "Data choices editable anytime", systemImage: "lock", imageURL: Dummy.Img.privacy)
        }
    }
}

// MARK: 1 — Age gate (kept early: fast legal gate)
private struct AgeGateScreen: View {
    var onContinue: () -> Void
    @State private var confirmed = false
    var body: some View {
        StepScaffold(eyebrow: "Adult-only safety gate", title: "Age Gate", image: Dummy.Img.calm,
                     caption: "CereBro is built for adults. A quick check keeps the experience safe and appropriate.",
                     progress: 0.22, canContinue: confirmed, onContinue: onContinue) {
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
        StepScaffold(eyebrow: "AI boundary screen", title: "AI Disclosure", image: Dummy.Img.support,
                     caption: "Honesty first. Here's exactly what your AI companion can and can't do for you.",
                     progress: 0.30, onContinue: onContinue) {
            HStack(spacing: 10) {
                Card { VStack(alignment: .leading, spacing: 4) {
                    Text("Can help").appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                    Text("Listen, reflect, guide tools, suggest a plan.").appFont(12).foregroundStyle(Theme.Palette.muted)
                }}
                Card { VStack(alignment: .leading, spacing: 4) {
                    Text("Cannot do").appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                    Text("Diagnose, prescribe, replace therapy, or handle emergencies.").appFont(12).foregroundStyle(Theme.Palette.muted)
                }}
            }
        }
    }
}

// MARK: 8 — Consent (after signup: choices for the data you're about to create)
private struct ConsentScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    var body: some View {
        StepScaffold(eyebrow: "Privacy choices", title: "Consent", image: Dummy.Img.privacy,
                     caption: "You decide what CereBro remembers. Change any of these later in Settings.",
                     progress: 0.76, onContinue: onContinue) {
            SettingsGroup {
                ToggleRow(title: "Mood history", subtitle: "Used for insights", isOn: $state.consent.moodHistory); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: "AI memory", subtitle: "Goals and preferences", isOn: $state.consent.aiMemory); Divider().overlay(Theme.Palette.line)
                ToggleRow(title: "Voice storage", subtitle: "Off by default", isOn: $state.consent.voiceStorage)
            }
        }
    }
}

// MARK: 9 — Language
private struct LanguageScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    @State private var selection: Set<String> = ["English", "Hinglish"]
    var body: some View {
        StepScaffold(eyebrow: "Localization setup", title: "Language", image: Dummy.Img.premium,
                     caption: "Talk and reflect in the language you think in. Mix more than one if that's you.",
                     progress: 0.84, onContinue: persistAndContinue) {
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

// MARK: 3 — Self-reflection (the value moment — moved up, before account/consent)
private struct GoalsScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    @State private var motivations: Set<String> = ["Focus", "Calm"]
    @State private var goals: Set<String> = ["Reduce stress", "Sleep better"]

    /// Flat, order-stable list of every goal item across categories.
    private var allGoalItems: [String] { Dummy.goalCategories.flatMap(\.items) }

    var body: some View {
        StepScaffold(eyebrow: "Self-reflection", title: "What matters now", image: Dummy.Img.meditate,
                     caption: "A quick self-reflection: what drives you, and what you'd like to practice. CereBro shapes your plan and conversation starters around it.",
                     progress: 0.40, onContinue: persistAndContinue) {
            VStack(alignment: .leading, spacing: 16) {
                Text("What drives you").eyebrow()
                ChipRow(options: Dummy.motivations, selection: $motivations)

                ForEach(Dummy.goalCategories, id: \.category) { group in
                    Text(group.category).eyebrow()
                    ChipRow(options: group.items, selection: $goals)
                }
            }
        }
        .onAppear {
            if !state.selectedMotivations.isEmpty { motivations = Set(state.selectedMotivations) }
            if !state.selectedGoals.isEmpty { goals = Set(state.selectedGoals) }
        }
    }

    private func persistAndContinue() {
        state.selectedMotivations = Dummy.motivations.filter(motivations.contains)
        state.selectedGoals = allGoalItems.filter(goals.contains)
        // Mirror to the profile if already signed in (best-effort).
        if backend.isConnected {
            backend.saveAssessment(motivations: state.selectedMotivations, goals: state.selectedGoals)
        }
        onContinue()
    }
}

// MARK: 4 — Baseline (a real, stored starting point)
private struct BaselineScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    @State private var stress = 3
    @State private var sleep = 3
    var body: some View {
        StepScaffold(eyebrow: "Non-clinical starting point", title: "Baseline Check", image: Dummy.Img.calm,
                     caption: "A gentle snapshot of where you're starting — so you can see how far you come.",
                     progress: 0.50, onContinue: { state.setBaseline(stress: stress, sleep: sleep); onContinue() }) {
            VStack(spacing: 20) {
                BaselineScale(label: "Stress right now", low: "Calm", high: "Overwhelmed", value: $stress)
                BaselineScale(label: "Sleep lately", low: "Restless", high: "Restful", value: $sleep)
            }
        }
        .onAppear {
            if state.baselineStress > 0 { stress = state.baselineStress }
            if state.baselineSleep > 0 { sleep = state.baselineSleep }
        }
    }
}

/// A 1–5 self-rating used to capture the onboarding baseline.
private struct BaselineScale: View {
    let label: String
    let low: String
    let high: String
    @Binding var value: Int
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(label).appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
            HStack(spacing: 8) {
                ForEach(1...5, id: \.self) { n in
                    Button { value = n; Haptics.selection() } label: {
                        Text("\(n)")
                            .appFont(15, weight: .bold)
                            .foregroundStyle(value == n ? Theme.Palette.ink : Theme.Palette.muted)
                            .frame(maxWidth: .infinity, minHeight: 46)
                            .background(value == n ? Theme.Palette.lav : Theme.Palette.card,
                                        in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                            .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Theme.Palette.line))
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("\(label) \(n) of 5")
                }
            }
            HStack {
                Text(low).appFont(10.5).foregroundStyle(Theme.Palette.muted2)
                Spacer()
                Text(high).appFont(10.5).foregroundStyle(Theme.Palette.muted2)
            }
        }
    }
}

// MARK: 5 — Companion
private struct CompanionScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    var body: some View {
        StepScaffold(eyebrow: "Support style setup", title: "AI Companion", image: Dummy.Img.support,
                     caption: "Choose the voice that feels right. Soft and reflective, or clear and structured.",
                     progress: 0.58, onContinue: onContinue) {
            ListRow(title: "Calm Guide", subtitle: "Soft, gentle and reflective", systemImage: "moon",
                    imageURL: Dummy.Img.support, emphasis: state.companion == "Calm Guide") { state.companion = "Calm Guide" }
            ListRow(title: "Scientific", subtitle: "Structured and evidence-informed", systemImage: "brain",
                    imageURL: Dummy.Img.write, emphasis: state.companion == "Scientific") { state.companion = "Scientific" }
        }
    }
}

// MARK: 10 — Notifications (kept last: highest-leverage retention opt-in)
private struct NotificationsScreen: View {
    var onContinue: () -> Void
    @EnvironmentObject var state: AppState
    @State private var selection: Set<String> = ["Evening 7 PM"]
    var body: some View {
        StepScaffold(eyebrow: "Gentle reminder setup", title: "Notifications", image: Dummy.Img.bell,
                     caption: "A couple of soft nudges a day — never noisy, always easy to turn off.",
                     progress: 0.92, onContinue: persistAndContinue) {
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

// MARK: 11 — First plan
private struct FirstPlanScreen: View {
    var onFinish: () -> Void
    @EnvironmentObject var state: AppState
    @State private var done = false

    /// Headline the plan around the user's first chosen goal.
    private var planTitle: String {
        switch state.selectedGoals.first {
        case "Sleep better":      return "Sleep deeper"
        case "Stop overthinking": return "Quiet the noise"
        case "Build confidence":  return "Steady confidence"
        case "Feel less alone":   return "Feel more connected"
        default:                  return "Ease work stress"
        }
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                Text("The app becomes agentic").eyebrow()
                Text("First Plan").displayFont(28).foregroundStyle(Theme.Palette.text)
                HeroCard(tag: "7-day plan", title: planTitle,
                         subtitle: "Breathing, journaling, and sleep support built around your baseline.",
                         cta: "Start today", imageURL: Dummy.Img.plan) {
                    done.toggle()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { onFinish() }
                }
                ForEach(Dummy.planSteps) { s in
                    ListRow(title: s.title, subtitle: s.detail, systemImage: s.symbol, imageURL: s.imageURL, emphasis: s.done)
                }
                PrimaryButton(title: "Enter CereBro", systemImage: "arrow.right.circle.fill") {
                    done.toggle()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { onFinish() }
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
