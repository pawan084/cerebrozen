import SwiftUI

// MARK: - SOS reset
struct SOSView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Fast anxiety/stress reset", title: "SOS Reset",
                       trailingSystemImage: "exclamationmark.triangle", accent: Theme.Accent.warm) {
            ToolBanner(imageURL: Dummy.Img.support, symbol: "lifepreserver",
                       caption: "Feeling overwhelmed? A few fast ways back to steady.",
                       accent: Theme.Accent.warm)
            NavRow(title: "2-minute breathing", subtitle: "Fast body reset", systemImage: "wind", imageURL: Dummy.Img.breath, emphasis: true) { BreathingView() }
            NavRow(title: "5-4-3-2-1 grounding", subtitle: "Return to present moment", systemImage: "checkmark.shield", imageURL: Dummy.Img.ground) { GroundingView() }
            NavRow(title: "Save reflection", subtitle: "Add result to private journal", systemImage: "book", imageURL: Dummy.Img.journal) { JournalEntryView() }
            NavRow(title: "Talk to someone now", subtitle: "Crisis & human support", systemImage: "phone", imageURL: Dummy.Img.support) { CrisisView() }
        }
        .toolAmbience(.ocean)
    }
}

// MARK: - Breathing
struct BreathingView: View {
    /// One engine, three paces (IOS_PARITY #1, Android Breathe.kt precedent).
    var preset: BreathingPacer.Preset = .box
    @State private var done = false
    var body: some View {
        ScreenScaffold(eyebrow: "A minute to breathe", title: preset.title,
                       trailingSystemImage: "wind", accent: Theme.Accent.breathe) {
            ToolBanner(imageURL: Dummy.Img.breath, symbol: "wind",
                       caption: preset.caption,
                       accent: Theme.Accent.breathe)

            BreathingPacer(accent: Theme.Accent.breathe, preset: preset)
                .frame(maxWidth: .infinity)

            NavRow(title: "Save reflection", subtitle: "Add result to private journal", systemImage: "book", imageURL: Dummy.Img.journal) { JournalEntryView() }
            WhyThisWorks(text: "Paced breathing is used in clinical distress-tolerance and relaxation protocols. Slowing the breath activates the body's calming response.")
            // Celebrate the first-ever completion; after that a quiet haptic
            // (F5 — reward at notable moments, not every rep).
            PrimaryButton(title: "Continue") {
                if CelebrationGate.firstTime("breathing") { done.toggle() } else { Haptics.success() }
            }
        }
        .toolAmbience(.wind)
        .celebration(trigger: $done, accent: Theme.Accent.breathe)
    }
}

/// Immersive guided box-breathing: concentric rings expand on the inhale, hold,
/// then contract on the exhale, with a live per-phase countdown, a gentle haptic
/// at each transition, and a running cycle count. Honors Reduce Motion (the orb
/// settles to a calm mid-size while the spoken cadence + countdown continue).
struct BreathingPacer: View {
    /// A named pace: phases of (label, seconds, expanded-orb). One engine
    /// drives every breathing surface (IOS_PARITY #1).
    struct Preset {
        let title: String
        let caption: String
        let phases: [(label: String, seconds: Int, expanded: Bool)]

        /// Box 4-4-4-4 — the classic steadying pattern.
        static let box = Preset(
            title: "Breathing",
            caption: "Follow the orb — in for four, hold, out for four.",
            phases: [("Breathe in", 4, true), ("Hold", 4, true),
                     ("Breathe out", 4, false), ("Hold", 4, false)])
        /// Color 4-2-6 — the longer-exhale pace the old game used.
        static let color = Preset(
            title: "Color breathing",
            caption: "A soft glow — in for four, hold, and a long six out.",
            phases: [("Breathe in", 4, true), ("Hold", 2, true), ("Breathe out", 6, false)])
        /// Reset — two gentle minutes, no holds (the onboarding first reset).
        static let reset = Preset(
            title: "Two-minute reset",
            caption: "Nothing to get right — just in for four, out for six.",
            phases: [("Breathe in", 4, true), ("Breathe out", 6, false)])
    }

    var accent: Color = Theme.Accent.breathe
    var preset: Preset = .box
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var phaseIndex = 0
    @State private var scale: CGFloat = 0.5
    @State private var count = 4
    @State private var cycles = 0

    var body: some View {
        VStack(spacing: 16) {
            Text(preset.phases[phaseIndex].label)
                .displayFont(26)
                .foregroundStyle(Theme.Palette.text)
                .contentTransition(.opacity)
                .animation(.easeInOut(duration: 0.4), value: phaseIndex)

            ZStack {
                ForEach(0..<3, id: \.self) { i in
                    Circle().stroke(accent.opacity(0.12), lineWidth: 1)
                        .frame(width: 150 + CGFloat(i) * 42, height: 150 + CGFloat(i) * 42)
                }
                Circle().fill(orb)
                    .frame(width: 150, height: 150)
                    .blur(radius: 28).opacity(0.7)
                    .scaleEffect(scale * 1.12)
                Circle().fill(orb)
                    .frame(width: 150, height: 150)
                    .overlay(Circle().fill(RadialGradient(colors: [.white.opacity(0.85), .clear],
                                                          center: .init(x: 0.36, y: 0.3),
                                                          startRadius: 0, endRadius: 64)))
                    .scaleEffect(scale)
                    .shadow(color: accent.opacity(0.5), radius: 28)
                Text("\(count)")
                    .displayFont(34).foregroundStyle(Theme.Palette.ink.opacity(0.85))
            }
            .frame(height: 250)

            Text(cycles == 0 ? "Follow the orb" : "\(cycles) cycle\(cycles == 1 ? "" : "s") complete")
                .appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted2)
        }
        .task { await run() }
    }

    private var orb: RadialGradient {
        RadialGradient(colors: [.white, Theme.Palette.soft, accent],
                       center: .init(x: 0.38, y: 0.34), startRadius: 2, endRadius: 95)
    }

    private func run() async {
        scale = reduceMotion ? 0.8 : 0.5
        while !Task.isCancelled {
            for (i, phase) in preset.phases.enumerated() {
                phaseIndex = i
                if !reduceMotion {
                    withAnimation(.easeInOut(duration: Double(phase.seconds))) {
                        scale = phase.expanded ? 1.0 : 0.5
                    }
                }
                Haptics.soft(intensity: i == 0 ? 0.6 : 0.35)
                for n in stride(from: phase.seconds, through: 1, by: -1) {
                    count = n
                    try? await Task.sleep(nanoseconds: 1_000_000_000)
                    if Task.isCancelled { return }
                }
            }
            cycles += 1
        }
    }
}

