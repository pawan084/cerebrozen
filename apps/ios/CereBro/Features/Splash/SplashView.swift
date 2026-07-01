import SwiftUI

/// Branded launch splash, composed in code (so it adapts to any screen with no
/// cropping) and referencing the splash artwork: a night-sky gradient + starfield,
/// the orb-lotus mark (the real brand image, clipped to a circle and glowing),
/// the wordmark + tagline, and an aurora horizon over a mountain-lake silhouette.
struct SplashView: View {
    @State private var appear = false
    @State private var breathe = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width
            ZStack {
                // Night sky.
                LinearGradient(
                    colors: [Theme.Brand.night, Theme.Brand.nightMid, Theme.Brand.nightPurple],
                    startPoint: .top, endPoint: .bottom
                )
                Starfield()
                // Aurora horizon + mountains + lake along the bottom.
                NightScenery()
                    .frame(height: geo.size.height * 0.46)
                    .frame(maxHeight: .infinity, alignment: .bottom)
                    .opacity(appear ? 1 : 0)

                // Foreground: orb, wordmark, tagline, footer.
                VStack(spacing: 0) {
                    Spacer(minLength: geo.size.height * 0.12)

                    OrbMark(size: min(w * 0.46, 230))
                        .scaleEffect((reduceMotion ? false : breathe) ? 1.03 : 1.0)
                        .opacity(appear ? 1 : 0)
                        .scaleEffect(appear ? 1 : 0.92)

                    Wordmark(size: min(w * 0.135, 58))
                        .padding(.top, 22)
                        .opacity(appear ? 1 : 0)

                    Text("Your AI Companion\nfor Mental Wellness")
                        .multilineTextAlignment(.center)
                        .appFont(16, weight: .medium)
                        .foregroundStyle(.white.opacity(0.82))
                        .lineSpacing(3)
                        .padding(.top, 14)
                        .opacity(appear ? 1 : 0)

                    Spacer()

                    HStack(spacing: 7) {
                        Image(systemName: "heart.fill").appFont(13).foregroundStyle(Theme.Palette.lav)
                        Text("You matter. Always.")
                            .appFont(14, weight: .semibold).foregroundStyle(.white.opacity(0.7))
                    }
                    .padding(.bottom, geo.size.height * 0.045)
                    .opacity(appear ? 1 : 0)
                }
                .padding(.horizontal, 28)
            }
            .ignoresSafeArea()
        }
        .ignoresSafeArea()
        .onAppear {
            withAnimation(.easeOut(duration: reduceMotion ? 0.2 : 0.8)) { appear = true }
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 3.4).repeatForever(autoreverses: true)) { breathe = true }
        }
    }
}

// MARK: - Orb-lotus mark (the brand image, clipped + glowing)
private struct OrbMark: View {
    let size: CGFloat
    var body: some View {
        ZStack {
            Circle()
                .fill(RadialGradient(colors: [Theme.Brand.periwinkle.opacity(0.55), .clear],
                                     center: .center, startRadius: size * 0.1, endRadius: size * 0.8))
                .frame(width: size * 1.7, height: size * 1.7)
                .blur(radius: 24)
            // The real orb-lotus art; clipping to a circle drops the artwork's
            // square corners so its dark field blends into the night sky.
            Image("BrandLogo")
                .resizable()
                .scaledToFit()
                .clipShape(Circle())
                .frame(width: size, height: size)
                .overlay(Circle().stroke(Color.white.opacity(0.06), lineWidth: 1))
        }
    }
}

// MARK: - Wordmark
private struct Wordmark: View {
    let size: CGFloat
    var body: some View {
        HStack(spacing: 0) {
            Text("Cere").foregroundStyle(.white)
            Text("Bro").foregroundStyle(Theme.Gradient.wordmark)
        }
        .font(.system(size: size, weight: .bold, design: .rounded))
        .shadow(color: Theme.Brand.periwinkle.opacity(0.35), radius: 18, y: 4)
    }
}

