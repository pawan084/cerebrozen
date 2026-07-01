import SwiftUI

// MARK: - Time-of-day + goal awareness for Home
//
// Turns the signals the app already stores (the hour, the user's stated goals,
// whether they've checked in today, plan progress) into one clear "next action"
// hero plus a time-appropriate content rail — instead of a hardcoded
// "Good evening" + sleep content at any hour.

enum DayPart {
    case morning, afternoon, evening, night

    static func current(_ date: Date = Date()) -> DayPart {
        switch Calendar.current.component(.hour, from: date) {
        case 5..<12:  return .morning
        case 12..<17: return .afternoon
        case 17..<22: return .evening
        default:      return .night
        }
    }

    var greeting: String {
        switch self {
        case .morning:   return "Good morning"
        case .afternoon: return "Good afternoon"
        case .evening:   return "Good evening"
        case .night:     return "Rest easy"
        }
    }

    var railTitle: String {
        switch self {
        case .morning:   return "For this morning"
        case .afternoon: return "For this afternoon"
        case .evening:   return "For tonight"
        case .night:     return "Winding down"
        }
    }
}

/// Where the single Home hero sends the user.
enum HomeRoute: Identifiable, Hashable {
    case mood, plan, breathe, sleep
    var id: Self { self }
}

/// The one thing worth doing next, with copy tuned to the moment.
struct DailyFocus {
    let tag: String
    let title: String
    let subtitle: String
    let cta: String
    let route: HomeRoute
}

extension AppState {
    /// True once a mood check-in has been logged today.
    var checkedInToday: Bool {
        guard let last = moodLogs.first else { return false }
        return Calendar.current.isDateInToday(last.date)
    }

    /// The user's headline goal from onboarding (drives Home copy).
    var primaryGoal: String { selectedGoals.first ?? "Reduce stress" }

    /// A hero title phrased around the primary goal.
    private var goalHeroTitle: String {
        switch primaryGoal {
        case "Sleep better":     return "Wind down for better sleep"
        case "Stop overthinking": return "Untangle a busy mind"
        case "Build confidence":  return "Build a little confidence"
        case "Feel less alone":   return "You're not alone today"
        case "Practice meditation": return "A few minutes of stillness"
        case "Calmer mornings":   return "Start the day grounded"
        default:                  return "Ease today's stress"
        }
    }

    /// Resolve the single next action: check in first, then the day's plan, then
    /// a time-appropriate wind-up / wind-down once everything's done.
    func homeFocus(_ part: DayPart = .current()) -> DailyFocus {
        if !checkedInToday {
            return DailyFocus(
                tag: "Start here", title: "How are you, really?",
                subtitle: "A 20-second check-in shapes today's plan.",
                cta: "Check in", route: .mood)
        }
        let planDone = Dummy.planSteps.allSatisfy { completedSteps.contains($0.title) }
        if !planDone {
            return DailyFocus(
                tag: "Today's focus", title: goalHeroTitle,
                subtitle: "One small step toward \(primaryGoal.lowercased()).",
                cta: "Begin", route: .plan)
        }
        switch part {
        case .evening, .night:
            return DailyFocus(
                tag: "Recommended now", title: "Quiet reset before sleep",
                subtitle: "Ease the day with breathing and one reflection.",
                cta: "Begin", route: .sleep)
        default:
            return DailyFocus(
                tag: "Recommended now", title: "A moment of calm",
                subtitle: "Reset your focus with a slow breath.",
                cta: "Begin", route: .breathe)
        }
    }

    /// A content rail matched to the time of day (and lightly to the sleep goal).
    func homeRail(_ part: DayPart = .current()) -> [ContentItem] {
        switch part {
        case .morning:
            // Morning calm, Soft focus, 3-min breath.
            var items = [Dummy.meditations[0], Dummy.meditations[1], Dummy.tonight[1]]
            if primaryGoal == "Sleep better" { items.append(Dummy.tonight[0]) }
            return items
        case .afternoon:
            // Focus + reset content — no morning-labelled item mid-afternoon.
            var items = [Dummy.meditations[1], Dummy.meditations[2], Dummy.tonight[1]]
            if primaryGoal == "Sleep better" { items.append(Dummy.tonight[0]) }
            return items
        case .evening, .night:
            return Dummy.tonight
        }
    }
}
