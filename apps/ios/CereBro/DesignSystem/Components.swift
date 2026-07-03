import SwiftUI

// MARK: - Remote photo with gradient placeholder
/// Mirrors the reference's Unsplash imagery. Falls back to a lavender gradient
/// while loading / offline so the app still looks complete without a network.
struct Photo: View {
    let url: String
    var symbol: String = "sparkles"

    var body: some View {
        AsyncImage(url: URL(string: url)) { phase in
            switch phase {
            case .success(let image):
                image.resizable().scaledToFill()
            default:
                ZStack {
                    LinearGradient(
                        colors: [Theme.Palette.lav.opacity(0.55), Theme.Palette.nightTop],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    )
                    Image(systemName: symbol)
                        .appFont(22, weight: .light)
                        .foregroundStyle(.white.opacity(0.55))
                }
            }
        }
        .clipped()
        .accessibilityHidden(true)   // decorative imagery throughout the app
    }
}

// MARK: - Card surface
struct Card<Content: View>: View {
    var padding: CGFloat = 16
    var cornerRadius: CGFloat = Theme.Radius.card
    @ViewBuilder var content: Content

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        content
            .padding(padding)
            .background(shape.fill(.ultraThinMaterial).opacity(0.5))
            .background(shape.fill(Theme.Palette.card))
            .overlay(shape.stroke(Theme.Stroke.bevel(), lineWidth: 1))
            .clipShape(shape)
            .appearOnScroll()
    }
}

// MARK: - Screen scaffold (header + scrolling body over the night background)
struct ScreenScaffold<Content: View>: View {
    let eyebrow: String
    let title: String
    var trailingSystemImage: String? = nil
    var trailingAction: (() -> Void)? = nil
    /// VoiceOver label for the trailing header button (icon-only otherwise).
    var trailingAccessibilityLabel: String? = nil
    /// Section accent tint for the backdrop + title glow.
    var accent: Color = Theme.Palette.lav
    /// Tab-root screens hide the back button.
    var isRoot: Bool = false
    /// Bottom-anchored scrolling (chat transcripts): opens at the newest
    /// content and stays pinned to it as the conversation grows.
    var anchorBottom: Bool = false
    @ViewBuilder var content: Content

    @Environment(\.dismiss) private var dismiss
    @State private var appeared = false

    var body: some View {
        ZStack {
            AppBackground(accent: accent)
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    HStack(alignment: .top, spacing: 10) {
                        if !isRoot {
                            CircleIconButton(systemImage: "chevron.left", accessibilityLabel: "Back") { dismiss() }
                        }
                        VStack(alignment: .leading, spacing: 5) {
                            Text(eyebrow).eyebrow()
                            Text(title)
                                .displayFont(27)
                                .foregroundStyle(Theme.Palette.text)
                                .shadow(color: accent.opacity(0.40), radius: 12, y: 4)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        Spacer()
                        if let img = trailingSystemImage {
                            CircleIconButton(systemImage: img,
                                             accessibilityLabel: trailingAccessibilityLabel,
                                             action: trailingAction ?? {})
                        }
                    }
                    content
                }
                .padding(.horizontal, 18)
                .padding(.top, 8)
                .padding(.bottom, 28)
                .opacity(appeared ? 1 : 0)
                .offset(y: appeared ? 0 : 18)
            }
            .defaultScrollAnchor(anchorBottom ? .bottom : .top)
        }
        .toolbar(.hidden, for: .navigationBar)
        .onAppear {
            withAnimation(.spring(response: 0.55, dampingFraction: 0.85)) { appeared = true }
        }
    }
}

struct CircleIconButton: View {
    let systemImage: String
    var accessibilityLabel: String? = nil
    var action: () -> Void = {}
    var body: some View {
        Button(action: action) {
            Image(systemName: systemImage)
                .appFont(16, weight: .semibold)
                .foregroundStyle(Theme.Palette.soft)
                .frame(width: 38, height: 38)
                .background(Theme.Stroke.iconWell, in: Circle())
                .frame(width: 44, height: 44)        // 44pt minimum tap target (visual stays 38)
                .contentShape(Circle())
        }
        .buttonStyle(.pressable)
        .accessibilityLabel(accessibilityLabel ?? "")
    }
}