// MARK: - Starfield (deterministic; upper sky)
private struct Starfield: View {
    private let count = 46
    private func h(_ i: Int, _ salt: Double) -> Double {
        let v = sin(Double(i) * 12.9898 + salt) * 43758.5453
        return v - floor(v)
    }
    var body: some View {
        Canvas { ctx, sz in
            for i in 0..<count {
                let x = h(i, 0) * sz.width
                let y = h(i, 7.1) * sz.height * 0.62
                let r = 0.6 + h(i, 3.3) * 1.6
                let o = 0.25 + h(i, 9.9) * 0.6
                ctx.fill(Path(ellipseIn: CGRect(x: x, y: y, width: r, height: r)),
                         with: .color(.white.opacity(o)))
            }
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Aurora horizon + mountains + lake
private struct NightScenery: View {
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            ZStack {
                // Aurora ribbons.
                Ellipse()
                    .fill(LinearGradient(colors: [Theme.Brand.cyan.opacity(0.5), .clear],
                                         startPoint: .leading, endPoint: .trailing))
                    .frame(width: w * 1.1, height: h * 0.5)
                    .blur(radius: 38).offset(x: -w * 0.18, y: h * 0.12)
                Ellipse()
                    .fill(LinearGradient(colors: [.clear, Theme.Brand.violet.opacity(0.5)],
                                         startPoint: .leading, endPoint: .trailing))
                    .frame(width: w * 1.1, height: h * 0.5)
                    .blur(radius: 40).offset(x: w * 0.2, y: h * 0.16)

                // Horizon glow + its reflection on the water.
                Circle()
                    .fill(RadialGradient(colors: [Theme.Brand.lavender, Theme.Brand.violet.opacity(0.0)],
                                         center: .center, startRadius: 1, endRadius: w * 0.28))
                    .frame(width: w * 0.6, height: w * 0.6)
                    .position(x: w * 0.5, y: h * 0.62)
                Rectangle()
                    .fill(LinearGradient(colors: [Theme.Brand.lavender.opacity(0.5), .clear],
                                         startPoint: .top, endPoint: .bottom))
                    .frame(width: w * 0.06, height: h * 0.34)
                    .blur(radius: 3)
                    .position(x: w * 0.5, y: h * 0.8)

                // Far + near mountain ridges.
                MountainRidge(peaks: [0.30, 0.52, 0.34, 0.6, 0.4, 0.5, 0.32])
                    .fill(LinearGradient(colors: [Color(hex: 0x2A2570), Color(hex: 0x191550)],
                                         startPoint: .top, endPoint: .bottom))
                    .frame(height: h * 0.5).frame(maxHeight: .infinity, alignment: .bottom)
                    .offset(y: h * 0.04)
                MountainRidge(peaks: [0.18, 0.4, 0.22, 0.46, 0.26, 0.42, 0.2])
                    .fill(LinearGradient(colors: [Color(hex: 0x151138), Color(hex: 0x0C0A24)],
                                         startPoint: .top, endPoint: .bottom))
                    .frame(height: h * 0.42).frame(maxHeight: .infinity, alignment: .bottom)

                // Lake.
                LinearGradient(colors: [Color(hex: 0x141A52).opacity(0.0), Color(hex: 0x0B0A28)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: h * 0.26).frame(maxHeight: .infinity, alignment: .bottom)
            }
        }
        .allowsHitTesting(false)
    }
}

private struct MountainRidge: Shape {
    let peaks: [CGFloat]   // normalized heights (0…1) across the width
    func path(in rect: CGRect) -> Path {
        var p = Path()
        guard peaks.count > 1 else { return p }
        let step = rect.width / CGFloat(peaks.count - 1)
        func y(_ i: Int) -> CGFloat { rect.maxY - peaks[i] * rect.height }
        p.move(to: CGPoint(x: 0, y: rect.maxY))
        p.addLine(to: CGPoint(x: 0, y: y(0)))
        for i in 1..<peaks.count {
            let x = step * CGFloat(i)
            let midX = x - step / 2
            p.addQuadCurve(to: CGPoint(x: x, y: y(i)), control: CGPoint(x: midX, y: y(i - 1)))
        }
        p.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY))
        p.closeSubpath()
        return p
    }
}
