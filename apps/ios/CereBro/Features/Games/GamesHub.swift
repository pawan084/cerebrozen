import SwiftUI

// MARK: - Toolkit
/// One hub for every small steadying tool (IOS_PARITY #3, Android ToolkitScreen
/// parity): sections Ground · Breathe · Reframe · Settle, ending with the
/// crisis door — tools first, honest pathways when tools aren't enough. The
/// old Games+Tools split (and the gamecontroller framing) is gone.
struct ToolkitView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Small ways to steady", title: "Toolkit",
                       trailingSystemImage: "wind") {
            Text("Little tools with real provenance — pick whatever the moment needs. Nothing to win, nothing to keep up.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)

            SectionTitle(title: "Ground", trailing: nil)
            NavRow(title: "5-4-3-2-1 grounding", subtitle: "Anchor through the senses",
                   systemImage: "checkmark.shield", imageURL: Dummy.Img.ground) { GroundingView() }
            NavRow(title: "Zen ripples", subtitle: "Tap a still pool and watch it spread",
                   systemImage: "drop", imageURL: Dummy.Img.ocean) { ZenRipplesGame() }
            NavRow(title: "Bubble pop", subtitle: "Pop drifting bubbles at your own pace",
                   systemImage: "circle.circle", imageURL: Dummy.Img.calm) { BubblePopGame() }

            SectionTitle(title: "Breathe", trailing: nil)
            NavRow(title: "Box breathing", subtitle: "4 · 4 · 4 · 4 — steady the body",
                   systemImage: "wind", imageURL: Dummy.Img.breath, emphasis: true) { BreathingView(preset: .box) }
            NavRow(title: "Two-minute reset", subtitle: "In for four, out for six — no holds",
                   systemImage: "leaf", imageURL: Dummy.Img.breath) { BreathingView(preset: .reset) }
            NavRow(title: "Color breathing", subtitle: "A soft glow with a long exhale",
                   systemImage: "lungs", imageURL: Dummy.Img.breath) { BreathingView(preset: .color) }

            SectionTitle(title: "Reframe", trailing: nil)
            NavRow(title: "CBT reframe", subtitle: "A kinder look at a worried thought",
                   systemImage: "brain", imageURL: Dummy.Img.journal) { CBTReframeView() }
            NavRow(title: "TIPP reset", subtitle: "For overwhelming moments (DBT)",
                   systemImage: "bolt.heart", imageURL: Dummy.Img.support) { DBTSkillView() }

            SectionTitle(title: "Settle", trailing: nil)
            NavRow(title: "Gratitude garden", subtitle: "Plant one small joy at a time",
                   systemImage: "leaf.fill", imageURL: Dummy.Img.calm) { GratitudeGardenGame() }
            NavRow(title: "Pattern glow", subtitle: "Follow the light, one step at a time",
                   systemImage: "circle.hexagongrid.fill", imageURL: Dummy.Img.meditate) { PatternGlowGame() }
            NavRow(title: "Sounds for sleep", subtitle: "Stories and soundscapes on the Sleep tab",
                   systemImage: "moon.zzz", imageURL: Dummy.Img.sleep) { PlayerView(item: Dummy.sleepContent[0]) }

            // Crisis stays ≤2 taps from every tool surface (REDESIGN §2.3).
            NavRow(title: "Need support right now?", subtitle: "Tele-MANAS 14416 · real people, 24/7",
                   systemImage: "phone.fill", imageURL: Dummy.Img.support) { CrisisView() }
        }
    }
}

// MARK: - Bubble pop
struct BubblePopGame: View {
    fileprivate struct Bubble: Identifiable { let id = UUID(); let x: CGFloat; let size: CGFloat; let tint: Color; let life: Double }
    @State private var bubbles: [Bubble] = []
    @State private var popped = 0
    private let palette: [Color] = [Theme.Palette.lav, Theme.Accent.breathe, Theme.Accent.calm, Theme.Palette.cream]
    private let spawn = Timer.publish(every: 0.65, on: .main, in: .common).autoconnect()