// MARK: - Hero banner (image background + tag + headline + CTA)
struct HeroCard: View {
    let tag: String
    let title: String
    let subtitle: String
    let cta: String
    let imageURL: String
    var action: () -> Void = {}
    @State private var zoom = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Photo(url: imageURL, symbol: "moon.stars")
                .frame(height: 218)
                .frame(maxWidth: .infinity)
                .scaleEffect(zoom ? 1.08 : 1.0)          // slow Ken Burns drift
                .animation(.easeInOut(duration: 11).repeatForever(autoreverses: true), value: zoom)
                .overlay(Theme.Gradient.imageScrim)
                .overlay(                                  // glossy diagonal sheen
                    LinearGradient(
                        colors: [.white.opacity(0.16), .clear],
                        startPoint: .topLeading, endPoint: .center
                    )
                )
                .onAppear { if !reduceMotion { zoom = true } }
            VStack(alignment: .leading, spacing: 6) {
                Tag(tag)
                Spacer(minLength: 30)
                Text(title)
                    .displayFont(26)
                    .foregroundStyle(Theme.Palette.text)
                Text(subtitle)
                    .appFont(13)
                    .foregroundStyle(Theme.Palette.muted)
                Button(action: action) {
                    Label(cta, systemImage: "play.fill")
                        .appFont(12.5, weight: .heavy)
                        .foregroundStyle(Theme.Palette.ink)
                        .padding(.horizontal, 12).frame(minHeight: 36)
                        .background(Theme.Palette.cream, in: RoundedRectangle(cornerRadius: Theme.Radius.chip, style: .continuous))
                }
                .buttonStyle(.pressable)
                .padding(.top, 4)
            }
            .padding(18)
        }
        .frame(height: 218)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.hero, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Radius.hero, style: .continuous)
                .stroke(Theme.Stroke.hairline, lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.35), radius: 18, y: 10)
        .appearOnScroll()
    }
}

struct Tag: View {
    let text: String
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text)
            .appFont(9, weight: .heavy)
            .tracking(1.2)
            .textCase(.uppercase)
            .foregroundStyle(Theme.Palette.text)
            .padding(.horizontal, 9).frame(minHeight: 25)
            .background(Color.white.opacity(0.16), in: Capsule())
            .overlay(Capsule().stroke(Color.white.opacity(0.16)))
    }
}

// MARK: - List row visual (photo + icon + title/subtitle + chevron)
struct RowLabel: View {
    let title: String
    let subtitle: String
    var systemImage: String = "sparkles"
    /// Accepted for call-site compatibility but no longer rendered: photo
    /// thumbnails duplicated the symbol well, and 17 stock photos stretched
    /// over ~100 rows meant constant wrong pairings (a portrait on "Urgent
    /// support", laptop hands on "Privacy"). Photos belong to content heroes
    /// and rails, not utility rows.
    var imageURL: String? = nil
    var emphasis: Bool = false

    var body: some View {
        HStack(spacing: 9) {
            Image(systemName: systemImage)
                .appFont(16, weight: .semibold)
                .foregroundStyle(Theme.Palette.soft)
                .frame(width: 32, height: 32)
                .background(Theme.Stroke.iconWell, in: RoundedRectangle(cornerRadius: 11, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(title).appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                Text(subtitle).appFont(12).foregroundStyle(Theme.Palette.muted).lineLimit(1)
            }
            Spacer()
            Image(systemName: "chevron.right").appFont(13, weight: .semibold).foregroundStyle(Theme.Stroke.chevron)
        }
        .padding(9)
        .background(RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous).fill(.ultraThinMaterial).opacity(0.5))
        .background(emphasis ? Theme.Palette.cardEmphasis : Theme.Palette.card,
                    in: RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous)
                .stroke(Theme.Stroke.bevel(emphasis: emphasis), lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous))
        .appearOnScroll()
    }
}

/// Tappable row that runs a closure.
struct ListRow: View {
    let title: String
    let subtitle: String
    var systemImage: String = "sparkles"
    var imageURL: String? = nil
    var emphasis: Bool = false
    var action: () -> Void = {}

    var body: some View {
        Button(action: action) {
            RowLabel(title: title, subtitle: subtitle, systemImage: systemImage, imageURL: imageURL, emphasis: emphasis)
        }
        .buttonStyle(.pressable)
    }
}

