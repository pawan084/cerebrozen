import SwiftUI

// MARK: - Sleep home (tab root)
struct SleepHomeView: View {
    @EnvironmentObject var state: AppState

    /// Favorited stories/sounds, drawn from the full sleep catalogue.
    private var favoriteItems: [ContentItem] {
        (Dummy.sleepContent + Dummy.meditations).filter { state.isSleepFavorite($0.title) }
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Premium sleep hub", title: "Sleep", trailingSystemImage: "moon.stars",
                       accent: Theme.Accent.sleep, isRoot: true) {
            HeroCard(tag: "Tonight", title: "Rain over quiet hills",
                     subtitle: "An 18-minute sleep story to slow a racing mind.",
                     cta: "Play", imageURL: Dummy.Img.sleep)
            if !favoriteItems.isEmpty {
                SectionTitle(title: "Favorites", trailing: nil)
                ForEach(favoriteItems) { SleepRow(item: $0) }
            }
            SectionTitle(title: "Sleep stories & sounds")
            ForEach(Dummy.sleepContent) { SleepRow(item: $0) }
            NavRow(title: "Meditation library", subtitle: "Mindfulness content", systemImage: "figure.mind.and.body", imageURL: Dummy.Img.meditate) { MeditationLibraryView() }
            InsightCard(label: "Auto-stop timer", title: "Sleep-safe playback fades out after 30 min.")
        }
    }
}

/// A sleep-catalogue row: tap to play, plus a heart to favorite (independent taps).
struct SleepRow: View {
    @EnvironmentObject var state: AppState
    let item: ContentItem
    private var faved: Bool { state.isSleepFavorite(item.title) }
    var body: some View {
        HStack(spacing: 8) {
            NavRow(title: item.title, subtitle: item.subtitle, systemImage: item.symbol, imageURL: item.imageURL) {
                PlayerView(item: item)
            }
            Button {
                state.toggleSleepFavorite(item.title); Haptics.selection()
            } label: {
                Image(systemName: faved ? "heart.fill" : "heart")
                    .appFont(15, weight: .semibold)
                    .foregroundStyle(faved ? Theme.Palette.lav : Theme.Palette.muted2)
                    .frame(width: 44, height: 44)
                    .background(Theme.Stroke.iconWell, in: Circle())
                    .contentShape(Circle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(faved ? "Remove \(item.title) from favorites" : "Add \(item.title) to favorites")
        }
    }
}

// MARK: - Meditation library
struct MeditationLibraryView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Mindfulness content", title: "Meditation Library",
                       trailingSystemImage: "figure.mind.and.body", accent: Theme.Accent.sleep) {
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(Dummy.meditations) { m in
                    NavigationLink { PlayerView(item: m) } label: { MeditationCard(item: m) }.buttonStyle(.pressable)
                }
            }
        }
    }
}

struct MeditationCard: View {
    let item: ContentItem
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Photo(url: item.imageURL, symbol: item.symbol)
                .frame(height: 92).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            Text(item.title).displayFont(15).foregroundStyle(Theme.Palette.soft).padding(.top, 8).lineLimit(1)
            Text(item.subtitle).appFont(11.5).foregroundStyle(Theme.Palette.muted).padding(.top, 2).lineLimit(1)
        }
        .padding(10)
        .background(Theme.Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.Palette.line))
    }
}

// MARK: - Audio player (flagship now-playing screen)
struct PlayerView: View {
    let item: ContentItem
    /// Optional shared namespace for a zoom transition from the source card.
    var zoomNamespace: Namespace.ID? = nil

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var audio = SoundscapePlayer()
    @State private var elapsed: Double = 0
    @State private var artZoom = false

    private let totalSeconds = 1080   // 18:00
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    /// Real playback position drives the scrubber.
    private var progress: Double { totalSeconds > 0 ? min(1, elapsed / Double(totalSeconds)) : 0 }

