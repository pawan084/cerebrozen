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
                     subtitle: "The full sleep library, richer voice sessions, and deeper personalization.",
                     cta: store.isPremium ? "You're all set" : "Choose a plan",
                     imageURL: Dummy.Img.sleep) { chooseFeaturedPlan() }
                .sheen(period: 6)   // occasional shine sweep (ref cbSheen)

            if store.available {
                // Real StoreKit products (App Store Connect configured).
                ForEach(store.products, id: \.id) { product in
                    Button {
                        Analytics.track("paywall_cta", step: product.id)
                        Task {
                            await store.purchase(product, appAccountToken: UUID(uuidString: backend.user?.id ?? ""))
                            await syncEntitlement()
                        }
                    } label: {
                        StoreProductCard(product: product)
                    }
                    .buttonStyle(.pressable)
                    .disabled(store.isPremium)
                }
                SecondaryButton(title: "Restore purchases", systemImage: "arrow.clockwise") {
                    Task { await store.restore(); await syncEntitlement() }
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

            // Cancelling must be as reachable as subscribing (OECD dark-pattern
            // checklist) — Apple's manage-subscriptions page, right on the paywall.
            if let manageURL = URL(string: "https://apps.apple.com/account/subscriptions") {
                Link(destination: manageURL) {
                    HStack(spacing: 6) {
                        Image(systemName: "arrow.uturn.left")
                        Text("Manage or cancel anytime in your Apple ID subscriptions")
                    }
                    .appFont(12, weight: .semibold).foregroundStyle(Theme.Palette.muted)
                    .frame(maxWidth: .infinity)
                }
            }

            NavRow(title: "Human support option", subtitle: "Real helplines and counsellors", systemImage: "person.2", imageURL: Dummy.Img.support) { HumanSupportView() }
        }
        .task {
            // Anonymous funnel: the paywall was seen (no products, no account).
            Analytics.track("paywall_view")
            await store.load()
            await syncEntitlement()
        }
    }

    /// Forward the verified transaction to the backend so the server sets the
    /// authoritative tier (which unlocks the usage quota).
    private func syncEntitlement() async {
        if let jws = store.latestJWS { await backend.verifySubscription(jws) }
    }

    /// Hero CTA: buy the featured plan if products loaded, else surface why not.
    private func chooseFeaturedPlan() {
        guard !store.isPremium else { return }
        if let product = store.products.first {
            Analytics.track("paywall_cta", step: product.id)
            Task { await store.purchase(product, appAccountToken: UUID(uuidString: backend.user?.id ?? "")); await syncEntitlement() }
        } else {
            store.message = "In-app subscriptions aren't available yet — everything runs free for now."
        }
    }
}

/// A price card backed by a real StoreKit `Product`. Annual plans get a
/// "Best value" tag — the honest kind: the saving is priced in, not implied.
struct StoreProductCard: View {
    let product: Product
    private var isAnnual: Bool {
        product.subscription?.subscriptionPeriod.unit == .year
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack {
                Text(product.displayName).eyebrow()
                Spacer()
                if isAnnual { Tag("Best value") }
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
/// Shown when the free daily message cap is hit. Was dead code until
/// 2026-07-30 — declared but never presented, so the cap surfaced as a generic
/// failure and the server's explanation never reached anyone.
///
/// The copy now states the real number and the real reset time. It used to say
/// "resets at midnight", which is wrong outside UTC: the window is UTC, so in
/// India it clears at 05:30 local. `info.resetText` renders the server's
/// timestamp in the user's own timezone.
struct FreeLimitView: View {
    let info: FreeLimitInfo
    @Environment(\.dismiss) private var dismiss
    var body: some View {
        ScreenScaffold(eyebrow: "Today's free messages are used", title: "Back tomorrow, or sooner", trailingSystemImage: "clock") {
            InsightCard(
                label: info.limit > 0 ? "\(info.limit) messages a day on Free" : "Free daily limit",
                title: "You've used today's messages",
                detail: "Your count \(info.resetText). Everything else — journal, sleep, breathing, your plan — is still here in the meantime.")
            NavRow(title: "See premium plans", subtitle: "Unlimited daily conversations", systemImage: "crown", imageURL: Dummy.Img.sleep, emphasis: true) { PremiumView() }
            SecondaryButton(title: "Continue with free", systemImage: "checkmark") { dismiss() }
        }
    }
}
