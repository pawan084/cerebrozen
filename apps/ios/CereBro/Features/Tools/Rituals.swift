import SwiftUI

// The three guided routines, ported from the web app (apps/app `/sleep/ritual`,
// `/games/ritual`, `/games/imagery`) and Android `ui/screens/Rituals.kt`. Copy
// is hand-synced with both, including every deliberate departure from the
// sibling build recorded there:
//
//  • the wind-down settles on **in 4, out 6** rather than the reference's
//    4-7-8 — a longer exhale than inhale is the part of slow breathing with
//    real evidence, and this app doesn't ship a ratio it can't source. That is
//    exactly `BreathingPacer.Preset.reset`, so no new rhythm is invented here;
//  • the ritual builder drops three of the reference's eight blocks: 4-7-8
//    breathing (same reason), Disidentification (Psychosynthesis, thin
//    evidence) and affirmation reading — generic positive self-statements
//    *lower* mood in people with low self-esteem (Wood, Perunovic & Lee,
//    Psychological Science, 2009), which is the population a wellness app
//    selects for. It adds what the reference lacks: the **cue**;
//  • guided imagery does **not** say "you are safe here, nothing can harm
//    you". Safe-place imagery is exactly where that promise can break, so the
//    screen keeps the mechanism, drops the absolute reassurance, and warns on
//    the way *in* that stopping is a normal outcome.
//
// Nothing here writes anywhere unless the user asks: written steps live in
// view state until "Save to journal" is tapped, and the screen says so.
//
// Every auto-advancing timer is gated on `-resetState` (the UITest launch
// argument), the same posture the splash and the audio engine take — a screen
// that re-renders on its own makes the suite wait forever and the screenshots
// nondeterministic. Manual "Skip" still walks the sequence under test.

// MARK: - Pure seams (no SwiftUI; safe to pin from CereBroTests)

/// The next prompt index, or nil when the sequence is finished. Shared by the
/// body scan and the imagery reel so "what happens on the last one" is decided
/// once rather than twice in a view branch.
func nextPromptIndex(_ current: Int, of total: Int) -> Int? {
    current >= total - 1 ? nil : current + 1
}

/// One selectable block of a personal ritual. Every one is an exercise the app
/// already ships with its own provenance — the builder sequences, it never
/// invents.
struct RitualBlock: Identifiable {
    let id: String
    let label: String
    let note: String
    let minutes: Int
}

let ritualBlocks: [RitualBlock] = [
    .init(id: "settle", label: "Settle the breath", note: "In 4, out 6 — slow breaths.", minutes: 1),
    .init(id: "box", label: "Box breathing", note: "Four equal counts: in, hold, out, hold.", minutes: 2),
    .init(id: "ground", label: "5-4-3-2-1 grounding", note: "Name what's around you, sense by sense.", minutes: 2),
    .init(id: "scan", label: "Let go, top to bottom", note: "A short body scan for held tension.", minutes: 1),
    .init(id: "dump", label: "Empty the desk", note: "Write out whatever's still loud.", minutes: 3),
    .init(id: "good", label: "Three good things", note: "Name what went right today.", minutes: 2),
    .init(id: "intention", label: "One intention", note: "Decide what this next stretch is for.", minutes: 1),
    .init(id: "reflect", label: "Write freely", note: "No prompt, no rules.", minutes: 5),
]

/// Stored block ids → a ritual we can actually run: unknown ids (an older or
/// newer install, a hand-edited default) and duplicates are dropped. A
/// duplicate would render two identical rows whose reorder controls fight over
/// one index.
func sanitizeRitual(_ stored: [String],
                    known: [String] = ritualBlocks.map(\.id)) -> [String] {
    var seen: [String] = []
    for id in stored where known.contains(id) && !seen.contains(id) { seen.append(id) }
    return seen
}

