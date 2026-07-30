import SwiftUI

/// A personal safety plan, in the user's own words — the six Stanley-Brown
/// sections. Nothing here is written, scored or interpreted by the model.
///
/// Two rules this screen exists to keep:
///
/// 1. **The user writes it.** No section is pre-filled by the AI. A suggestion
///    would have to be confirmed before it could be saved, and none is offered.
/// 2. **It opens without a network.** A plan you can only read when the server
///    is reachable is worthless at the moment it matters, so the last saved
///    copy is mirrored to `UserDefaults` and shown — labelled — when the fetch
///    fails. Signed out, the local copy is still readable.
struct SafetyPlanView: View {
    @EnvironmentObject var backend: BackendService

    private struct Section: Identifiable {
        let field: String
        let label: String
        let hint: String
        var id: String { field }
    }

    private static let sections: [Section] = [
        .init(field: "warning_signs", label: "Warning signs I notice",
              hint: "Thoughts, feelings or situations that tell you things are getting harder."),
        .init(field: "internal_coping", label: "Things I can do on my own",
              hint: "What has helped before, without needing anyone else."),
        .init(field: "social_distractors", label: "People and places that take my mind off it",
              hint: "Company or surroundings that shift the mood — no conversation required."),
        .init(field: "social_support", label: "People I can ask for help",
              hint: "Names and numbers, so you don't have to think of them later."),
        .init(field: "professionals", label: "Professionals and services I can contact",
              hint: "Your doctor, a counsellor, a helpline you trust."),
        .init(field: "means_safety", label: "Making my space safer",
              hint: "Anything you'd want out of reach on a hard night, and who could hold it."),
        .init(field: "notes", label: "Anything else",
              hint: "Whatever else you want future-you to read.")
    ]

    private static let cacheKey = "safetyPlanCache"

    @State private var values: [String: String] = [:]
    @State private var version: Int?
    @State private var loading = true
    @State private var offline = false
    @State private var savingField: String?
    @State private var savedField: String?
    @State private var error: String?

    var body: some View {
        ScreenScaffold(eyebrow: "Yours, in your words", title: "My safety plan",
                       trailingSystemImage: "shield.lefthalf.filled") {
            Text("A plan you write while things are steady, so a harder day has fewer decisions in it. Fill in as much or as little as you like — a part-written plan still helps.")
                .appFont(13).foregroundStyle(Theme.Palette.muted)
                .fixedSize(horizontal: false, vertical: true)
            Text("CereBro doesn't read this back to you as advice, score it, or share it.")
                .appFont(11.5).foregroundStyle(Theme.Palette.muted2)
                .fixedSize(horizontal: false, vertical: true)

            if offline {
                Text("Showing the copy saved on this device — we couldn't reach the server.")
                    .appFont(11.5).foregroundStyle(Theme.Palette.stress)
                    .fixedSize(horizontal: false, vertical: true)
            }
            if let error {
                Text(error).appFont(12).foregroundStyle(Theme.Palette.danger)
                    .fixedSize(horizontal: false, vertical: true)
            }

            if loading {
                Text("Loading…").appFont(13).foregroundStyle(Theme.Palette.muted)
            } else {
                ForEach(Self.sections) { section in
                    Card {
                        VStack(alignment: .leading, spacing: 8) {
                            Text(section.label)
                                .appFont(14.5, weight: .bold).foregroundStyle(Theme.Palette.soft)
                            Text(section.hint)
                                .appFont(11.5).foregroundStyle(Theme.Palette.muted2)
                                .fixedSize(horizontal: false, vertical: true)
                            TextField("", text: binding(for: section.field), axis: .vertical)
                                .appFont(13).foregroundStyle(Theme.Palette.text)
                                .frame(minHeight: 60, alignment: .topLeading)
                                .accessibilityLabel(section.label)
                            HStack(spacing: 14) {
                                Button(savingField == section.field ? "Saving…" : "Save") {
                                    save(section.field)
                                }
                                .appFont(13, weight: .semibold)
                                .foregroundStyle(Theme.Palette.lav)
                                .disabled(savingField == section.field)
                                .frame(minHeight: 44)
                                if savedField == section.field {
                                    Text("Saved.").appFont(12).foregroundStyle(Theme.Palette.muted)
                                }
                            }
                        }
                    }
                }

                if let version {
                    Text("Version \(version)")
                        .appFont(11).foregroundStyle(Theme.Palette.muted2)
                }
                NavRow(title: "Crisis support", subtitle: "Real people, right now",
                       systemImage: "lifepreserver", imageURL: Dummy.Img.privacy) {
                    CrisisView()
                }
            }
        }
        .task { await load() }
    }

    private func binding(for field: String) -> Binding<String> {
        Binding(get: { values[field] ?? "" }, set: { values[field] = $0 })
    }

    private func load() async {
        // Read the cache first so something is on screen even if the network
        // never answers — then let the server correct it.
        loadCache()
        guard backend.isConnected else { loading = false; return }
        do {
            if let plan = try await APIClient.shared.safetyPlan() {
                apply(plan)
                cache(plan)
            }
            offline = false
        } catch {
            offline = !values.isEmpty
        }
        loading = false
    }

    private func apply(_ plan: RemoteSafetyPlan) {
        values = [
            "warning_signs": plan.warning_signs,
            "internal_coping": plan.internal_coping,
            "social_distractors": plan.social_distractors,
            "social_support": plan.social_support,
            "professionals": plan.professionals,
            "means_safety": plan.means_safety,
            "notes": plan.notes
        ]
        version = plan.version
    }

    private func cache(_ plan: RemoteSafetyPlan) {
        if let data = try? JSONEncoder().encode(plan) {
            UserDefaults.standard.set(data, forKey: Self.cacheKey)
        }
    }

    private func loadCache() {
        guard let data = UserDefaults.standard.data(forKey: Self.cacheKey),
              let plan = try? JSONDecoder().decode(RemoteSafetyPlan.self, from: data)
        else { return }
        apply(plan)
        offline = true   // corrected below if the server answers
    }

    private func save(_ field: String) {
        guard backend.isConnected else {
            error = "Sign in to save your plan — what you've typed is still here."
            return
        }
        savingField = field
        error = nil
        Task {
            do {
                let plan = try await APIClient.shared.saveSafetyPlan([field: values[field] ?? ""])
                version = plan.version
                cache(plan)
                offline = false
                savedField = field
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                if savedField == field { savedField = nil }
            } catch {
                self.error = "Couldn't save that section — it's still here, try again in a moment."
            }
            savingField = nil
        }
    }
}