    var body: some View {
        ScreenScaffold(eyebrow: "Pop at your own pace", title: "Bubble pop", trailingSystemImage: "circle.circle") {
            Text("\(popped) popped").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.muted)
            GeometryReader { geo in
                ZStack {
                    ForEach(bubbles) { b in
                        BubbleView(bubble: b, fieldHeight: geo.size.height,
                                   onPop: { pop(b) }, onExpire: { remove(b) })
                            .position(x: b.x * geo.size.width, y: 0)   // x fixed; BubbleView drives y
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .contentShape(Rectangle())
            }
            .frame(height: 460)
            .background(Theme.Palette.card.opacity(0.4))
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Theme.Palette.line))
        }
        .onReceive(spawn) { _ in
            guard bubbles.count < 14 else { return }
            bubbles.append(.init(x: CGFloat.random(in: 0.1...0.9),
                                 size: CGFloat.random(in: 44...78),
                                 tint: palette.randomElement()!,
                                 life: Double.random(in: 4.5...7)))
        }
    }

    private func pop(_ b: Bubble) { Haptics.soft(intensity: 0.6); popped += 1; remove(b) }
    private func remove(_ b: Bubble) { bubbles.removeAll { $0.id == b.id } }
}

private struct BubbleView: View {
    let bubble: BubblePopGame.Bubble
    let fieldHeight: CGFloat
    var onPop: () -> Void
    var onExpire: () -> Void
    @State private var y: CGFloat = 0
    @State private var gone = false

    var body: some View {
        Circle()
            .fill(bubble.tint.opacity(0.28))
            .overlay(Circle().stroke(bubble.tint.opacity(0.7), lineWidth: 1.5))
            .overlay(Circle().fill(.white.opacity(0.25)).frame(width: bubble.size * 0.22).offset(x: -bubble.size * 0.18, y: -bubble.size * 0.18))
            .frame(width: bubble.size, height: bubble.size)
            .scaleEffect(gone ? 1.4 : 1).opacity(gone ? 0 : 1)
            .offset(y: y)
            .onAppear {
                y = fieldHeight + bubble.size           // start below the field
                withAnimation(.linear(duration: bubble.life)) { y = -bubble.size }
                DispatchQueue.main.asyncAfter(deadline: .now() + bubble.life) { onExpire() }
            }
            .onTapGesture {
                guard !gone else { return }
                withAnimation(.easeOut(duration: 0.18)) { gone = true }
                onPop()
            }
            .accessibilityLabel("Bubble")
            .accessibilityAddTraits(.isButton)
    }
}

// MARK: - Zen ripples
struct ZenRipplesGame: View {
    fileprivate struct Ripple: Identifiable { let id = UUID(); let pos: CGPoint; let tint: Color }
    @State private var ripples: [Ripple] = []
    private let palette: [Color] = [Theme.Palette.lav, Theme.Accent.calm, Theme.Accent.breathe, Theme.Palette.cream]

    var body: some View {
        ScreenScaffold(eyebrow: "Tap the still water", title: "Zen ripples", trailingSystemImage: "drop") {
            Text("Tap anywhere on the pool. Each touch ripples out and fades.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            GeometryReader { _ in
                ZStack {
                    LinearGradient(colors: [Theme.Palette.night, Theme.Accent.sleep.opacity(0.3)],
                                   startPoint: .top, endPoint: .bottom)
                    ForEach(ripples) { r in RippleView(ripple: r) { remove(r) } }
                }
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0).onEnded { v in
                    ripples.append(.init(pos: v.location, tint: palette.randomElement()!))
                    Haptics.soft(intensity: 0.4)
                })
                .accessibilityLabel("Ripple pool")
                .accessibilityAddTraits(.isButton)
            }
            .frame(height: 480)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Theme.Palette.line))
        }
    }

    private func remove(_ r: Ripple) { ripples.removeAll { $0.id == r.id } }
}

private struct RippleView: View {
    let ripple: ZenRipplesGame.Ripple
    var onDone: () -> Void
    @State private var scale: CGFloat = 0.1
    @State private var opacity: Double = 0.7

    var body: some View {
        Circle().stroke(ripple.tint, lineWidth: 2)
            .frame(width: 120, height: 120)
            .scaleEffect(scale).opacity(opacity)
            .position(ripple.pos)
            .onAppear {
                withAnimation(.easeOut(duration: 1.4)) { scale = 2.6; opacity = 0 }
                DispatchQueue.main.asyncAfter(deadline: .now() + 1.4) { onDone() }
            }
    }
}