/// Total minutes for a chosen order — the honest "about N min" on the card.
func ritualMinutes(_ order: [String]) -> Int {
    order.reduce(0) { $0 + (ritualBlocks.first { $0.id == $1 }?.minutes ?? 0) }
}

/// The personal ritual: which blocks, in which order, and the cue it hangs off.
/// Device-local by design — there is no server model for a ritual, and
/// inventing one to sync a list of eight ids would be the wrong trade. The
/// screen says so rather than letting the user assume it follows their account.
enum RitualStore {
    static let blocksKey = "ritual_blocks"
    static let cueKey = "ritual_cue"

    static func blocks() -> [String] {
        sanitizeRitual(UserDefaults.standard.stringArray(forKey: blocksKey) ?? [])
    }
    static func cue() -> String { UserDefaults.standard.string(forKey: cueKey) ?? "" }

    static func save(_ order: [String], cue: String) {
        UserDefaults.standard.set(sanitizeRitual(order), forKey: blocksKey)
        UserDefaults.standard.set(cue.trimmingCharacters(in: .whitespacesAndNewlines), forKey: cueKey)
    }
}

/// UITest launch flag — see the file header.
private var underUITest: Bool { UserDefaults.standard.bool(forKey: "resetState") }

// MARK: - Shared step views
// Each owns its mechanics and takes its words from the caller: the wind-down
// speaks to someone already in bed, the builder to someone at any hour.

private struct StepProgress: View {
    let step: Int
    let total: Int
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                ForEach(0..<total, id: \.self) { i in
                    Capsule()
                        .fill(i <= step ? Theme.Palette.lav : Theme.Palette.line)
                        .frame(height: 4)
                }
            }
            Text("Step \(step + 1) of \(total)")
                .appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted2)
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Step \(step + 1) of \(total)")
    }
}

/// A writing step. The privacy posture is the point: what someone writes lives
/// in this view until they press Save, and the screen says so before they
/// start. These prompts invite the most unguarded writing a user will do all
/// day, and silence about where it goes is not the same as discarding it.
private struct WritingStep: View {
    let eyebrow: String
    let title: String
    let body_: String
    let placeholder: String
    let journalTitle: String
    let journalTag: String
    let journalSymbol: String
    let why: String
    let cta: String
    var lines: ClosedRange<Int> = 5...12
    let onNext: () -> Void

    @EnvironmentObject private var state: AppState
    @EnvironmentObject private var backend: BackendService
    @State private var text = ""
    @State private var saved = false

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                Text(eyebrow).appFont(11, weight: .heavy).foregroundStyle(Theme.Palette.lavText)
                Text(title).displayFont(20).foregroundStyle(Theme.Palette.text)
                Text(body_).appFont(13).foregroundStyle(Theme.Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                TextField(placeholder, text: $text, axis: .vertical)
                    .appFont(13).foregroundStyle(Theme.Palette.soft).lineLimit(lines)
                    .padding(12)
                    .background(Theme.Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityLabel(title)
                Text("This stays on your device and is never sent anywhere — unless you choose to save it.")
                    .appFont(11).foregroundStyle(Theme.Palette.muted2)
                    .fixedSize(horizontal: false, vertical: true)
                PrimaryButton(title: cta) { onNext() }
                Button {
                    save()
                } label: {
                    Text(saved ? "Saved to journal" : "Save to journal")
                        .appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted)
                }
                .buttonStyle(.pressable)
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || saved)
            }
        }
        WhyThisWorks(text: why)
    }

    private func save() {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !saved else { return }
        let entry = JournalEntry(title: journalTitle, tags: [journalTag], date: "Today",
                                 symbol: journalSymbol, imageURL: Dummy.Img.write, body: trimmed)
        state.journalEntries.insert(entry, at: 0)
        state.recordActivity()
        backend.mirrorJournal(entry, body: trimmed)
        saved = true
    }
}

