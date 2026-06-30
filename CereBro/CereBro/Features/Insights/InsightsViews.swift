import SwiftUI

// MARK: - Weekly insights
struct InsightsView: View {
    @EnvironmentObject var state: AppState

    /// Weekly metrics, with the data-backed rows reflecting real activity.
    private var metrics: [Metric] {
        Dummy.weeklyMetrics.map { m in
            switch m.label {
            case "Journal entries":
                let n = state.journalEntries.count
                return Metric(label: m.label, value: "\(n)", progress: min(1, Double(n) / 8))
            case "Calm sessions":
                let n = state.moodLogs.count + state.completedSteps.count
                return n > 0 ? Metric(label: m.label, value: "\(n)", progress: min(1, Double(n) / 12)) : m
            default:
                return m
            }
        }
    }

    var body: some View {
        ScreenScaffold(eyebrow: "Weekly report", title: "Weekly Insights", trailingSystemImage: "chart.line.uptrend.xyaxis") {
            HeroCard(tag: "This week", title: "Calmer evenings",
                     subtitle: "Your stress eased on days you journaled before bed.",
                     cta: "Apply this insight", imageURL: Dummy.Img.calm)
            Card {
                VStack(spacing: 16) {
                    ForEach(Array(metrics.enumerated()), id: \.element.id) { i, m in
                        MetricBar(label: m.label, value: m.value, progress: m.progress, index: i)
                    }
                }
            }
            NavRow(title: "Pattern dashboard", subtitle: "Transparent AI memory", systemImage: "brain", imageURL: Dummy.Img.write) { PatternsView() }
        }
    }
}

// MARK: - Pattern dashboard
struct PatternsView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Transparent AI memory", title: "Pattern Dashboard", trailingSystemImage: "brain") {
            Text("Everything CereBro has noticed. You can edit or delete any of it.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
            ForEach(Dummy.memoryItems) { m in
                NavRow(title: m.title, subtitle: m.subtitle, systemImage: m.symbol, imageURL: m.imageURL) { MemoryDetailView(item: m) }
            }
            NavRow(title: "Update next week's plan", subtitle: "Apply these patterns", systemImage: "arrow.triangle.2.circlepath", imageURL: Dummy.Img.plan, emphasis: true) { DailyPlanView() }
        }
    }
}

struct MemoryDetailView: View {
    let item: ContentItem
    @State private var text: String = ""
    var body: some View {
        ScreenScaffold(eyebrow: "Editable AI memory", title: "Memory Detail", trailingSystemImage: "brain") {
            Photo(url: item.imageURL, symbol: item.symbol).frame(height: 120).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
            InsightCard(label: "Remembered", title: item.title, detail: item.subtitle)
            Card(cornerRadius: 18) {
                TextField("Edit this memory…", text: $text, axis: .vertical)
                    .appFont(13).foregroundStyle(Theme.Palette.soft).frame(minHeight: 70, alignment: .topLeading)
            }
            PrimaryButton(title: "Save changes")
            SecondaryButton(title: "Delete this memory", systemImage: "trash")
        }
    }
}