/// Row that pushes a destination via NavigationLink.
struct NavRow<Destination: View>: View {
    let title: String
    let subtitle: String
    var systemImage: String = "sparkles"
    var imageURL: String? = nil
    var emphasis: Bool = false
    @ViewBuilder var destination: Destination

    var body: some View {
        NavigationLink {
            destination
        } label: {
            RowLabel(title: title, subtitle: subtitle, systemImage: systemImage, imageURL: imageURL, emphasis: emphasis)
        }
        .buttonStyle(.pressable)
    }
}

// MARK: - Section header
struct SectionTitle: View {
    let title: String
    var trailing: String? = "See all"
    var body: some View {
        HStack {
            Text(title).displayFont(18).foregroundStyle(Theme.Palette.text)
            Spacer()
            if let trailing { Text(trailing).appFont(12, weight: .semibold).foregroundStyle(Theme.Palette.muted2) }
        }
    }
}

// MARK: - Buttons
struct PrimaryButton: View {
    let title: String
    var systemImage: String = "checkmark.circle.fill"
    var action: () -> Void = {}
    @State private var taps = 0
    var body: some View {
        Button { taps += 1; action() } label: {
            Label(title, systemImage: systemImage)
                .appFont(14, weight: .heavy)
                .foregroundStyle(Theme.Palette.ink)
                .frame(maxWidth: .infinity).frame(minHeight: 52)
                .background(Theme.Gradient.primaryButton,
                            in: RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous)
                    .stroke(.white.opacity(0.7), lineWidth: 0.5))
                .shadow(color: Theme.Palette.soft.opacity(0.28), radius: 14, y: 6)
        }
        .buttonStyle(.pressable)
        .sensoryFeedback(.impact(weight: .medium), trigger: taps)
    }
}

struct SecondaryButton: View {
    let title: String
    var systemImage: String = "house"
    var action: () -> Void = {}
    @State private var taps = 0
    var body: some View {
        Button { taps += 1; action() } label: {
            Label(title, systemImage: systemImage)
                .appFont(14, weight: .heavy)
                .foregroundStyle(Theme.Palette.text)
                .frame(maxWidth: .infinity).frame(minHeight: 52)
                .background(RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous).fill(.ultraThinMaterial).opacity(0.6))
                .background(Theme.Palette.card, in: RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Theme.Radius.row, style: .continuous).stroke(Color.white.opacity(0.18)))
        }
        .buttonStyle(.pressable)
        .sensoryFeedback(.selection, trigger: taps)
    }
}

// MARK: - Chips (selectable pills)
struct ChipRow: View {
    let options: [String]
    @Binding var selection: Set<String>
    /// When true, tapping selects exactly one option (e.g. a date range).
    var singleSelect: Bool = false
    var body: some View {
        FlowLayout(spacing: 8) {
            ForEach(options, id: \.self) { opt in
                let on = selection.contains(opt)
                Button {
                    if singleSelect {
                        selection = [opt]
                    } else if on {
                        selection.remove(opt)
                    } else {
                        selection.insert(opt)
                    }
                } label: {
                    Text(opt)
                        .appFont(12, weight: .heavy)
                        .foregroundStyle(on ? Theme.Palette.ink : Theme.Palette.muted)
                        .padding(.horizontal, 12).frame(minHeight: 36)
                        .background(on ? Theme.Palette.cream : Theme.Palette.card)
                        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.chip, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.chip, style: .continuous).stroke(on ? .clear : Theme.Palette.line))
                        .scaleEffect(on ? 1.04 : 1)
                        .animation(.spring(response: 0.3, dampingFraction: 0.6), value: on)
                }
                .buttonStyle(.pressable)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .sensoryFeedback(.selection, trigger: selection)
    }
}

// MARK: - Toggle row group (the `.settings` block)
struct ToggleRow: View {
    let title: String
    let subtitle: String
    @Binding var isOn: Bool
    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).appFont(13.5, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                Text(subtitle).appFont(11.5).foregroundStyle(Theme.Palette.muted2)
            }
            Spacer()
            Toggle("", isOn: $isOn).labelsHidden().tint(Theme.Palette.lav)
        }
        .sensoryFeedback(.selection, trigger: isOn)
        .padding(12)
    }
}