private struct ThreeGoodThingsStep: View {
    let eyebrow: String
    let title: String
    let body_: String
    let why: String
    let cta: String
    let skip: String
    let onNext: () -> Void

    @State private var items = ["", "", ""]
    // Never gated on filling all three: a day where only one thing comes to
    // mind is exactly the day not to be blocked by a form.
    private var any: Bool { items.contains { !$0.trimmingCharacters(in: .whitespaces).isEmpty } }

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                Text(eyebrow).appFont(11, weight: .heavy).foregroundStyle(Theme.Palette.lavText)
                Text(title).displayFont(20).foregroundStyle(Theme.Palette.text)
                Text(body_).appFont(13).foregroundStyle(Theme.Palette.muted)
                ForEach(0..<3, id: \.self) { i in
                    TextField("Good thing \(i + 1)", text: $items[i])
                        .appFont(13).foregroundStyle(Theme.Palette.soft)
                        .padding(12)
                        .background(Theme.Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .accessibilityLabel("Good thing \(i + 1)")
                }
                PrimaryButton(title: any ? cta : skip) { onNext() }
            }
        }
        WhyThisWorks(text: why)
    }
}

/// One line at a time with a dot per prompt — the body scan. Auto-advance is
/// gated under UI test; the button walks it either way.
private struct PromptReelStep: View {
    let eyebrow: String
    let prompts: [String]
    let seconds: Double
    let why: String
    let cta: String
    let onNext: () -> Void

    @State private var index = 0
    private var isLast: Bool { nextPromptIndex(index, of: prompts.count) == nil }

    var body: some View {
        Card {
            VStack(spacing: 14) {
                Text(eyebrow).appFont(11, weight: .heavy).foregroundStyle(Theme.Palette.lavText)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text(prompts[index])
                    .displayFont(22).foregroundStyle(Theme.Palette.text)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: .infinity, minHeight: 64)
                    .transition(.opacity)
                    .id(index)
                HStack(spacing: 6) {
                    ForEach(0..<prompts.count, id: \.self) { n in
                        Circle().fill(n <= index ? Theme.Palette.lav : Theme.Palette.line)
                            .frame(width: 7, height: 7)
                    }
                }
                // Always skippable — a body scan you're stuck inside is not
                // relaxing.
                PrimaryButton(title: isLast ? cta : "Skip ahead") {
                    if let next = nextPromptIndex(index, of: prompts.count) {
                        withAnimation { index = next }
                    } else {
                        onNext()
                    }
                }
            }
        }
        .task(id: index) {
            guard !underUITest, !isLast else { return }
            try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
            guard !Task.isCancelled, let next = nextPromptIndex(index, of: prompts.count) else { return }
            withAnimation { index = next }
        }
        WhyThisWorks(text: why)
    }
}

private struct BreatheStep: View {
    let eyebrow: String
    let preset: BreathingPacer.Preset
    let why: String
    let cta: String
    let onNext: () -> Void

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 12) {
                Text(eyebrow).appFont(11, weight: .heavy).foregroundStyle(Theme.Palette.lavText)
                BreathingPacer(accent: Theme.Accent.breathe, preset: preset)
                PrimaryButton(title: cta) { onNext() }
            }
        }
        WhyThisWorks(text: why)
    }
}

private struct SensoryStep: View {
    let why: String
    let cta: String
    let onNext: () -> Void

    // Copy hand-synced with GroundingView / Android strings.xml ground_step*.
    private let steps: [(String, String)] = [
        ("5 things you can see", "Look around — name five, slowly."),
        ("4 things you can feel", "Textures, temperature, your feet on the floor."),
        ("3 things you can hear", "Near, then far."),
        ("2 things you can smell", "Or two scents you like."),
        ("1 thing you can taste", "Or one slow, full breath."),
    ]
    @State private var index = 0

    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                Text("5 · 4 · 3 · 2 · 1").appFont(11, weight: .heavy).foregroundStyle(Theme.Palette.lavText)
                Text(steps[index].0).displayFont(20).foregroundStyle(Theme.Palette.text)
                Text(steps[index].1).appFont(13).foregroundStyle(Theme.Palette.muted)
                PrimaryButton(title: nextPromptIndex(index, of: steps.count) == nil ? cta : "Next") {
                    if let next = nextPromptIndex(index, of: steps.count) { index = next } else { onNext() }
                }
            }
        }
        WhyThisWorks(text: why)
    }
}

