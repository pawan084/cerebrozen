import SwiftUI

/// First-run guided tour (ref GUIDED TOUR OVERLAY): four gentle stops over
/// Home, shown once per install. Skipped under `-resetState` so UITests stay
/// deterministic (same contract as the catalogue/splash gates).
struct GuidedTourOverlay: View {
    @Binding var isPresented: Bool
    @State private var idx = 0

    static let stops: [(label: String, caption: String)] = [
        ("Check in daily",
         "One tap tells CereBro how you're arriving — plans, insights and starters all personalize from it."),
        ("Your plan adapts",
         "Three small steps a day, rebuilt from your check-ins and sleep diary. Tap the hero any time."),
        ("Talk it through",
         "A voice companion that listens first. It's AI — never a therapist, and always honest about that."),
        ("Private by default",
         "Nothing is remembered without your say-so. Change anything under You → Privacy & memory."),
    ]

    private var stop: (label: String, caption: String) { Self.stops[idx] }
    private var isLast: Bool { idx == Self.stops.count - 1 }

    private func finish() {
        UserDefaults.standard.set(true, forKey: "guidedTourDone")
        withAnimation(.easeOut(duration: 0.25)) { isPresented = false }
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            Theme.Palette.ink.opacity(0.82).ignoresSafeArea()
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 8) {
                Text("GUIDED TOUR · \(idx + 1) OF \(Self.stops.count)")
                    .appFont(10.5, weight: .bold).kerning(1.2)
                    .foregroundStyle(Theme.Brand.cyan)
                Text(stop.label)
                    .appFont(17, weight: .bold).foregroundStyle(Theme.Palette.text)
                Text(stop.caption)
                    .appFont(13).foregroundStyle(Theme.Palette.muted)
                    .fixedSize(horizontal: false, vertical: true)
                HStack {
                    HStack(spacing: 6) {
                        ForEach(Self.stops.indices, id: \.self) { i in
                            Circle()
                                .fill(i == idx ? Theme.Palette.lav : Theme.Palette.line)
                                .frame(width: 7, height: 7)
                        }
                    }
                    Spacer()
                    Button("Skip") { finish() }
                        .appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.muted)
                    Button(isLast ? "Let's begin" : "Next") {
                        if isLast { finish() } else { withAnimation { idx += 1 } }
                    }
                    .appFont(13, weight: .bold).foregroundStyle(Theme.Palette.lav)
                    .padding(.leading, 14)
                }
                .padding(.top, 4)
            }
            .padding(20)
            .background(
                RoundedRectangle(cornerRadius: 22)
                    .fill(Theme.Brand.ink)
                    .overlay(RoundedRectangle(cornerRadius: 22).fill(Theme.Palette.cardEmphasis))
            )
            .overlay(RoundedRectangle(cornerRadius: 22).stroke(Theme.Palette.line, lineWidth: 1))
            .padding(16)
        }
        .transition(.opacity)
    }
}