// MARK: - Pattern glow (follow-the-light memory)
struct PatternGlowGame: View {
    private enum Mode { case idle, showing, input, done }
    @State private var sequence: [Int] = []
    @State private var inputIndex = 0
    @State private var mode: Mode = .idle
    @State private var litPad: Int?
    @State private var best = 0
    private let pads: [Color] = [Theme.Accent.breathe, Theme.Palette.lav, Theme.Accent.calm, Theme.Palette.cream]
    private let grid = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    var body: some View {
        ScreenScaffold(eyebrow: "Follow the light", title: "Pattern glow", trailingSystemImage: "circle.hexagongrid.fill") {
            HStack {
                Text(statusText).appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.muted)
                Spacer()
                Text("Best \(best)").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.muted2)
            }
            LazyVGrid(columns: grid, spacing: 14) {
                ForEach(0..<4, id: \.self) { i in
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(pads[i].opacity(litPad == i ? 0.95 : 0.28))
                        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(pads[i].opacity(0.7), lineWidth: 1.5))
                        .frame(height: 120)
                        .scaleEffect(litPad == i ? 1.04 : 1)
                        .animation(.easeOut(duration: 0.12), value: litPad)
                        .contentShape(RoundedRectangle(cornerRadius: 20))
                        .onTapGesture { tap(i) }
                        .accessibilityLabel("Pad \(i + 1)")
                        .accessibilityAddTraits(.isButton)
                }
            }
            PrimaryButton(title: mode == .idle ? "Start" : "Play again",
                          systemImage: "play.fill") { startGame() }
                .opacity(mode == .input || mode == .showing ? 0.4 : 1)
                .disabled(mode == .input || mode == .showing)
        }
    }

    private var statusText: String {
        switch mode {
        case .idle:    return "Tap start"
        case .showing: return "Watch…"
        case .input:   return "Your turn · round \(sequence.count)"
        case .done:    return "Reached round \(sequence.count)"
        }
    }

    private func startGame() { sequence = []; best = max(best, 0); addStep() }

    private func addStep() {
        sequence.append(Int.random(in: 0..<4))
        mode = .showing
        showStep(0)
    }

    private func showStep(_ i: Int) {
        guard i < sequence.count else { mode = .input; inputIndex = 0; return }
        litPad = sequence[i]
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.45) {
            litPad = nil
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { showStep(i + 1) }
        }
    }

    private func tap(_ pad: Int) {
        guard mode == .input else { return }
        Haptics.selection()
        flash(pad)
        if pad == sequence[inputIndex] {
            inputIndex += 1
            if inputIndex == sequence.count {
                best = max(best, sequence.count)
                mode = .showing
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) { addStep() }
            }
        } else {
            mode = .done
            Haptics.warning()
        }
    }

    private func flash(_ pad: Int) {
        litPad = pad
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { if litPad == pad { litPad = nil } }
    }
}

// MARK: - Gratitude garden
struct GratitudeGardenGame: View {
    fileprivate struct Sprout: Identifiable { let id = UUID(); let x: CGFloat; let y: CGFloat; let symbol: String; let tint: Color }
    @State private var sprouts: [Sprout] = []
    private let symbols = ["leaf.fill", "camera.macro", "tree.fill", "ladybug.fill", "drop.fill"]
    private let tints: [Color] = [Theme.Accent.calm, Theme.Accent.breathe, Theme.Palette.lav, Theme.Palette.cream]

    var body: some View {
        ScreenScaffold(eyebrow: "Plant a little joy", title: "Gratitude garden", trailingSystemImage: "leaf") {
            Text("Tap the soil to plant one thing you're grateful for — watch your garden slowly fill.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            WhyThisWorks(text: "Noting what you're grateful for is a studied positive-psychology practice linked to improved mood over time.")
            Text("\(sprouts.count) planted").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.muted)
            GeometryReader { geo in
                ZStack {
                    LinearGradient(colors: [Theme.Accent.sleep.opacity(0.2), Theme.Accent.calm.opacity(0.28)],
                                   startPoint: .top, endPoint: .bottom)
                    ForEach(sprouts) { s in
                        Image(systemName: s.symbol).appFont(22, weight: .semibold).foregroundStyle(s.tint)
                            .position(x: s.x * geo.size.width, y: s.y * geo.size.height)
                            .transition(.scale.combined(with: .opacity))
                            .accessibilityLabel("Sprout")
                    }
                }
                .contentShape(Rectangle())
                .onTapGesture {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.6)) {
                        sprouts.append(.init(x: .random(in: 0.1...0.9), y: .random(in: 0.2...0.9),
                                             symbol: symbols.randomElement()!, tint: tints.randomElement()!))
                    }
                    Haptics.soft(intensity: 0.4)
                }
                .accessibilityLabel("Garden soil, tap to plant")
                .accessibilityAddTraits(.isButton)
            }
            .frame(height: 420)
            .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).stroke(Theme.Palette.line))
            Button { withAnimation { sprouts.removeAll() } } label: {
                Text("Clear garden").appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.muted)
            }.buttonStyle(.pressable)
        }
    }
}