private struct ClosingCard: View {
    let title: String
    let body_: String
    var body: some View {
        Card {
            VStack(spacing: 8) {
                Text(title).displayFont(22).foregroundStyle(Theme.Palette.text)
                Text(body_).appFont(13).foregroundStyle(Theme.Palette.muted)
                    .multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
        }
    }
}

// MARK: - Wind-down ritual

private let windDownScan = [
    "Let your jaw come unclenched.",
    "Drop your shoulders away from your ears.",
    "Open your hands.",
    "Soften your belly.",
    "Let your legs get heavy.",
    "Let the whole body sink into the bed.",
]

struct WindDownRitualView: View {
    @State private var step = 0
    private let total = 4

    var body: some View {
        ScreenScaffold(eyebrow: "Wind down", title: "A ritual for tonight",
                       trailingSystemImage: "moon.stars", accent: Theme.Accent.sleep) {
            if step < total { StepProgress(step: step, total: total) }
            switch step {
            case 0:
                WritingStep(
                    eyebrow: "Step one · Empty the desk",
                    title: "What's still on your mind?",
                    body_: "Anything unfinished, worrying, or just loud. Don't organise it — the point is getting it out of your head, not writing well.",
                    placeholder: "Whatever's there…",
                    journalTitle: "Before bed", journalTag: "wind-down", journalSymbol: "moon",
                    why: "Writing down what's unfinished before bed helps people fall asleep faster than writing about what they've completed (Scullin et al., Journal of Experimental Psychology: General, 2018).",
                    cta: "Continue") { step = 1 }
            case 1:
                ThreeGoodThingsStep(
                    eyebrow: "Step two · Three good things",
                    title: "What went right today?",
                    body_: "Small counts. The coffee, a message, getting through it.",
                    why: "Naming three specific good things each night is one of the most replicated positive-psychology exercises, with measured effects on mood up to six months out (Seligman et al., American Psychologist, 2005).",
                    cta: "Continue", skip: "Skip tonight") { step = 2 }
            case 2:
                PromptReelStep(
                    eyebrow: "Step three · Let go, top to bottom",
                    prompts: windDownScan, seconds: 6,
                    why: "Progressive muscle relaxation is one of the standard relaxation components of CBT-I, the best-evidenced treatment for insomnia (Lancet Digital Health, 2025).",
                    cta: "Continue") { step = 3 }
            case 3:
                BreatheStep(
                    eyebrow: "Step four · Settle",
                    preset: .reset,
                    why: "A longer exhale than inhale is the part of slow breathing with the clearest evidence — it raises vagal tone and slows heart rate. The count matters less than the ratio.",
                    cta: "I'm settled") { step = 4 }
            default:
                ClosingCard(title: "Goodnight.", body_: "Nothing else is needed tonight.")
            }
        }
    }
}

// MARK: - Ritual builder

private let cueIdeas = ["make my morning coffee", "close my laptop", "get off the bus", "brush my teeth"]

// Daytime wording — the wind-down's version ends "sink into the bed", which is
// wrong at 3pm.
private let builderScan = [
    "Let your jaw come unclenched.",
    "Drop your shoulders away from your ears.",
    "Open your hands.",
    "Soften your belly.",
    "Let your feet feel the floor.",
    "Let the chair take your weight.",
]

struct RitualBuilderView: View {
    private enum Phase { case build, run, done }
    @State private var phase: Phase = .build
    @State private var chosen: [String] = RitualStore.blocks()
    @State private var cue: String = RitualStore.cue()
    @State private var saved = false
    @State private var at = 0

