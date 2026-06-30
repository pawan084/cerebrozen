import SwiftUI

// MARK: - Games hub
/// A small hub of calming, low-stakes games — playful resets with no fail state,
/// no timers pressuring you, no scores to chase. Designed to extend: add a
/// `GameEntry` and a destination and it appears here.
struct GamesHubView: View {
    private struct GameEntry: Identifiable {
        let id = UUID(); let title: String; let blurb: String; let symbol: String; let accent: Color
        let destination: AnyView
    }

    private var games: [GameEntry] {
        [
            .init(title: "Bubble pop", blurb: "Pop drifting bubbles at your own pace.",
                  symbol: "circle.circle", accent: Theme.Accent.breathe, destination: AnyView(BubblePopGame())),
            .init(title: "Bubble wrap", blurb: "Endless, satisfying little pops.",
                  symbol: "circle.grid.3x3.fill", accent: Theme.Palette.lav, destination: AnyView(BubbleWrapGame())),
            .init(title: "Color breathing", blurb: "Breathe with a soft, shifting glow.",
                  symbol: "lungs", accent: Theme.Accent.calm, destination: AnyView(ColorBreathingGame())),
            .init(title: "Zen ripples", blurb: "Tap a still pool and watch it spread.",
                  symbol: "drop", accent: Theme.Accent.sleep, destination: AnyView(ZenRipplesGame())),
            .init(title: "Memory match", blurb: "Gentle pairs — no clock, no pressure.",
                  symbol: "square.grid.2x2", accent: Theme.Accent.warm, destination: AnyView(MemoryMatchGame())),
            .init(title: "Pattern glow", blurb: "Follow the light, one step at a time.",
                  symbol: "circle.hexagongrid.fill", accent: Theme.Palette.lav, destination: AnyView(PatternGlowGame())),
            .init(title: "Sliding puzzle", blurb: "Slide the tiles into quiet order.",
                  symbol: "square.grid.3x3.fill", accent: Theme.Accent.breathe, destination: AnyView(SlidingPuzzleGame())),
            .init(title: "Gratitude garden", blurb: "Plant one small joy at a time.",
                  symbol: "leaf", accent: Theme.Accent.calm, destination: AnyView(GratitudeGardenGame())),
        ]
    }

    private let cols = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        ScreenScaffold(eyebrow: "Playful resets", title: "Calm games", trailingSystemImage: "gamecontroller") {
            Text("Quick, gentle games for a busy mind. Nothing to win — just somewhere soft to put your attention.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            LazyVGrid(columns: cols, spacing: 12) {
                ForEach(Array(games.enumerated()), id: \.element.id) { i, g in
                    NavigationLink { g.destination } label: { card(g) }
                        .buttonStyle(.pressable)
                        .entrance(i)
                }
            }
        }
    }

    private func card(_ g: GameEntry) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: g.symbol)
                .appFont(20, weight: .semibold).foregroundStyle(g.accent)
                .frame(width: 46, height: 46)
                .background(g.accent.opacity(0.16), in: RoundedRectangle(cornerRadius: 13, style: .continuous))
            Text(g.title).appFont(15, weight: .semibold).foregroundStyle(Theme.Palette.text)
            Text(g.blurb).appFont(11.5).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, minHeight: 132, alignment: .leading)
        .padding(13)
        .background(Theme.Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).stroke(Theme.Palette.line))
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
    }
}

// MARK: - Color breathing
struct ColorBreathingGame: View {
    private enum Phase: String { case inhale = "Breathe in", hold = "Hold", exhale = "Breathe out"
        var seconds: Double { switch self { case .inhale: return 4; case .hold: return 2; case .exhale: return 6 } }
        var next: Phase { switch self { case .inhale: return .hold; case .hold: return .exhale; case .exhale: return .inhale } }
        var tint: Color { switch self { case .inhale: return Theme.Accent.breathe; case .hold: return Theme.Palette.lav; case .exhale: return Theme.Accent.calm } }
    }
    @State private var phase: Phase = .inhale
    @State private var scale: CGFloat = 0.6
    @State private var breaths = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ScreenScaffold(eyebrow: "Follow the glow", title: "Color breathing", trailingSystemImage: "lungs") {
            Text("\(breaths) calm breaths").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.muted)
            ZStack {
                Circle().fill(phase.tint.opacity(0.18)).frame(width: 300, height: 300).blur(radius: 30)
                Circle()
                    .fill(RadialGradient(colors: [phase.tint.opacity(0.9), phase.tint.opacity(0.2)],
                                         center: .center, startRadius: 4, endRadius: 150))
                    .frame(width: 220, height: 220)
                    .scaleEffect(scale)
                Text(phase.rawValue).appFont(17, weight: .heavy).foregroundStyle(.white)
            }
            .frame(maxWidth: .infinity, minHeight: 360)
            .animation(.easeInOut(duration: 0.4), value: phase)
            .onAppear { run() }
        }
    }

    private func run() {
        let target: CGFloat = phase == .inhale ? 1.0 : (phase == .exhale ? 0.6 : scale)
        withAnimation(reduceMotion ? .none : .easeInOut(duration: phase.seconds)) { scale = target }
        DispatchQueue.main.asyncAfter(deadline: .now() + phase.seconds) {
            if phase == .exhale { breaths += 1; Haptics.soft(intensity: 0.4) }
            phase = phase.next
            run()
        }
    }
}