// MARK: - Metric bar (baseline / insights)
/// Counts (numeric values) render an animated fill bar; qualitative values
/// ("Improving", "Steady"…) render a trend chip instead, since a bar implies a
/// quantity that a word doesn't have. The fill animates up on appear, staggered
/// by `index` so a group of metrics cascades in.
struct MetricBar: View {
    let label: String
    let value: String
    let progress: Double
    var color: Color = Theme.Palette.lav
    var index: Int = 0

    @State private var fill: CGFloat = 0

    /// Numeric value → quantity bar; otherwise a qualitative trend.
    private var isQuantitative: Bool { Double(value) != nil }

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack {
                Text(label).appFont(13).foregroundStyle(Theme.Palette.soft)
                Spacer()
                if isQuantitative {
                    Text(value).appFont(13, weight: .medium).foregroundStyle(Theme.Palette.muted2)
                } else {
                    TrendChip(value: value, progress: progress, color: color)
                }
            }
            if isQuantitative {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Theme.Stroke.iconWell)
                        Capsule()
                            .fill(Theme.Gradient.accentBar(color))
                            .frame(width: geo.size.width * fill)
                    }
                }
                .frame(height: 8)
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.7, dampingFraction: 0.85).delay(Double(index) * 0.09)) {
                fill = progress
            }
        }
    }
}

/// Trend pill for qualitative metric values (direction inferred from `progress`).
struct TrendChip: View {
    let value: String
    let progress: Double
    var color: Color = Theme.Palette.lav

    private var symbol: String {
        if progress >= 0.6 { return "arrow.up.right" }
        if progress >= 0.45 { return "arrow.right" }
        return "arrow.down.right"
    }
    private var tint: Color {
        if progress >= 0.6 { return color }
        if progress >= 0.45 { return Theme.Palette.muted }
        return Theme.Palette.danger
    }

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: symbol).appFont(10, weight: .bold)
            Text(value).appFont(12, weight: .heavy)
        }
        .foregroundStyle(tint)
        .padding(.horizontal, 10).frame(height: 26)
        .background(tint.opacity(0.14), in: Capsule())
        .overlay(Capsule().stroke(tint.opacity(0.28)))
    }
}

// MARK: - Insight callout
struct InsightCard: View {
    let label: String
    let title: String
    var detail: String? = nil
    var body: some View {
        Card {
            VStack(alignment: .leading, spacing: 4) {
                Text(label).appFont(12).foregroundStyle(Theme.Palette.muted)
                Text(title).appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                if let detail { Text(detail).appFont(12).foregroundStyle(Theme.Palette.muted).fixedSize(horizontal: false, vertical: true) }
            }
        }
    }
}

// MARK: - Danger / safety panel
struct DangerPanel<Content: View>: View {
    @ViewBuilder var content: Content
    var body: some View {
        content
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Theme.Palette.danger.opacity(0.10))
            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.panel, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Theme.Radius.panel, style: .continuous).stroke(Theme.Palette.danger.opacity(0.24)))
    }
}

// MARK: - Animated audio wave
struct WaveView: View {
    @State private var animate = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    let bars: [CGFloat] = [14,25,18,36,22,32,18,28,14,30,18]
    var body: some View {
        HStack(spacing: 4) {
            ForEach(Array(bars.enumerated()), id: \.offset) { i, h in
                Capsule()
                    .fill(LinearGradient(colors: [Theme.Palette.soft, Theme.Palette.lav], startPoint: .top, endPoint: .bottom))
                    .frame(width: 5, height: (animate || reduceMotion) ? h : h * 0.4)   // brand soft→periwinkle
                    .animation(.easeInOut(duration: 0.5).repeatForever().delay(Double(i) * 0.06), value: animate)
            }
        }
        .frame(height: 46).frame(maxWidth: .infinity)
        .background(Theme.Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.control, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.control, style: .continuous).stroke(Theme.Palette.line))
        .onAppear { if !reduceMotion { animate = true } }   // bars rest at full height
    }
}

