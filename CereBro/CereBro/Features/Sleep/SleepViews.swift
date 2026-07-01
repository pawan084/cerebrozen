import SwiftUI

// MARK: - Sleep home (tab root)
struct SleepHomeView: View {
    @EnvironmentObject var state: AppState
    @State private var playFeatured = false

    /// The featured "Tonight" story (first sleep item) — powers the hero CTA.
    private var featured: ContentItem { Dummy.sleepContent[0] }
    /// Favorited stories/sounds, drawn from the full sleep catalogue.
    private var favoriteItems: [ContentItem] {
        (Dummy.sleepContent + Dummy.meditations).filter { state.isSleepFavorite($0.title) }
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Premium sleep hub", title: "Sleep", trailingSystemImage: "moon.stars",
                       accent: Theme.Accent.sleep, isRoot: true) {
            HeroCard(tag: "Tonight", title: featured.title,
                     subtitle: "A calming sleep story to slow a racing mind.",
                     cta: "Play", imageURL: Dummy.Img.sleep) { playFeatured = true }
            if !favoriteItems.isEmpty {
                SectionTitle(title: "Favorites", trailing: nil)
                ForEach(favoriteItems) { SleepRow(item: $0) }
            }
            SectionTitle(title: "Sleep stories & sounds")
            ForEach(Dummy.sleepContent) { SleepRow(item: $0) }
            NavRow(title: "Meditation library", subtitle: "Mindfulness content", systemImage: "figure.mind.and.body", imageURL: Dummy.Img.meditate) { MeditationLibraryView() }
            InsightCard(label: "Auto-stop timer", title: "Sleep-safe playback fades out when you set the timer.")
        }
        .navigationDestination(isPresented: $playFeatured) { PlayerView(item: featured) }
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

    @EnvironmentObject private var state: AppState
    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var audio = SoundscapePlayer()
    /// Real seconds played (counts up; soundscapes are continuous, not a track
    /// with a fixed length — so there's no fake "progress toward the end").
    @State private var elapsed: Int = 0
    @State private var artZoom = false

    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()
    private var faved: Bool { state.isSleepFavorite(item.title) }

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

                    elapsedBar.padding(.top, 4)

                    transport.padding(.top, 2)

                    volumeSlider.padding(.top, 6)

                    mixSection.padding(.top, 10)

                    PlayerVisualizer(playing: audio.isPlaying).padding(.top, 6)

                    InsightCard(label: "Auto-stop timer",
                                title: audio.sleepRemainingText.map { "Fades out and stops in \($0) — tap the timer to change." }
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
            if audio.isPlaying { elapsed += 1 }   // real elapsed, counts up
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

    // Honest "now playing" strip: elapsed counts up, and a soft ambient rail
    // that gently pulses while playing (continuous audio has no end to scrub to).
    private var elapsedBar: some View {
        VStack(spacing: 8) {
            Capsule()
                .fill(LinearGradient(colors: [Theme.Palette.soft, Theme.Accent.sleep],
                                     startPoint: .leading, endPoint: .trailing))
                .frame(height: 4)
                .opacity(audio.isPlaying ? (artZoom ? 0.85 : 0.45) : 0.25)
                .animation(.easeInOut(duration: 3).repeatForever(autoreverses: true), value: artZoom)

            HStack {
                Label(timeString(elapsed), systemImage: "waveform")
                    .appFont(11, weight: .semibold).foregroundStyle(Theme.Palette.muted)
                Spacer()
                Text(audio.sleepRemainingText.map { "Fades out in \($0)" } ?? "Continuous ambient")
                    .appFont(11).foregroundStyle(Theme.Palette.muted2)
                    .monospacedDigit()
                    .contentTransition(.numericText())
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(audio.isPlaying ? "Playing" : "Paused") \(timeString(elapsed))")
    }

    private var transport: some View {
        HStack(spacing: 18) {
            // Favorite this soundscape (a real action, replacing the dead skip).
            Button {
                state.toggleSleepFavorite(item.title); Haptics.selection()
            } label: {
                Image(systemName: faved ? "heart.fill" : "heart")
                    .appFont(18, weight: .semibold)
                    .foregroundStyle(faved ? Theme.Palette.lav : Theme.Palette.soft)
                    .frame(width: 48, height: 48).background(Theme.Stroke.iconWell, in: Circle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(faved ? "Remove from favorites" : "Add to favorites")

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

            // Cycle the sleep timer (off → 15 → 30 → 45 → 60 → off).
            Button { audio.cycleSleepTimer() } label: {
                Image(systemName: audio.sleepTimerMinutes == nil ? "timer" : "timer.circle.fill")
                    .appFont(18, weight: .semibold)
                    .foregroundStyle(audio.sleepTimerMinutes == nil ? Theme.Palette.soft : Theme.Palette.lav)
                    .frame(width: 48, height: 48).background(Theme.Stroke.iconWell, in: Circle())
            }
            .buttonStyle(.pressable)
            .accessibilityLabel(audio.sleepTimerMinutes.map { "Sleep timer, \($0) minutes" } ?? "Set sleep timer")
        }
        .frame(maxWidth: .infinity)
    }

    // Blend ambient layers (rain + ocean + wind + drone), each with its own level.
    private var mixSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Mix").appFont(11, weight: .heavy).foregroundStyle(Theme.Palette.muted2)
                .frame(maxWidth: .infinity, alignment: .leading)
            ForEach(audio.layers) { layer in
                HStack(spacing: 12) {
                    Button { audio.toggleLayer(layer.id) } label: {
                        Image(systemName: layer.symbol)
                            .appFont(14, weight: .semibold)
                            .foregroundStyle(layer.volume > 0.02 ? Theme.Palette.lav : Theme.Palette.muted2)
                            .frame(width: 40, height: 40)
                            .background(Theme.Stroke.iconWell, in: Circle())
                    }
                    .buttonStyle(.pressable)
                    .accessibilityLabel("\(layer.name), \(layer.volume > 0.02 ? "on" : "off")")
                    Text(layer.name).appFont(12.5, weight: .semibold)
                        .foregroundStyle(Theme.Palette.soft).frame(width: 52, alignment: .leading)
                    Slider(value: Binding(get: { Double(layer.volume) },
                                          set: { audio.setLayerVolume(Float($0), at: layer.id) }), in: 0...1)
                        .tint(Theme.Accent.sleep)
                        .accessibilityLabel("\(layer.name) level")
                }
            }
        }
        .padding(14)
        .background(Theme.Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Theme.Palette.line))
    }

    // Master volume with speaker icons.
    private var volumeSlider: some View {
        HStack(spacing: 12) {
            Image(systemName: "speaker.fill").appFont(12).foregroundStyle(Theme.Palette.muted2)
            Slider(value: Binding(get: { Double(audio.volume) },
                                  set: { audio.volume = Float($0) }), in: 0...1)
                .tint(Theme.Accent.sleep)
                .accessibilityLabel("Volume")
            Image(systemName: "speaker.wave.3.fill").appFont(12).foregroundStyle(Theme.Palette.muted2)
        }
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