// MARK: - Memory match
struct MemoryMatchGame: View {
    private struct MCard: Identifiable { let id = UUID(); let symbol: String; var faceUp = false; var matched = false }
    @State private var cards: [MCard] = []
    @State private var firstUp: Int?
    @State private var moves = 0
    @State private var won = false
    @State private var busy = false

    private static let symbols = ["moon.stars", "leaf", "sparkles", "drop", "flame", "snowflake"]
    private let cols = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12),
                        GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        ScreenScaffold(eyebrow: "Match the pairs", title: "Memory match", trailingSystemImage: "square.grid.2x2") {
            HStack {
                Text("\(moves) moves").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.muted)
                Spacer()
                Button { deal() } label: {
                    Label("New game", systemImage: "arrow.clockwise").appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.soft)
                }.buttonStyle(.pressable)
            }
            LazyVGrid(columns: cols, spacing: 12) {
                ForEach(Array(cards.enumerated()), id: \.element.id) { i, c in
                    cardView(c).onTapGesture { flip(i) }
                }
            }
            if won {
                Card(cornerRadius: 18) {
                    Text("Lovely — all matched in \(moves) moves.")
                        .appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                }
            }
        }
        .onAppear { if cards.isEmpty { deal() } }
        .celebration(trigger: $won)
    }

    private func cardView(_ c: MCard) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(c.faceUp || c.matched ? Theme.Palette.cardEmphasis : Theme.Palette.card)
                .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Theme.Palette.line))
            if c.faceUp || c.matched {
                Image(systemName: c.symbol).appFont(22, weight: .semibold)
                    .foregroundStyle(c.matched ? Theme.Palette.lav : Theme.Palette.soft)
            } else {
                Image(systemName: "questionmark").appFont(15, weight: .bold).foregroundStyle(Theme.Palette.muted2)
            }
        }
        .frame(height: 66)
        .opacity(c.matched ? 0.55 : 1)
        .rotation3DEffect(.degrees(c.faceUp || c.matched ? 0 : 180), axis: (0, 1, 0))
        .animation(.easeInOut(duration: 0.25), value: c.faceUp)
    }

    private func deal() {
        var deck = (Self.symbols + Self.symbols).map { MCard(symbol: $0) }
        deck.shuffle()
        cards = deck; firstUp = nil; moves = 0; won = false; busy = false
    }

    private func flip(_ i: Int) {
        guard !busy, !cards[i].faceUp, !cards[i].matched else { return }
        cards[i].faceUp = true
        Haptics.selection()
        guard let f = firstUp else { firstUp = i; return }
        moves += 1
        firstUp = nil
        if cards[f].symbol == cards[i].symbol {
            cards[f].matched = true; cards[i].matched = true
            Haptics.success()
            if cards.allSatisfy(\.matched) { won = true }
        } else {
            busy = true
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
                cards[f].faceUp = false; cards[i].faceUp = false; busy = false
            }
        }
    }
}

// MARK: - Bubble wrap
struct BubbleWrapGame: View {
    @State private var popped: Set<Int> = []
    private let count = 48
    private let cols = Array(repeating: GridItem(.flexible(), spacing: 10), count: 6)