    var body: some View {
        switch phase {
        case .run:  runner
        case .done: finished
        case .build: builder
        }
    }

    // MARK: Build
    private var builder: some View {
        ScreenScaffold(eyebrow: "Toolkit", title: "Your ritual", trailingSystemImage: "sparkles") {
            // The cue leads. Reordering blocks is the fun part; deciding when
            // it happens is the part with the evidence behind it.
            Card {
                VStack(alignment: .leading, spacing: 10) {
                    Text("First, the when").appFont(11, weight: .heavy).foregroundStyle(Theme.Palette.lavText)
                    Text("Attach it to something you already do")
                        .displayFont(20).foregroundStyle(Theme.Palette.text)
                    Text("A routine with no cue is a good intention. A routine that rides on something already in your day is a plan.")
                        .appFont(13).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    TextField("After I…", text: $cue)
                        .appFont(13).foregroundStyle(Theme.Palette.soft)
                        .padding(12)
                        .background(Theme.Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .accessibilityLabel("After I")
                        .onChange(of: cue) { _, _ in saved = false }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(cueIdeas, id: \.self) { idea in
                                Button { cue = idea; saved = false } label: { MiniChip(idea) }
                                    .buttonStyle(.pressable)
                            }
                        }
                    }
                    if !cue.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text("After I \(cue.trimmingCharacters(in: .whitespaces)), I'll run this.")
                            .displayFont(17).foregroundStyle(Theme.Palette.text)
                    }
                    // Honest about what we won't do: no reminder is wired to
                    // this, and promising one we don't send would be a fake. It
                    // is also the point — the cue does the reminding.
                    Text("CereBro won't nag you about this. The cue is the reminder — that's how it works.")
                        .appFont(11).foregroundStyle(Theme.Palette.muted2)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            WhyThisWorks(text: "Deciding in advance when and where you'll do something — an 'if-then' plan tied to a moment already in your day — roughly doubles follow-through compared with intention alone, across ~94 studies (Gollwitzer & Sheeran, Advances in Experimental Social Psychology, 2006).")

            SectionTitle(title: "Then, the what")
            Text("Pick a few. Short beats thorough — a five-minute routine you keep is worth more than a twenty-minute one you skip.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            ForEach(ritualBlocks) { block in
                blockRow(block)
            }
            Text(chosen.isEmpty
                 ? "Nothing picked yet."
                 : "\(chosen.count) \(chosen.count == 1 ? "step" : "steps") · about \(ritualMinutes(chosen)) min")
                .appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted2)
            PrimaryButton(title: "Start the ritual", systemImage: "play.fill") {
                at = 0; phase = .run
            }
            .opacity(chosen.isEmpty ? 0.45 : 1)
            .disabled(chosen.isEmpty)
            Button {
                RitualStore.save(chosen, cue: cue); saved = true
            } label: {
                Text(saved ? "Saved" : "Save it")
                    .appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted)
            }
            .buttonStyle(.pressable)
            .disabled(chosen.isEmpty || saved)
            Text("Saved on this device only — it isn't synced to your account or sent to us.")
                .appFont(11).foregroundStyle(Theme.Palette.muted2)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    /// The whole row toggles, not just a switch — a small control beside a
    /// block name is what everyone taps second (found on Android, on device).
    private func blockRow(_ block: RitualBlock) -> some View {
        let position = chosen.firstIndex(of: block.id)
        return Card(padding: 12) {
            HStack(spacing: 12) {
                Image(systemName: position != nil ? "checkmark.circle.fill" : "circle")
                    .appFont(20).foregroundStyle(position != nil ? Theme.Palette.lav : Theme.Palette.muted2)
                VStack(alignment: .leading, spacing: 2) {
                    Text(position != nil ? "\(position! + 1). \(block.label)" : block.label)
                        .appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.text)
                    Text("\(block.note) · \(block.minutes) min")
                        .appFont(11.5).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                if let i = position {
                    VStack(spacing: 2) {
                        Button { move(i, by: -1) } label: {
                            Image(systemName: "chevron.up").appFont(13)
                                .foregroundStyle(i == 0 ? Theme.Palette.line : Theme.Palette.lav)
                        }
                        .buttonStyle(.pressable).disabled(i == 0)
                        .accessibilityLabel("Move \(block.label) earlier")
                        Button { move(i, by: 1) } label: {
                            Image(systemName: "chevron.down").appFont(13)
                                .foregroundStyle(i == chosen.count - 1 ? Theme.Palette.line : Theme.Palette.lav)
                        }
                        .buttonStyle(.pressable).disabled(i == chosen.count - 1)
                        .accessibilityLabel("Move \(block.label) later")
                    }
                }
            }
            .contentShape(Rectangle())
        }
        .onTapGesture { toggle(block.id) }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Include \(block.label)")
        .accessibilityValue(position != nil ? "Step \(position! + 1)" : "Not included")
    }

