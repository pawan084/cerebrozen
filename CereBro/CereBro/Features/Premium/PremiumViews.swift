import StoreKit
import SwiftUI

// MARK: - Premium upgrade
struct PremiumView: View {
    @EnvironmentObject var backend: BackendService
    @StateObject private var store = SubscriptionManager()

    var body: some View {
        ScreenScaffold(eyebrow: "Subscription funnel", title: "CereBro Premium", trailingSystemImage: "crown") {
            HeroCard(tag: store.isPremium ? "Active" : "Upgrade",
                     title: store.isPremium ? "You're Premium" : "Unlock your calmest self",
                     subtitle: "Premium sleep library, downloads, and unlimited voice support.",
                     cta: store.isPremium ? "You're all set" : "Choose a plan",
                     imageURL: Dummy.Img.premium)

            if store.available {
                // Real StoreKit products (App Store Connect configured).
                ForEach(store.products, id: \.id) { product in
                    Button { Task { await store.purchase(product) } } label: {
                        StoreProductCard(product: product)
                    }
                    .buttonStyle(.pressable)
                    .disabled(store.isPremium)
                }
                SecondaryButton(title: "Restore purchases", systemImage: "arrow.clockwise") {
                    Task { await store.restore() }
                }
            } else {
                // Graceful fallback until in-app purchases are set up.
                ForEach(Dummy.plans) { plan in PriceCard(plan: plan) }
                InsightCard(label: "Coming soon",
                            title: "In-app subscriptions aren't available yet.",
                            detail: "Everything here runs free for now — Premium unlocks once billing is enabled.")
            }

            if let msg = store.message {
                Text(msg).appFont(12).foregroundStyle(Theme.Palette.muted)
                    .frame(maxWidth: .infinity)
            }

            NavRow(title: "Human support option", subtitle: "Partner booking flow", systemImage: "person.2", imageURL: Dummy.Img.support) { HumanSupportView() }
        }
        .task { await store.load() }
    }
}

/// A price card backed by a real StoreKit `Product`.
struct StoreProductCard: View {
    let product: Product
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack {
                Text(product.displayName).eyebrow()
                Spacer()
                Text(product.displayPrice).displayFont(20).foregroundStyle(Theme.Palette.text)
            }
            Text(product.description).appFont(12).foregroundStyle(Theme.Palette.muted)
        }
        .padding(14).frame(maxWidth: .infinity, alignment: .leading)
        .background(Theme.Palette.cardEmphasis)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).stroke(Theme.Palette.lav.opacity(0.6)))
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
