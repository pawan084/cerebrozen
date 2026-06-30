import SwiftUI

// MARK: - Premium upgrade
struct PremiumView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Subscription funnel", title: "CereBro Premium", trailingSystemImage: "crown") {
            HeroCard(tag: "Upgrade", title: "Unlock your calmest self",
                     subtitle: "Premium sleep library, downloads, and unlimited voice support.",
                     cta: "Start free trial", imageURL: Dummy.Img.premium)
            ForEach(Dummy.plans) { plan in
                PriceCard(plan: plan)
            }
            NavRow(title: "Human support option", subtitle: "Partner booking flow", systemImage: "person.2", imageURL: Dummy.Img.support) { HumanSupportView() }
        }
    }
}

struct PriceCard: View {
    let plan: PricePlan
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack {
                Text(plan.tier).eyebrow()
                Spacer()
                if plan.featured { Tag("Most popular") }
            }
            Text(plan.price).displayFont(25).foregroundStyle(Theme.Palette.text)
            Text(plan.detail).appFont(12).foregroundStyle(Theme.Palette.muted)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(plan.featured ? Theme.Palette.cardEmphasis : Theme.Palette.card)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous)
            .stroke(plan.featured ? Theme.Palette.lav.opacity(0.6) : Theme.Palette.line, lineWidth: 1))
    }
}

// MARK: - Free limit state
struct FreeLimitView: View {
    var body: some View {
        ScreenScaffold(eyebrow: "Usage limit state", title: "Free Limit Reached", trailingSystemImage: "lock") {
            Photo(url: Dummy.Img.premium, symbol: "lock").frame(height: 132).frame(maxWidth: .infinity)
                .clipShape(RoundedRectangle(cornerRadius: 23, style: .continuous))
            InsightCard(label: "Today's free voice minutes are used", title: "Upgrade for unlimited voice support",
                        detail: "You've reached the free daily limit. Premium removes the cap.")
            NavRow(title: "See premium plans", subtitle: "Premium sleep library, downloads", systemImage: "crown", imageURL: Dummy.Img.premium, emphasis: true) { PremiumView() }
            SecondaryButton(title: "Continue with free", systemImage: "checkmark")
        }
    }
}