    private func toggle(_ id: String) {
        saved = false
        if let i = chosen.firstIndex(of: id) { chosen.remove(at: i) } else { chosen.append(id) }
    }

    private func move(_ index: Int, by: Int) {
        let to = index + by
        guard chosen.indices.contains(index), chosen.indices.contains(to) else { return }
        saved = false
        chosen.swapAt(index, to)
    }

    // MARK: Run
    @ViewBuilder private var runner: some View {
        let id = chosen.indices.contains(at) ? chosen[at] : ""
        let block = ritualBlocks.first { $0.id == id }
        let isLast = at == chosen.count - 1
        let cta = isLast ? "Finish" : "Next step"
        ScreenScaffold(eyebrow: "Your ritual", title: block?.label ?? "Your ritual",
                       trailingSystemImage: "sparkles") {
            StepProgress(step: at, total: max(chosen.count, 1))
            // Leaving is one tap at every step — a routine you can't put down
            // half-way is a trap, not a ritual.
            Button { phase = .build } label: {
                Text("Stop here").appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted)
            }
            .buttonStyle(.pressable)
            // Keyed by block: two writing steps in a row would otherwise reuse
            // the same view identity and the second would inherit the first's
            // text — for a brain dump that is a privacy bug, not a cosmetic one.
            stepView(for: id, cta: cta).id(id)
        }
    }