// MARK: - Grounding
struct GroundingView: View {
    @State private var done = false
    var body: some View {
        ScreenScaffold(eyebrow: "5-4-3-2-1 sensory reset", title: "Grounding",
                       trailingSystemImage: "checkmark.shield", accent: Theme.Accent.breathe) {
            ToolBanner(imageURL: Dummy.Img.ground, symbol: "leaf",
                       caption: "Anchor to your senses, one at a time.",
                       accent: Theme.Accent.breathe)
            RowLabel(title: "5 things you can see", subtitle: "Look around gently", systemImage: "eye", chevron: false)
            RowLabel(title: "4 things you can feel", subtitle: "Feet, chair, clothes, air", systemImage: "hand.raised", chevron: false)
            RowLabel(title: "3 things you can hear", subtitle: "Near and far sounds", systemImage: "ear", chevron: false)
            RowLabel(title: "2 things you can smell", subtitle: "Take a slow breath in", systemImage: "nose", chevron: false)
            RowLabel(title: "1 thing you can taste", subtitle: "Notice it without judging", systemImage: "mouth", chevron: false)
            NavRow(title: "Save reflection", subtitle: "Add result to private journal", systemImage: "book", imageURL: Dummy.Img.journal) { JournalEntryView() }
            WhyThisWorks(text: "Sensory grounding redirects attention from spiralling thoughts to the here-and-now — a widely taught anxiety-management skill.")
            PrimaryButton(title: "Continue") {
                if CelebrationGate.firstTime("grounding") { done.toggle() } else { Haptics.success() }
            }
        }
        .toolAmbience(.rain)
        .celebration(trigger: $done, accent: Theme.Accent.breathe)
    }
}

// MARK: - CBT reframe
struct CBTReframeView: View {
    @State private var thought = ""
    @State private var showBalanced = false
    var body: some View {
        ScreenScaffold(eyebrow: "A kinder look at a thought", title: "CBT Reframe", trailingSystemImage: "brain") {
            ToolBanner(imageURL: Dummy.Img.journal, symbol: "brain",
                       caption: "Untangle a worried thought, one step at a time.")
            Card {
                TextField("Write the worried thought…", text: $thought, axis: .vertical)
                    .appFont(13).foregroundStyle(Theme.Palette.soft).lineLimit(3...)
            }
            StepRow(number: 1, text: "Evidence for this thought")
            StepRow(number: 2, text: "Evidence against this thought")
            StepRow(number: 3, text: "A more balanced thought")
            WhyThisWorks(text: "Reframing is a core cognitive-behavioural (CBT) technique — the approach with the strongest evidence among app-delivered tools (Linardon et al. 2024, World Psychiatry).")
            NavRow(title: "See balanced thought", subtitle: "CBT output", systemImage: "sparkles", imageURL: Dummy.Img.write, emphasis: true) { BalancedThoughtView() }
            PrimaryButton(title: "Continue") { showBalanced = true }
        }
        .toolAmbience(.drone)
        .navigationDestination(isPresented: $showBalanced) { BalancedThoughtView() }
    }
}

struct StepRow: View {
    let number: Int
    let text: String
    var body: some View {
        HStack(spacing: 10) {
            Text("\(number)")
                .appFont(13, weight: .bold).foregroundStyle(Theme.Palette.onPrimary)
                .frame(width: 26, height: 26).background(Theme.Palette.primaryPill, in: Circle())
            Text(text).appFont(13).foregroundStyle(Theme.Palette.soft)
            Spacer()
        }
        .padding(12)
        .background(Theme.Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 17, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 17, style: .continuous).stroke(Theme.Palette.line))
    }
}

// MARK: - Balanced thought (CBT output)
struct BalancedThoughtView: View {
    @State private var done = false
    var body: some View {
        ScreenScaffold(eyebrow: "From worry to balance", title: "Balanced Thought", trailingSystemImage: "brain") {
            ToolBanner(imageURL: Dummy.Img.write, symbol: "sparkles",
                       caption: "An example of the calmer, truer version of a thought.")
            HStack(spacing: 10) {
                Card { VStack(alignment: .leading, spacing: 4) {
                    Text("Before · example").appFont(12).foregroundStyle(Theme.Palette.muted)
                    Text("I will fail.").appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                }}
                Card { VStack(alignment: .leading, spacing: 4) {
                    Text("After · example").appFont(12).foregroundStyle(Theme.Palette.muted)
                    Text("I can prepare three clear points.").appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                }}
            }
            NavRow(title: "Save reflection", subtitle: "Add result to private journal", systemImage: "book", imageURL: Dummy.Img.journal) { JournalEntryView() }
            PrimaryButton(title: "Continue") {
                if CelebrationGate.firstTime("balanced_thought") { done.toggle() } else { Haptics.success() }
            }
        }
        .toolAmbience(.drone)
        .celebration(trigger: $done)
    }
}
