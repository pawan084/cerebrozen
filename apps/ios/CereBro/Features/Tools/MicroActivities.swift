import SwiftUI

// The standalone "One good thing" / "Set an intention" micro-views were
// absorbed into Journal quick prompts (IOS_PARITY #4, Android IA precedent) —
// their widget kinds now route to JournalEntryView(prompt:) in ChatActivities.

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
            WhyThisWorks(text: "TIPP comes from dialectical behaviour therapy (DBT) — skills clinicians teach for riding out intense moments.")
            DangerPanel {
                Text("If you're thinking about harming yourself, you deserve support now — reach a crisis line or emergency services.")
                    .appFont(12).foregroundStyle(Theme.Palette.muted)
            }
            NavRow(title: "Get urgent support", subtitle: "Tele-MANAS 14416 · real people, 24/7", systemImage: "lifepreserver", imageURL: Dummy.Img.support, emphasis: true) { CrisisView() }
        }
        .toolAmbience(.ocean)
    }
}

