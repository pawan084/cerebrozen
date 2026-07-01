import SwiftUI

// MARK: - SOS reset
struct SOSView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Fast anxiety/stress reset", title: "SOS Reset",
                       trailingSystemImage: "exclamationmark.triangle", accent: Theme.Accent.warm) {
            ToolBanner(imageURL: Dummy.Img.support, symbol: "lifepreserver",
                       caption: "Feeling overwhelmed? Three fast ways back to steady.",
                       accent: Theme.Accent.warm)
            NavRow(title: "2-minute breathing", subtitle: "Fast body reset", systemImage: "wind", imageURL: Dummy.Img.breath, emphasis: true) { BreathingView() }
            NavRow(title: "5-4-3-2-1 grounding", subtitle: "Return to present moment", systemImage: "checkmark.shield", imageURL: Dummy.Img.ground) { GroundingView() }
            NavRow(title: "Save reflection", subtitle: "Add result to private journal", systemImage: "book", imageURL: Dummy.Img.journal) { JournalEntryView() }
            NavRow(title: "Talk to someone now", subtitle: "Crisis & human support", systemImage: "phone", imageURL: Dummy.Img.support) { CrisisView() }
        }
    }
}

// MARK: - Breathing
struct BreathingView: View {
    @State private var done = false
    var body: some View {
        ScreenScaffold(eyebrow: "Guided breathing interaction", title: "Breathing",
                       trailingSystemImage: "wind", accent: Theme.Accent.breathe) {
            ToolBanner(imageURL: Dummy.Img.breath, symbol: "wind",
                       caption: "Follow the orb — in for four, hold, out for four.",
                       accent: Theme.Accent.breathe)

            BreathingPacer(accent: Theme.Accent.breathe)
                .frame(maxWidth: .infinity)

            NavRow(title: "Save reflection", subtitle: "Add result to private journal", systemImage: "book", imageURL: Dummy.Img.journal) { JournalEntryView() }
            PrimaryButton(title: "Continue") { done.toggle() }
        }
        .celebration(trigger: $done, accent: Theme.Accent.breathe)
    }
}

/// Immersive guided box-breathing: concentric rings expand on the inhale, hold,
/// then contract on the exhale, with a live per-phase countdown, a gentle haptic
/// at each transition, and a running cycle count. Honors Reduce Motion (the orb
/// settles to a calm mid-size while the spoken cadence + countdown continue).
struct BreathingPacer: View {
    var accent: Color = Theme.Accent.breathe
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    enum Step: CaseIterable {
        case inhale, holdIn, exhale, holdOut
        var label: String {
            switch self {
            case .inhale: return "Breathe in"
            case .holdIn, .holdOut: return "Hold"
            case .exhale: return "Breathe out"
            }
        }
        var seconds: Int { 4 }
        var expanded: Bool { self == .inhale || self == .holdIn }
    }

    @State private var step: Step = .inhale
    @State private var scale: CGFloat = 0.5
    @State private var count = 4
    @State private var cycles = 0

    var body: some View {
        VStack(spacing: 16) {
            Text(step.label)
                .displayFont(26)
                .foregroundStyle(Theme.Palette.text)
                .contentTransition(.opacity)
                .animation(.easeInOut(duration: 0.4), value: step)

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
            for s in Step.allCases {
                step = s
                if !reduceMotion {
                    withAnimation(.easeInOut(duration: Double(s.seconds))) {
                        scale = s.expanded ? 1.0 : 0.5
                    }
                }
                Haptics.soft(intensity: s == .inhale ? 0.6 : 0.35)
                for n in stride(from: s.seconds, through: 1, by: -1) {
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
            ListRow(title: "5 things you can see", subtitle: "Look around gently", systemImage: "eye", imageURL: Dummy.Img.ground, emphasis: true)
            ListRow(title: "4 things you can feel", subtitle: "Feet, chair, clothes, air", systemImage: "hand.raised", imageURL: Dummy.Img.breath)
            ListRow(title: "3 things you can hear", subtitle: "Near and far sounds", systemImage: "ear", imageURL: Dummy.Img.ocean)
            NavRow(title: "Save reflection", subtitle: "Add result to private journal", systemImage: "book", imageURL: Dummy.Img.journal) { JournalEntryView() }
            PrimaryButton(title: "Continue") { done.toggle() }
        }
        .celebration(trigger: $done, accent: Theme.Accent.breathe)
    }
}

// MARK: - CBT reframe
struct CBTReframeView: View {
    @State private var thought = "I will fail in tomorrow's meeting…"
    @State private var showBalanced = false
    var body: some View {
        ScreenScaffold(eyebrow: "Structured thought reframe", title: "CBT Reframe", trailingSystemImage: "brain") {
            ToolBanner(imageURL: Dummy.Img.journal, symbol: "brain",
                       caption: "Untangle a worried thought, one step at a time.")
            Card {
                TextField("", text: $thought, axis: .vertical)
                    .appFont(13).foregroundStyle(Theme.Palette.soft).lineLimit(3...)
            }
            StepRow(number: 1, text: "Evidence for this thought")
            StepRow(number: 2, text: "Evidence against this thought")
            StepRow(number: 3, text: "A more balanced thought")
            NavRow(title: "See balanced thought", subtitle: "CBT output", systemImage: "sparkles", imageURL: Dummy.Img.write, emphasis: true) { BalancedThoughtView() }
            PrimaryButton(title: "Continue") { showBalanced = true }
        }
        .navigationDestination(isPresented: $showBalanced) { BalancedThoughtView() }
    }
}

struct StepRow: View {
    let number: Int
    let text: String
    var body: some View {
        HStack(spacing: 10) {
            Text("\(number)")
                .appFont(13, weight: .bold).foregroundStyle(Theme.Palette.ink)
                .frame(width: 26, height: 26).background(Theme.Palette.cream, in: Circle())
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
        ScreenScaffold(eyebrow: "CBT output screen", title: "Balanced Thought", trailingSystemImage: "brain") {
            ToolBanner(imageURL: Dummy.Img.write, symbol: "sparkles",
                       caption: "Here's the calmer, truer version of the thought.")
            HStack(spacing: 10) {
                Card { VStack(alignment: .leading, spacing: 4) {
                    Text("Before").appFont(12).foregroundStyle(Theme.Palette.muted)
                    Text("I will fail.").appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                }}
                Card { VStack(alignment: .leading, spacing: 4) {
                    Text("After").appFont(12).foregroundStyle(Theme.Palette.muted)
                    Text("I can prepare three clear points.").appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                }}
            }
            NavRow(title: "Save reflection", subtitle: "Add result to private journal", systemImage: "book", imageURL: Dummy.Img.journal) { JournalEntryView() }
            PrimaryButton(title: "Continue") { done.toggle() }
        }
        .celebration(trigger: $done)
    }
}