    var body: some View {
        ZStack {
            // Ambient backdrop: a heavily blurred, oversized copy of the artwork.
            // Clipped to a full-screen Color.clear so the image's own sizing can't
            // inflate the layout.
            Color.clear
                .overlay(
                    Photo(url: item.imageURL, symbol: item.symbol)
                        .scaleEffect(1.5)
                        .blur(radius: 70)
                )
                .overlay(Theme.Palette.night.opacity(0.55))
                .overlay(
                    LinearGradient(colors: [.clear, Theme.Palette.night],
                                   startPoint: .center, endPoint: .bottom)
                )
                .clipped()
                .ignoresSafeArea()

            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    header

                    artwork
                        .padding(.top, 6)

                    Text(item.subtitle)
                        .appFont(13).foregroundStyle(Theme.Palette.muted)

                    scrubber.padding(.top, 4)

                    transport.padding(.top, 2)

                    PlayerVisualizer(playing: audio.isPlaying).padding(.top, 6)

                    InsightCard(label: "Auto-stop timer",
                                title: audio.sleepTimerMinutes.map { "Fades out and stops in \($0) min — tap the timer to change." }
                                    ?? "Tap the timer icon to fade out after 15–60 min.")
                        .padding(.top, 4)
                }
                .padding(.horizontal, 18)
                .padding(.top, 8)
                .padding(.bottom, 28)
            }
        }
        .toolbar(.hidden, for: .navigationBar)
        .modifier(ZoomTransitionIfAvailable(id: item.id, namespace: zoomNamespace))
        .onAppear { audio.configure(for: item); audio.play() }
        .onDisappear { audio.stop() }
        .onReceive(tick) { _ in
            guard audio.isPlaying else { return }
            elapsed = elapsed + 1 >= Double(totalSeconds) ? 0 : elapsed + 1   // loop the position
        }
    }

    // Header: back + eyebrow/title + sleep timer
    private var header: some View {
        HStack(alignment: .top, spacing: 10) {
            CircleIconButton(systemImage: "chevron.left", accessibilityLabel: "Back") { dismiss() }
            VStack(alignment: .leading, spacing: 5) {
                Text("Now playing").eyebrow()
                Text(item.title)
                    .displayFont(27).foregroundStyle(Theme.Palette.text)
                    .shadow(color: Theme.Accent.sleep.opacity(0.45), radius: 12, y: 4)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            CircleIconButton(systemImage: audio.sleepTimerMinutes == nil ? "timer" : "timer.circle.fill",
                             accessibilityLabel: audio.sleepTimerMinutes.map { "Sleep timer, \($0) minutes" } ?? "Sleep timer off") {
                audio.cycleSleepTimer()
            }
        }
    }

    // Artwork with glow + a soft reflection beneath, slowly breathing.
    private var artwork: some View {
        let shape = RoundedRectangle(cornerRadius: 30, style: .continuous)
        return HStack {
            Spacer(minLength: 0)
            VStack(spacing: 6) {
                Photo(url: item.imageURL, symbol: item.symbol)
                    .frame(width: 250, height: 250)
                    .clipShape(shape)
                    .overlay(shape.stroke(Theme.Stroke.hairline))
                    .scaleEffect(artZoom ? 1.05 : 1.0)
                    .shadow(color: Theme.Accent.sleep.opacity(0.55), radius: 38, y: 14)
                    .animation(.easeInOut(duration: 11).repeatForever(autoreverses: true), value: artZoom)

                // Reflection
                Photo(url: item.imageURL, symbol: item.symbol)
                    .frame(width: 250, height: 250)
                    .clipShape(shape)
                    .scaleEffect(x: 1, y: -1)
                    .frame(width: 250, height: 56, alignment: .top)
                    .clipped()
                    .opacity(0.22)
                    .blur(radius: 3)
                    .mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .bottom))
            }
            Spacer(minLength: 0)
        }
        .onAppear { if !reduceMotion { artZoom = true } }
    }

    // Draggable scrubber with a handle + scrub haptic.
    private var scrubber: some View {
        VStack(spacing: 6) {
            GeometryReader { geo in
                let w = geo.size.width
                ZStack(alignment: .leading) {
                    Capsule().fill(Theme.Palette.cardEmphasis).frame(height: 6)
                    Capsule()
                        .fill(LinearGradient(colors: [Theme.Palette.soft, Theme.Accent.sleep],
                                             startPoint: .leading, endPoint: .trailing))
                        .frame(width: max(0, w * progress), height: 6)
                    Circle().fill(.white)
                        .frame(width: 16, height: 16)
                        .shadow(color: Theme.Accent.sleep.opacity(0.6), radius: 6)
                        .offset(x: max(0, min(w - 16, w * progress - 8)))
                }
                .frame(height: 16)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            elapsed = Double(min(1, max(0, v.location.x / w))) * Double(totalSeconds)
                        }
                )
            }
            .frame(height: 16)
            .sensoryFeedback(.selection, trigger: Int(progress * 20))

            HStack {
                Text(timeString(Int(progress * Double(totalSeconds))))
                    .appFont(11).foregroundStyle(Theme.Palette.muted2)
                Spacer()
                Text(timeString(totalSeconds))
                    .appFont(11).foregroundStyle(Theme.Palette.muted2)
            }
        }
    }

    private var transport: some View {
        HStack(spacing: 18) {
            PlayerControl(symbol: "backward.fill", size: 48)
            Button { audio.toggle() } label: {
                Image(systemName: audio.isPlaying ? "pause.fill" : "play.fill")
                    .appFont(24, weight: .bold).foregroundStyle(Theme.Palette.ink)
                    .frame(width: 68, height: 68)
                    .background(Theme.Gradient.primaryButton, in: Circle())
                    .shadow(color: Theme.Accent.sleep.opacity(0.4), radius: 16, y: 6)
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(audio.isPlaying ? "Pause" : "Play")
            .sensoryFeedback(.impact(weight: .medium), trigger: audio.isPlaying)
            PlayerControl(symbol: "forward.fill", size: 48)
        }
        .frame(maxWidth: .infinity)
    }

    private func timeString(_ s: Int) -> String {
        String(format: "%d:%02d", s / 60, s % 60)
    }
}