    var body: some View {
        ScreenScaffold(eyebrow: "Satisfying little pops", title: "Bubble wrap", trailingSystemImage: "circle.grid.3x3.fill") {
            HStack {
                Text("\(popped.count) / \(count) popped").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.muted)
                Spacer()
                Button { withAnimation { popped.removeAll() } } label: {
                    Label("Reset", systemImage: "arrow.clockwise").appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.soft)
                }.buttonStyle(.pressable)
            }
            LazyVGrid(columns: cols, spacing: 10) {
                ForEach(0..<count, id: \.self) { i in
                    let isPopped = popped.contains(i)
                    Circle()
                        .fill(isPopped ? Theme.Palette.card : Theme.Palette.lav.opacity(0.35))
                        .overlay(Circle().stroke(Theme.Palette.lav.opacity(isPopped ? 0.2 : 0.6), lineWidth: 1.5))
                        .overlay { if !isPopped {
                            Circle().fill(.white.opacity(0.25)).frame(width: 10).offset(x: -6, y: -6)
                        } }
                        .frame(height: 44)
                        .scaleEffect(isPopped ? 0.82 : 1)
                        .contentShape(Circle())
                        .onTapGesture {
                            guard !isPopped else { return }
                            withAnimation(.easeOut(duration: 0.15)) { _ = popped.insert(i) }
                            Haptics.soft(intensity: 0.5)
                        }
                }
            }
            if popped.count == count {
                Card(cornerRadius: 18) {
                    Text("All popped. Take a slow breath — reset for more.")
                        .appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                }
            }
        }
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

// MARK: - Sliding puzzle
struct SlidingPuzzleGame: View {
    @State private var tiles: [Int] = Array(1...8) + [0]
    @State private var moves = 0
    @State private var won = false
    private let cols = Array(repeating: GridItem(.flexible(), spacing: 10), count: 3)
    private let solved = Array(1...8) + [0]

    var body: some View {
        ScreenScaffold(eyebrow: "Slide into order", title: "Sliding puzzle", trailingSystemImage: "square.grid.3x3.fill") {
            HStack {
                Text("\(moves) moves").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.muted)
                Spacer()
                Button { shuffle() } label: {
                    Label("Shuffle", systemImage: "shuffle").appFont(12, weight: .heavy).foregroundStyle(Theme.Palette.soft)
                }.buttonStyle(.pressable)
            }
            LazyVGrid(columns: cols, spacing: 10) {
                ForEach(0..<9, id: \.self) { i in tileView(i) }
            }
            if won {
                Card(cornerRadius: 18) {
                    Text("Solved in \(moves) moves — calm and clear.")
                        .appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.soft)
                }
            }
        }
        .onAppear { if tiles == solved { shuffle() } }
        .celebration(trigger: $won)
    }

    private func tileView(_ i: Int) -> some View {
        let v = tiles[i]
        return RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(v == 0 ? Color.clear : Theme.Palette.cardEmphasis)
            .overlay(v == 0 ? nil : RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Theme.Palette.line))
            .overlay(Text(v == 0 ? "" : "\(v)").appFont(22, weight: .heavy).foregroundStyle(Theme.Palette.text))
            .frame(height: 88)
            .contentShape(Rectangle())
            .onTapGesture { move(i) }
    }

    private func adjacent(_ a: Int, _ b: Int) -> Bool {
        abs(a / 3 - b / 3) + abs(a % 3 - b % 3) == 1
    }

    private func move(_ i: Int) {
        guard !won, let empty = tiles.firstIndex(of: 0), adjacent(i, empty) else { return }
        tiles.swapAt(i, empty); moves += 1; Haptics.selection()
        if tiles == solved { won = true; Haptics.success() }
    }

    private func shuffle() {
        won = false; moves = 0
        var t = solved
        var empty = 8
        for _ in 0..<120 {
            let neighbors = (0..<9).filter { adjacent($0, empty) }
            let pick = neighbors.randomElement()!
            t.swapAt(pick, empty); empty = pick
        }
        if t == solved { t.swapAt(0, 1) }   // never start already-solved
        tiles = t
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
            Text("\(sprouts.count) planted").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.muted)
            GeometryReader { geo in
                ZStack {
                    LinearGradient(colors: [Theme.Accent.sleep.opacity(0.2), Theme.Accent.calm.opacity(0.28)],
                                   startPoint: .top, endPoint: .bottom)
                    ForEach(sprouts) { s in
                        Image(systemName: s.symbol).appFont(22, weight: .semibold).foregroundStyle(s.tint)
                            .position(x: s.x * geo.size.width, y: s.y * geo.size.height)
                            .transition(.scale.combined(with: .opacity))
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