// MARK: - Simple flow layout for chips
struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > maxWidth { x = 0; y += rowHeight + spacing; rowHeight = 0 }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: maxWidth == .infinity ? x : maxWidth, height: y + rowHeight)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for sub in subviews {
            let size = sub.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX { x = bounds.minX; y += rowHeight + spacing; rowHeight = 0 }
            sub.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

// MARK: - Tactile press style (spring scale + dim)
struct PressableButtonStyle: ButtonStyle {
    var scale: CGFloat = 0.96
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .opacity(configuration.isPressed ? 0.92 : 1)
            .animation(.spring(response: 0.32, dampingFraction: 0.62), value: configuration.isPressed)
            .onChange(of: configuration.isPressed) { _, pressed in
                if pressed { Haptics.soft(intensity: 0.5) }   // app-wide tactile press
            }
    }
}

extension ButtonStyle where Self == PressableButtonStyle {
    /// Drop-in replacement for `.plain` that adds a springy press response.
    static var pressable: PressableButtonStyle { .init() }
}

// MARK: - Glowing orb (welcome / voice / loading)
/// Layered, softly pulsing lavender orb with an outer bloom and inner specular
/// highlight. Used as the signature brand element across hero screens.
struct GlowOrb: View {
    var size: CGFloat = 92
    var pulses: Bool = true
    @State private var anim = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            // Outer bloom
            Circle()
                .fill(Theme.orb)
                .frame(width: size, height: size)
                .blur(radius: size * 0.34)
                .opacity(0.8)
                .scaleEffect(anim ? 1.22 : 0.9)
            // Core
            Circle()
                .fill(Theme.orb)
                .frame(width: size, height: size)
            // Inner specular highlight
            Circle()
                .fill(RadialGradient(colors: [.white.opacity(0.95), .clear],
                                     center: .init(x: 0.36, y: 0.3),
                                     startRadius: 0, endRadius: size * 0.42))
                .frame(width: size, height: size)
        }
        .shadow(color: Theme.Palette.soft.opacity(0.5), radius: 30)
        .animation(pulses ? .easeInOut(duration: 2.6).repeatForever(autoreverses: true) : .default, value: anim)
        .onAppear { if pulses && !reduceMotion { anim = true } }
    }
}

// MARK: - Shimmer (loading skeletons)
struct Shimmer: ViewModifier {
    @State private var phase: CGFloat = -1
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    func body(content: Content) -> some View {
        content.overlay(
            GeometryReader { geo in
                LinearGradient(colors: [.clear, .white.opacity(0.35), .clear],
                               startPoint: .leading, endPoint: .trailing)
                    .frame(width: geo.size.width * 0.7)
                    .offset(x: phase * geo.size.width * 1.7)
            }
            .mask(content)
            .allowsHitTesting(false)
        )
        .onAppear {
            guard !reduceMotion else { return }   // skip the sweeping highlight
            withAnimation(.linear(duration: 1.4).repeatForever(autoreverses: false)) { phase = 1 }
        }
    }
}

// MARK: - Tool banner (purposeful detail-screen header image)
/// Replaces the plain decorative photo strips on tool/detail screens. The image
/// carries an icon badge + a one-line intent caption over a scrim, so it states
/// what the screen is *for* instead of being filler. Subtle Ken Burns drift.
struct ToolBanner: View {
    let imageURL: String
    var symbol: String = "sparkles"
    let caption: String
    var accent: Color = Theme.Palette.lav
    var height: CGFloat = 124
    @State private var zoom = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            Photo(url: imageURL, symbol: symbol)
                .frame(height: height).frame(maxWidth: .infinity)
                .scaleEffect(zoom ? 1.07 : 1.0)
                .animation(.easeInOut(duration: 12).repeatForever(autoreverses: true), value: zoom)
                .overlay(
                    LinearGradient(colors: [Theme.Stroke.scrimTop, .black.opacity(0.7)],
                                   startPoint: .top, endPoint: .bottom)
                )
                .onAppear { if !reduceMotion { zoom = true } }
            HStack(spacing: 9) {
                Image(systemName: symbol)
                    .appFont(15, weight: .semibold).foregroundStyle(.white)
                    .frame(width: 32, height: 32)
                    .background(accent.opacity(0.5), in: Circle())
                    .overlay(Circle().stroke(.white.opacity(0.3)))
                Text(caption)
                    .appFont(13, weight: .semibold).foregroundStyle(Theme.Palette.text)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(14)
        }
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.banner, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Theme.Radius.banner, style: .continuous).stroke(Theme.Stroke.hairline))
        .shadow(color: .black.opacity(0.28), radius: 12, y: 6)
        .appearOnScroll()
    }
}