    @ViewBuilder private func stepView(for id: String, cta: String) -> some View {
        switch id {
        case "settle":
            BreatheStep(eyebrow: "Settle the breath", preset: .reset,
                        why: "A longer exhale than inhale is the part of slow breathing with the clearest evidence — it raises vagal tone and slows heart rate. The count matters less than the ratio.",
                        cta: cta, onNext: advance)
        case "box":
            BreatheStep(eyebrow: "Box breathing", preset: .box,
                        why: "Paced breathing is used in clinical distress-tolerance and relaxation protocols. Slowing the breath activates the body's calming response.",
                        cta: cta, onNext: advance)
        case "ground":
            SensoryStep(why: "Sensory grounding redirects attention from spiralling thoughts to the here-and-now — a widely taught anxiety-management skill.",
                        cta: cta, onNext: advance)
        case "scan":
            PromptReelStep(eyebrow: "Let go, top to bottom", prompts: builderScan, seconds: 6,
                           why: "Progressive muscle relaxation is one of the standard relaxation components of CBT-I, the best-evidenced treatment for insomnia (Lancet Digital Health, 2025).",
                           cta: cta, onNext: advance)
        case "dump":
            WritingStep(eyebrow: "Empty the desk", title: "What's still loud?",
                        body_: "Anything unfinished, worrying, or circling. Don't organise it — getting it out is the point.",
                        placeholder: "Whatever's there…",
                        journalTitle: "From my ritual", journalTag: "ritual", journalSymbol: "book",
                        why: "Writing down what's unfinished helps people let go of it — the effect is strongest for what's still open, not what's already done (Scullin et al., Journal of Experimental Psychology: General, 2018).",
                        cta: cta, onNext: advance)
        case "good":
            ThreeGoodThingsStep(eyebrow: "Three good things", title: "What went right?",
                                body_: "Small counts. The coffee, a message, getting through it.",
                                why: "Naming three specific good things is one of the most replicated positive-psychology exercises, with measured effects on mood up to six months out (Seligman et al., American Psychologist, 2005).",
                                cta: cta, skip: "Skip this one", onNext: advance)
        case "intention":
            WritingStep(eyebrow: "One intention", title: "What is this next stretch for?",
                        body_: "One line. Concrete beats noble — 'reply to two emails, then stop' travels further than 'be productive'.",
                        placeholder: "Today I'll…",
                        journalTitle: "An intention", journalTag: "intention", journalSymbol: "sparkles",
                        why: "Naming a specific, concrete intention — what, when, where — is what separates a plan from a wish; vague goals reliably underperform specific ones (Gollwitzer & Sheeran, 2006).",
                        cta: cta, lines: 2...5, onNext: advance)
        default:
            WritingStep(eyebrow: "Write freely", title: "Anything at all",
                        body_: "No prompt, no structure, nobody reading it.",
                        placeholder: "…",
                        journalTitle: "From my ritual", journalTag: "ritual", journalSymbol: "book",
                        why: "Writing about what's on your mind, for a few minutes at a time, has small but repeatedly measured effects on wellbeing — the writing does the work, not the reading back (Pennebaker, Psychological Science, 1997).",
                        cta: cta, lines: 7...16, onNext: advance)
        }
    }

    private func advance() {
        if at < chosen.count - 1 { at += 1 } else { phase = .done }
    }

    // MARK: Done
    // Deliberately quiet: no trophy, no count, no streak (F5 — celebrate
    // notable moments, not every repetition). The one thing worth saying is the
    // cue, because that is what makes the next run happen.
    private var finished: some View {
        ScreenScaffold(eyebrow: "Your ritual", title: "Done", trailingSystemImage: "sparkles") {
            ClosingCard(title: "That's the ritual.",
                        body_: cue.trimmingCharacters(in: .whitespaces).isEmpty
                        ? "Nothing else is needed right now."
                        : "Next time: after you \(cue.trimmingCharacters(in: .whitespaces)).")
            Button { phase = .build } label: {
                Text("Change it").appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted)
            }
            .buttonStyle(.pressable)
        }
    }
}

// MARK: - Guided imagery

private let imageryLines = [
    "Let your shoulders drop. Three slow breaths — out for longer than in.",
    "Bring to mind a place where you feel at ease. Real or imagined: a room, a shore, a path you know.",
    "Look around it. What's the light like? What colours are there?",
    "Listen from where you're standing. What's close by, and what's further off?",
    "Feel the ground holding you up. The air on your skin, and its temperature.",
    "Nothing here needs anything from you. You can put things down for a minute.",
    "When you're ready, let the place stay as it is. You know the way back to it.",
    "Come back to the room. One more slow breath, then open your eyes.",
]
private let imagerySeconds = 15

struct GuidedImageryView: View {
    @State private var started = false
    @State private var done = false
    @State private var index = 0
    @State private var left = imagerySeconds
    @State private var paused = false

