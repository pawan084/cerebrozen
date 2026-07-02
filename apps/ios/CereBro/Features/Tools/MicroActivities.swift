import SwiftUI

// MARK: - One good thing (gratitude micro-activity)
/// A 30-second gratitude prompt. When connected, the note mirrors to the journal
/// tagged "Gratitude" so it shows up in history; otherwise it's a calm local moment.
struct OneGoodThingView: View {
    @EnvironmentObject var backend: BackendService
    @Environment(\.dismiss) private var dismiss
    @State private var text = ""
    @State private var done = false

    var body: some View {
        ScreenScaffold(eyebrow: "A small shift in attention", title: "One good thing", trailingSystemImage: "sparkles") {
            Text("Name one small thing that went right today — however tiny. Noticing it is the whole exercise.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            Card(cornerRadius: 18) {
                TextField("Today, I'm glad that…", text: $text, axis: .vertical)
                    .foregroundStyle(Theme.Palette.text)
                    .lineLimit(3...6)
            }
            PrimaryButton(title: "Save this moment", systemImage: "checkmark") {
                let entry = JournalEntry(title: "One good thing", tags: ["Gratitude"],
                                         date: "Today", symbol: "sparkles", imageURL: Dummy.Img.calm)
                backend.mirrorJournal(entry, body: text)
                Haptics.success()
                done.toggle()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { dismiss() }
            }
            .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .toolAmbience(.rain)
        .celebration(trigger: $done)
    }
}

// MARK: - Set an intention
/// Pick or write a single, kind intention to carry forward.
struct IntentionSetView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var intention = ""
    @State private var done = false

    private let suggestions = ["Be patient with myself", "One thing at a time",
                               "Pause before reacting", "Ask for help if I need it",
                               "Rest without guilt"]

    var body: some View {
        ScreenScaffold(eyebrow: "Choose your direction", title: "Set an intention", trailingSystemImage: "target") {
            Text("An intention isn't a to-do. It's the tone you want to move through the day with.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            Card(cornerRadius: 18) {
                TextField("My intention is to…", text: $intention)
                    .foregroundStyle(Theme.Palette.text)
            }
            Text("Or start from one of these").eyebrow()
            FlowChips(options: suggestions) { intention = $0; Haptics.selection() }
            PrimaryButton(title: "Set intention", systemImage: "checkmark") {
                Haptics.success(); done.toggle()
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) { dismiss() }
            }
            .disabled(intention.trimmingCharacters(in: .whitespaces).isEmpty)
        }
        .toolAmbience(.drone)
        .celebration(trigger: $done)
    }
}

// MARK: - DBT skill (TIPP / opposite action) for intense moments
struct DBTSkillView: View {
    private struct Skill: Identifiable { let id = UUID(); let letter: String; let title: String; let body: String; let symbol: String }
    private let tipp: [Skill] = [
        .init(letter: "T", title: "Temperature", body: "Hold something cold — cup cold water on your face or hold ice. It quickly calms a racing body.", symbol: "snowflake"),
        .init(letter: "I", title: "Intense movement", body: "Burn the surge: 60 seconds of jumping jacks, fast walking, or stairs.", symbol: "figure.run"),
        .init(letter: "P", title: "Paced breathing", body: "Make your exhale longer than your inhale — in for 4, out for 6 — for a minute.", symbol: "wind"),
        .init(letter: "P", title: "Paired muscle relaxation", body: "Tense a muscle group as you breathe in, release fully as you breathe out.", symbol: "figure.mind.and.body"),
    ]

    var body: some View {
        ScreenScaffold(eyebrow: "For overwhelming moments", title: "TIPP reset", trailingSystemImage: "bolt.heart") {
            Text("When an emotion or urge feels too big, TIPP works with your body to bring the intensity down fast. Try one — you don't need all four.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            ForEach(tipp) { s in
                Card(cornerRadius: 18) {
                    HStack(alignment: .top, spacing: 12) {
                        Text(s.letter)
                            .appFont(18, weight: .heavy).foregroundStyle(Theme.Palette.ink)
                            .frame(width: 38, height: 38)
                            .background(Theme.Palette.cream, in: Circle())
                        VStack(alignment: .leading, spacing: 4) {
                            Label(s.title, systemImage: s.symbol)
                                .appFont(14.5, weight: .semibold).foregroundStyle(Theme.Palette.text)
                            Text(s.body).appFont(12.5).foregroundStyle(Theme.Palette.soft)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                }
            }
            DangerPanel {
                Text("If you're thinking about harming yourself, you deserve support now — reach a crisis line or emergency services.")
                    .appFont(12).foregroundStyle(Theme.Palette.muted)
            }
            NavRow(title: "Get urgent support", subtitle: "Crisis resources", systemImage: "lifepreserver", imageURL: Dummy.Img.support, emphasis: true) { CrisisView() }
        }
        .toolAmbience(.ocean)
    }
}

// MARK: - Simple wrapping chip row (tap to pick a suggestion)
private struct FlowChips: View {
    let options: [String]
    var onPick: (String) -> Void
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(options, id: \.self) { opt in
                    Button { onPick(opt) } label: {
                        Text(opt).appFont(12.5, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                            .padding(.horizontal, 13).frame(height: 36)
                            .background(Theme.Palette.card).clipShape(Capsule())
                            .overlay(Capsule().stroke(Theme.Palette.line))
                    }.buttonStyle(.pressable)
                }
            }.padding(.horizontal, 1)
        }
    }
}
