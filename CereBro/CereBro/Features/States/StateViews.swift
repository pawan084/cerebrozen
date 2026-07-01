import SwiftUI

// MARK: - Offline mode
struct OfflineView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Offline support state", title: "Offline Mode", trailingSystemImage: "wifi.slash") {
            DangerPanel {
                VStack(alignment: .leading, spacing: 4) {
                    Text("You're offline").appFont(14, weight: .bold).foregroundStyle(Theme.Palette.danger)
                    Text("Available offline: saved breathing, local journal drafts, downloaded sounds.")
                        .appFont(12).foregroundStyle(Theme.Palette.muted)
                }
            }
            ForEach(Dummy.offline) { item in
                ListRow(title: item.title, subtitle: item.subtitle, systemImage: item.symbol, imageURL: item.imageURL)
            }
        }
    }
}

// MARK: - Empty journal
struct EmptyJournalView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "First-time empty state", title: "Empty Journal", trailingSystemImage: "book") {
            VStack(spacing: 12) {
                Image(systemName: "book.closed")
                    .appFont(40, weight: .light).foregroundStyle(Theme.Palette.muted)
                    .padding(.top, 30)
                Text("Start with one sentence.").displayFont(22).foregroundStyle(Theme.Palette.text)
                Text("CereBro can help you reflect without pressure.")
                    .appFont(13).foregroundStyle(Theme.Palette.muted).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.bottom, 10)
            NavRow(title: "Write your first entry", subtitle: "Private writing with consent", systemImage: "square.and.pencil", imageURL: Dummy.Img.write, emphasis: true) { JournalEntryView() }
        }
    }
}

// MARK: - Voice loading
struct VoiceLoadingView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Loading state", title: "Voice Loading", trailingSystemImage: "hourglass") {
            GlowOrb(size: 100)
                .frame(maxWidth: .infinity).padding(.vertical, 16)
            ForEach(0..<3, id: \.self) { i in
                RoundedRectangle(cornerRadius: 999)
                    .fill(Theme.Palette.field)
                    .frame(height: 18)
                    .frame(maxWidth: i == 2 ? 200 : .infinity, alignment: .leading)
                    .shimmer()
            }
            Text("Connecting to your voice companion…")
                .appFont(12).foregroundStyle(Theme.Palette.muted2)
        }
    }
}

// MARK: - Voice error
struct VoiceErrorView: View {
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        ScreenScaffold(eyebrow: "Network fallback", title: "Voice Error", trailingSystemImage: "exclamationmark.triangle") {
            DangerPanel {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Couldn't reach the voice service").appFont(14, weight: .bold).foregroundStyle(Theme.Palette.danger)
                    Text("Check your connection. You can switch to text chat in the meantime.")
                        .appFont(12).foregroundStyle(Theme.Palette.muted)
                }
            }
            PrimaryButton(title: "Try again", systemImage: "arrow.clockwise") { Haptics.tap(.light); dismiss() }
            NavRow(title: "Switch to chat", subtitle: "Text fallback", systemImage: "bubble.left", imageURL: Dummy.Img.chat) { ChatView() }
        }
    }
}