    var body: some View {
        ScreenScaffold(eyebrow: "Settle", title: "A place you can go",
                       trailingSystemImage: "moon.stars", accent: Theme.Accent.sleep) {
            if done {
                ClosingCard(title: "The place stays where it is.",
                            body_: "You built it, so you can get back to it — it takes less time each go.")
                Button { index = 0; left = imagerySeconds; paused = false; done = false } label: {
                    Text("Again").appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted)
                }
                .buttonStyle(.pressable)
            } else if !started {
                intro
            } else {
                stage
                controls
            }
        }
    }

    private var intro: some View {
        Group {
            Card {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Two minutes of building somewhere calm in detail — what it looks like, what you can hear from there, how the ground feels. Eight lines, fifteen seconds each. Read one, let your eyes rest, and glance back when the next lands.")
                        .appFont(13).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                    // The caution goes BEFORE the exercise, not after something
                    // goes wrong.
                    VStack(alignment: .leading, spacing: 6) {
                        Text("If it stops feeling calm, stop.")
                            .appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.text)
                        Text("Going looking for a quiet place can sometimes turn up the opposite. That's a normal thing for this exercise to do and not a failure of yours.")
                            .appFont(12.5).foregroundStyle(Theme.Palette.muted)
                            .fixedSize(horizontal: false, vertical: true)
                        NavigationLink { GroundingView() } label: {
                            Text("Try 5-4-3-2-1 grounding instead")
                                .appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.lavText)
                        }
                        .buttonStyle(.pressable)
                    }
                    .padding(14)
                    .background(Theme.Palette.danger.opacity(0.10),
                                in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(Theme.Palette.danger.opacity(0.35)))
                    PrimaryButton(title: "Begin", systemImage: "play.fill") { started = true }
                }
            }
            WhyThisWorks(text: "Guided imagery is a standard relaxation component in stress and anxiety protocols. The detail is what does the work — which is why these prompts ask about sound, light and temperature rather than just telling you to picture somewhere nice.")
        }
    }

    /// The dusk stage — constant-dark in both themes, the rule content art
    /// follows (a warm-white version of "somewhere calm at dusk" is a different
    /// exercise), so its text is a fixed cream rather than a theme token.
    private var stage: some View {
        ZStack {
            LinearGradient(colors: [Color(red: 0.114, green: 0.098, blue: 0.251),
                                    Color(red: 0.043, green: 0.039, blue: 0.110)],
                           startPoint: .top, endPoint: .bottom)
            Text(imageryLines[index])
                .displayFont(23).foregroundStyle(.white.opacity(0.9))
                .multilineTextAlignment(.center)
                .padding(28)
                .transition(.opacity)
                .id(index)
        }
        .frame(minHeight: 280)
        .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous)
            .stroke(.white.opacity(0.08)))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(imageryLines[index])
        // Keyed on `paused` as well as `index`: toggling pause restarts the
        // task with the new value rather than relying on a running closure to
        // observe the change.
        .task(id: "\(index)-\(paused)") {
            guard !underUITest, !paused else { return }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                if Task.isCancelled { return }
                if left > 1 { left -= 1; continue }
                if let next = nextPromptIndex(index, of: imageryLines.count) {
                    left = imagerySeconds
                    withAnimation { index = next }
                } else {
                    done = true
                }
                return
            }
        }
    }

    private var controls: some View {
        Card {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Button { paused.toggle() } label: {
                        Text(paused ? "Resume" : "Pause")
                            .appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.lavText)
                    }.buttonStyle(.pressable)
                    Spacer()
                    Button {
                        if let next = nextPromptIndex(index, of: imageryLines.count) {
                            left = imagerySeconds
                            withAnimation { index = next }
                        } else { done = true }
                    } label: {
                        Text("Skip ahead").appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted)
                    }.buttonStyle(.pressable)
                    Spacer()
                    Text(paused ? "Paused" : "\(left)s")
                        .appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted2)
                }
                // Stopping is one tap at every moment, the same posture as the
                // caution on the way in.
                Button { started = false; index = 0; left = imagerySeconds; paused = false } label: {
                    Text("Stop here").appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted)
                }.buttonStyle(.pressable)
            }
        }
    }
}