/// Applies the iOS 18 zoom navigation transition when a source namespace is
/// supplied; a no-op (default push) on iOS 17 or when launched without a source.
private struct ZoomTransitionIfAvailable: ViewModifier {
    let id: AnyHashable
    let namespace: Namespace.ID?

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *), let namespace {
            content.navigationTransition(.zoom(sourceID: id, in: namespace))
        } else {
            content
        }
    }
}

/// Slim equalizer that animates only while playing.
struct PlayerVisualizer: View {
    let playing: Bool
    private let bars: [CGFloat] = [16,28,20,38,24,34,20,30,16,32,22,36,18,26]
    @State private var animate = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(bars.enumerated()), id: \.offset) { i, h in
                Capsule()
                    .fill(LinearGradient(colors: [Theme.Palette.soft, Theme.Accent.sleep],
                                         startPoint: .top, endPoint: .bottom))
                    .frame(width: 4, height: (playing && (animate || reduceMotion)) ? h : 6)
                    .animation(playing ? .easeInOut(duration: 0.5).repeatForever().delay(Double(i) * 0.05)
                                       : .easeOut(duration: 0.3), value: animate)
                    .animation(.easeInOut(duration: 0.3), value: playing)
            }
        }
        .frame(height: 42).frame(maxWidth: .infinity)
        .onAppear { if !reduceMotion { animate = true } }   // static bars when reduced
    }
}

struct PlayerControl: View {
    let symbol: String
    let size: CGFloat
    var body: some View {
        Image(systemName: symbol)
            .appFont(18, weight: .semibold).foregroundStyle(Theme.Palette.soft)
            .frame(width: size, height: size).background(Theme.Stroke.iconWell, in: Circle())
    }
}
