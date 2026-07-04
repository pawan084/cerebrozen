import SwiftUI

// MARK: - Companion style (persona picker)
/// How CereBro talks with you. Removed from onboarding to keep the 90-second
/// flow lean; lives here instead. Local-first, synced to the server profile
/// so AI replies can match the chosen voice.
struct CompanionStyleView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService

    private static let styles: [(name: String, detail: String, symbol: String)] = [
        ("Calm Guide", "Steady and soothing — grounds you first, never rushes", "leaf"),
        ("Warm Friend", "Encouraging and familiar — like a friend who gets it", "heart"),
        ("Straight Talker", "Clear and direct — kind, but skips the padding", "text.alignleft"),
        ("Quiet Coach", "Action-first — one small concrete step at a time", "figure.walk"),
    ]

    var body: some View {
        ScreenScaffold(eyebrow: "How CereBro talks with you", title: "Companion style") {
            Text("Same care, different voice. Change it any time — conversations adapt from the next message.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)

            ForEach(Self.styles, id: \.name) { style in
                ListRow(title: style.name,
                        subtitle: style.detail,
                        systemImage: style.symbol,
                        emphasis: state.companion == style.name) {
                    state.companion = style.name
                    backend.syncCompanion(style.name)
                    Haptics.selection()
                }
                .accessibilityAddTraits(state.companion == style.name ? .isSelected : [])
            }
        }
    }
}
