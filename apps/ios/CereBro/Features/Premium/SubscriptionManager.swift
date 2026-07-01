import StoreKit
import SwiftUI

/// StoreKit 2 subscription flow. Fully functional once the products below exist
/// in App Store Connect (and, for real entitlement enforcement, once the backend
/// validates receipts via the App Store Server API / Server Notifications).
///
/// Until then it degrades gracefully: `Product.products(for:)` simply returns an
/// empty list, `available` stays false, and the paywall shows a "coming soon"
/// state instead of crashing — so the app ships and runs with no store config.
@MainActor
final class SubscriptionManager: ObservableObject {
    /// Product identifiers to create in App Store Connect.
    static let productIDs = ["com.cerebrozen.premium.monthly", "com.cerebrozen.premiumhuman.monthly"]

    @Published private(set) var products: [Product] = []
    /// Local entitlement ("free" | "premium" | "premium_human"). The server holds
    /// the authoritative tier once receipt validation is wired.
    @Published private(set) var entitledTier = "free"
    @Published private(set) var available = false
    @Published var message: String?
    /// The most recent verified transaction's JWS — forwarded to the backend for
    /// authoritative, server-side entitlement.
    @Published private(set) var latestJWS: String?

    var isPremium: Bool { entitledTier != "free" }

    func load() async {
        do {
            let items = try await Product.products(for: Self.productIDs)
            products = items.sorted { $0.price < $1.price }
            available = !products.isEmpty
        } catch {
            available = false
        }
        await refreshEntitlements()
    }

    /// - Parameter appAccountToken: the user's id, stamped on the transaction so
    ///   App Store Server Notifications can map lifecycle events back to the user.
    func purchase(_ product: Product, appAccountToken: UUID? = nil) async {
        do {
            var options: Set<Product.PurchaseOption> = []
            if let token = appAccountToken { options.insert(.appAccountToken(token)) }
            switch try await product.purchase(options: options) {
            case let .success(verification):
                if case let .verified(transaction) = verification {
                    latestJWS = verification.jwsRepresentation
                    await transaction.finish()
                    await refreshEntitlements()
                    message = "You're Premium — thank you."
                }
            case .userCancelled:
                break
            default:
                break
            }
        } catch {
            message = "Purchase couldn't complete. Please try again."
        }
    }

    func restore() async {
        try? await AppStore.sync()
        await refreshEntitlements()
        message = isPremium ? "Purchases restored." : "No active subscription found."
    }

    private func refreshEntitlements() async {
        var tier = "free"
        for await result in Transaction.currentEntitlements {
            guard case let .verified(t) = result else { continue }
            latestJWS = result.jwsRepresentation      // for server-side re-verification
            if t.productID.contains("premiumhuman") { tier = "premium_human" }
            else if t.productID.contains("premium") { tier = "premium" }
        }
        entitledTier = tier
    }
}