// MARK: - Celebration (completion reward)
/// A brief, calm reward: a soft particle bloom + a checkmark that springs in and
/// fades, plus a success haptic. Bump `trigger` to fire it.
struct Celebration: ViewModifier {
    @Binding var trigger: Bool
    var accent: Color = Theme.Palette.lav

    @State private var burst = false
    @State private var show = false

    func body(content: Content) -> some View {
        content
            .overlay {
                if show {
                    ZStack {
                        ForEach(0..<12, id: \.self) { i in
                            Circle()
                                .fill(accent.opacity(0.9))
                                .frame(width: 8, height: 8)
                                .offset(particleOffset(i))
                                .opacity(burst ? 0 : 1)
                        }
                        Circle()
                            .fill(.ultraThinMaterial)
                            .frame(width: 88, height: 88)
                            .overlay(Circle().stroke(accent.opacity(0.5), lineWidth: 1))
                            .overlay(
                                Image(systemName: "checkmark")
                                    .appFont(34, weight: .bold)
                                    .foregroundStyle(Theme.Palette.text)
                            )
                            .scaleEffect(burst ? 1 : 0.4)
                            .opacity(burst ? 1 : 0)
                            .shadow(color: accent.opacity(0.5), radius: 24)
                    }
                    .allowsHitTesting(false)
                    .transition(.opacity)
                }
            }
            .sensoryFeedback(.success, trigger: trigger)
            .onChange(of: trigger) { _, _ in fire() }
    }

    private func particleOffset(_ i: Int) -> CGSize {
        let angle = Double(i) / 12 * 2 * .pi
        let r: CGFloat = burst ? 88 : 0
        return CGSize(width: cos(angle) * r, height: sin(angle) * r)
    }

    private func fire() {
        show = true
        burst = false
        withAnimation(.spring(response: 0.5, dampingFraction: 0.6)) { burst = true }
        withAnimation(.easeOut(duration: 0.45).delay(0.75)) { show = false }
    }
}

extension View {
    /// Plays a calm completion reward (particles + checkmark + haptic) when `trigger` flips.
    func celebration(trigger: Binding<Bool>, accent: Color = Theme.Palette.lav) -> some View {
        modifier(Celebration(trigger: trigger, accent: accent))
    }

    func shimmer() -> some View { modifier(Shimmer()) }

    /// Marks this view as the source for an iOS 18 zoom navigation transition
    /// (no-op on iOS 17).
    @ViewBuilder
    func matchedZoomSource(id: some Hashable, in ns: Namespace.ID) -> some View {
        if #available(iOS 18.0, *) {
            self.matchedTransitionSource(id: id, in: ns)
        } else {
            self
        }
    }

    /// Scroll-driven appear: items gently scale/fade/blur as they enter & leave.
    func appearOnScroll() -> some View {
        // Opacity + scale only: a per-frame Gaussian blur on every row during
        // scrolling was the most expensive effect on long lists. Dropping it
        // keeps the same gentle enter/leave feel at a fraction of the GPU cost.
        scrollTransition { content, phase in
            content
                .opacity(phase.isIdentity ? 1 : 0.35)
                .scaleEffect(phase.isIdentity ? 1 : 0.93)
        }
    }

    /// One-shot entrance: fades + lifts the view in on appear, with an optional
    /// `index` so a stack of elements cascades. Honors Reduce Motion.
    func entrance(_ index: Int = 0, y: CGFloat = 16) -> some View {
        modifier(Entrance(index: index, y: y))
    }
}

// MARK: - Staggered entrance reveal
struct Entrance: ViewModifier {
    var index: Int = 0
    var y: CGFloat = 16
    @State private var shown = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    func body(content: Content) -> some View {
        content
            .opacity(shown || reduceMotion ? 1 : 0)
            .offset(y: shown || reduceMotion ? 0 : y)
            .onAppear {
                guard !reduceMotion else { return }
                withAnimation(.spring(response: 0.55, dampingFraction: 0.82)
                    .delay(Double(index) * 0.07)) { shown = true }
            }
    }
}
