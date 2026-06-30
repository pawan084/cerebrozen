import SwiftUI

/// Routes a backend `widget_kind` / suggestion `action` to the native activity
/// screen. This is the SwiftUI port of the reference app's inline-widget
/// registry — the companion can launch real activities from inside the chat.
struct ActivityDestination: View {
    let kind: String
    var body: some View {
        switch kind {
        case "breathing":            BreathingView()
        case "grounding":            GroundingView()
        case "mood_check":           MoodCheckinView()
        case "mini_journal", "journal": JournalEntryView()
        case "one_good_thing":       OneGoodThingView()
        case "intention_set":        IntentionSetView()
        case "dbt_skill":            DBTSkillView()
        case "crisis":               CrisisView()
        default:                     BreathingView()
        }
    }

    static func icon(_ kind: String) -> String {
        switch kind {
        case "breathing": return "wind"
        case "grounding": return "checkmark.shield"
        case "mood_check": return "heart"
        case "mini_journal", "journal": return "book"
        case "one_good_thing": return "sparkles"
        case "intention_set": return "target"
        case "dbt_skill": return "bolt.heart"
        case "crisis": return "phone"
        default: return "sparkles"
        }
    }
}

/// Inline "suggested activity" card rendered under an assistant message. Tapping
/// it launches the activity right from the conversation.
struct ActivityWidgetCard: View {
    let widget: RemoteWidget
    var body: some View {
        NavigationLink { ActivityDestination(kind: widget.widget_kind) } label: {
            HStack(spacing: 12) {
                Image(systemName: ActivityDestination.icon(widget.widget_kind))
                    .appFont(17, weight: .semibold)
                    .foregroundStyle(Theme.Palette.lav)
                    .frame(width: 42, height: 42)
                    .background(Theme.Palette.lav.opacity(0.16), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                VStack(alignment: .leading, spacing: 3) {
                    Text("Suggested activity").eyebrow()
                    Text(widget.title).appFont(14.5, weight: .semibold).foregroundStyle(Theme.Palette.text)
                    Text(widget.description).appFont(11.5).foregroundStyle(Theme.Palette.muted)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 4)
                Image(systemName: "play.circle.fill").appFont(26).foregroundStyle(Theme.Palette.lav)
            }
            .padding(13)
            .background(Theme.Palette.cardEmphasis)
            .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Theme.Palette.lav.opacity(0.35)))
        }
        .buttonStyle(.pressable)
        .accessibilityLabel("Start activity: \(widget.title)")
    }
}

/// Inline "approve this action?" card shown when the Oracle wants to run a write
/// tool (log mood, save journal). Ported from the reference `ToolConfirmCard`.
struct ToolConfirmCard: View {
    let confirm: OracleConfirm
    var onResolve: (Bool) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 11) {
            Text("Confirm action").eyebrow()
            Text(confirm.summary)
                .appFont(14, weight: .semibold).foregroundStyle(Theme.Palette.text)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                Button { onResolve(true) } label: {
                    Text("Approve").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.ink)
                        .frame(maxWidth: .infinity).frame(height: 42)
                        .background(Theme.Palette.cream, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }.buttonStyle(.pressable).accessibilityLabel("Approve action")
                Button { onResolve(false) } label: {
                    Text("Not now").appFont(13, weight: .heavy).foregroundStyle(Theme.Palette.soft)
                        .frame(maxWidth: .infinity).frame(height: 42)
                        .background(Theme.Palette.card, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Theme.Palette.line))
                }.buttonStyle(.pressable).accessibilityLabel("Decline action")
            }
        }
        .padding(14)
        .background(Theme.Palette.cardEmphasis)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Theme.Palette.lav.opacity(0.4)))
    }
}

/// Personalized "conversation starters" generated from the user's onboarding
/// self-reflection (motivations + goals). Tapping a topic seeds the chat — it
/// flows straight into the Oracle when available. Laid out in two compact,
/// horizontally-scrolling rows so longer topics stay readable.
struct ConversationStartersRail: View {
    let topics: [RemoteTopic]
    var onPick: (String) -> Void

    private var rows: [[RemoteTopic]] {
        var a: [RemoteTopic] = [], b: [RemoteTopic] = []
        for (i, t) in topics.enumerated() { if i.isMultiple(of: 2) { a.append(t) } else { b.append(t) } }
        return b.isEmpty ? [a] : [a, b]
    }

    var body: some View {
        if !topics.isEmpty {
            VStack(alignment: .leading, spacing: 9) {
                HStack(spacing: 6) {
                    Image(systemName: "sparkles").appFont(11, weight: .bold).foregroundStyle(Theme.Palette.lav)
                    Text("Start a conversation").eyebrow()
                }
                ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(row) { t in
                                Button { onPick(t.topic) } label: { chip(t.topic) }
                                    .buttonStyle(.pressable)
                                    .accessibilityLabel("Talk about: \(t.topic)")
                            }
                        }
                        .padding(.horizontal, 1)
                    }
                }
            }
        }
    }

    private func chip(_ text: String) -> some View {
        Text(text)
            .appFont(12.5, weight: .semibold)
            .foregroundStyle(Theme.Palette.soft)
            .lineLimit(1)
            .padding(.horizontal, 14).frame(height: 38)
            .background(Theme.Palette.cardEmphasis)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(Theme.Palette.lav.opacity(0.3)))
    }
}

/// Quick-reply chips under the composer. Activity/crisis actions navigate to the
/// matching screen; anything else is sent as a new message.
struct SuggestionChipRail: View {
    let suggestions: [RemoteSuggestion]
    var onSend: (String) -> Void
    private let navActions: Set<String> = ["breathing", "grounding", "mood_check", "mini_journal",
                                           "journal", "one_good_thing", "intention_set", "dbt_skill", "crisis"]

    var body: some View {
        if !suggestions.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(suggestions) { s in
                        if navActions.contains(s.action) {
                            NavigationLink { ActivityDestination(kind: s.action) } label: { chip(s) }
                                .buttonStyle(.pressable)
                        } else {
                            Button { onSend(s.label) } label: { chip(s) }
                                .buttonStyle(.pressable)
                        }
                    }
                }
                .padding(.horizontal, 1)
            }
        }
    }

    private func chip(_ s: RemoteSuggestion) -> some View {
        HStack(spacing: 5) {
            if s.action == "crisis" {
                Image(systemName: "lifepreserver").appFont(11, weight: .bold)
            }
            Text(s.label).appFont(12, weight: .heavy)
        }
        .foregroundStyle(s.action == "crisis" ? Theme.Palette.danger : Theme.Palette.soft)
        .padding(.horizontal, 13).frame(height: 34)
        .background(Theme.Palette.card)
        .clipShape(Capsule())
        .overlay(Capsule().stroke(s.action == "crisis" ? Theme.Palette.danger.opacity(0.4) : Theme.Palette.line))
    }
}
