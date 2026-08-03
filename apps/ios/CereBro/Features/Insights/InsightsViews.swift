import SwiftUI

// MARK: - Weekly insights
struct InsightsView: View {
    @EnvironmentObject var state: AppState
    @EnvironmentObject var backend: BackendService
    @State private var applyToPlan = false

    /// Weekly metrics. The server computes these from the user's own rows and
    /// gates each category on its own consent flag (`services/insights.py`), so
    /// when it has spoken, it is authoritative — this screen renders it as-is
    /// rather than second-guessing it.
    ///
    /// Signed out (or when the fetch failed) only locally-derived rows are
    /// shown. There is deliberately no filler: a "Sleep consistency: Improving"
    /// row nobody measured is worse than no row.
    private var metrics: [Metric] {
        if let served = backend.insight?.metrics, !served.isEmpty {
            return served.map { Metric(label: $0.label, value: $0.value, progress: $0.progress) }
        }
        return localMetrics
    }

    /// Rows derivable from on-device data alone.
    private var localMetrics: [Metric] {
        var rows: [Metric] = []
        let sessions = state.moodLogs.count + state.completedSteps.count
        if sessions > 0 {
            rows.append(Metric(label: "Calm sessions", value: "\(sessions)",
                               progress: min(1, Double(sessions) / 12)))
        }
        let entries = state.journalEntries.count
        if entries > 0 {
            rows.append(Metric(label: "Journal entries", value: "\(entries)",
                               progress: min(1, Double(entries) / 8)))
        }
        let nights = state.last7Sleep().compactMap(\.entry)
        if !nights.isEmpty {
            let avg = nights.map(\.durationMin).reduce(0, +) / nights.count
            rows.append(Metric(label: "Sleep",
                               value: "\(avg / 60)h \(String(format: "%02d", avg % 60))m avg",
                               progress: min(1, Double(avg) / 480)))
        }
        return rows
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Weekly report", title: "Weekly Insights", trailingSystemImage: "chart.line.uptrend.xyaxis") {
            if let insight = backend.insight {
                HeroCard(tag: "From your week", title: insight.headline,
                         subtitle: insight.summary,
                         cta: "Open your plan", imageURL: Dummy.Img.calm) { applyToPlan = true }
            } else {
                // Signed out: a general observation offered as a question, not a
                // claim about this user — there is no data here to claim from.
                HeroCard(tag: "A pattern to look for", title: "Calmer evenings",
                         subtitle: "Many people find stress eases on days they journal before bed — see if that's true for you.",
                         cta: "Open your plan", imageURL: Dummy.Img.calm) { applyToPlan = true }
            }

            // The baseline ask lives HERE now (Home was de-densified — the ask
            // belongs where its payoff renders): offered once the check-in
            // habit exists, until answered.
            if !state.hasBaseline && state.moodLogs.count >= 3 {
                NavRow(title: "Set your starting point",
                       subtitle: "Two quick scales — so Insights can show real change",
                       systemImage: "flag", imageURL: Dummy.Img.calm) { BaselineCheckView() }
            }
            // The real onboarding baseline — a true "before" to measure against.
            if state.hasBaseline {
                Card {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Your starting point").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.soft)
                            Spacer()
                            if let d = state.baselineDate {
                                Text(baselineDateText(d)).appFont(11).foregroundStyle(Theme.Palette.muted2)
                            }
                        }
                        MetricBar(label: "Stress at start", value: "\(state.baselineStress)/5",
                                  progress: Double(state.baselineStress) / 5, color: Theme.Palette.stress, index: 0)
                        MetricBar(label: "Sleep at start", value: "\(state.baselineSleep)/5",
                                  progress: Double(state.baselineSleep) / 5, color: Theme.Palette.lav, index: 1)
                    }
                }
            }

            Card {
                VStack(spacing: 16) {
                    let rows = metrics
                    if rows.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Nothing to measure yet")
                                .appFont(14, weight: .bold).foregroundStyle(Theme.Palette.soft)
                            Text("Check in, write an entry or log a night's sleep and this fills in with your own numbers.")
                                .appFont(12).foregroundStyle(Theme.Palette.muted)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    } else {
                        ForEach(Array(rows.enumerated()), id: \.element.id) { i, m in
                            MetricBar(label: m.label, value: m.value, progress: m.progress, index: i)
                        }
                        if backend.insight == nil {
                            Text("Counted on this device. Sign in to include what you log elsewhere.")
                                .appFont(11).foregroundStyle(Theme.Palette.muted2)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }
                }
            }
            // The REAL dashboard (server-mined patterns + real deletion). This
            // used to open a `PatternsView` that rendered invented memories with
            // save/delete buttons that did nothing — deleted 2026-07-30.
            NavRow(title: "Pattern dashboard", subtitle: "Transparent AI memory", systemImage: "brain", imageURL: Dummy.Img.write) { PatternDashboardView() }
        }
        .navigationDestination(isPresented: $applyToPlan) { DailyPlanView() }
    }

    private func baselineDateText(_ date: Date) -> String {
        let f = DateFormatter(); f.dateStyle = .medium
        return "since \(f.string(from: date))"
    }
}

// `PatternsView` + `MemoryDetailView` lived here and were deleted 2026-07-30.
//
// They were a second, fabricated Pattern Dashboard: rows came from
// `Dummy.memoryItems` ("Pattern: stress spikes after meetings · Observed 4
// times" — an invented count), "Save changes" played a success celebration and
// dismissed without writing anything, and "Delete this memory" only dismissed.
// Weekly Insights linked here, so this — not the real `PatternDashboardView` —
// is what a user reached from that screen.
//
// Simulating control over what an AI remembers is worse than showing nothing:
// someone believed they had deleted a memory and nothing happened. The single
// real dashboard is `PatternDashboardView.swift`.
