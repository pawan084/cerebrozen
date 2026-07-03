import HealthKit

/// Optional, off-by-default Apple Health integration: READS sleep samples to
/// pre-fill the morning check-in. Never writes, never claims accuracy — the
/// user confirms every entry, and manual stays the source of truth
/// (docs/SLEEP_TRACKING.md v1.5; App Store 5.1.3 constraints).
@MainActor
final class HealthKitSleep: ObservableObject {
    static var isSupported: Bool { HKHealthStore.isHealthDataAvailable() }

    private let store = HKHealthStore()
    private static let sleepType = HKCategoryType(.sleepAnalysis)

    struct Night {
        let bedMinutes: Int
        let wakeMinutes: Int
        /// True when stage samples (core/deep/REM — effectively Apple Watch data)
        /// were present, not just an inBed/unspecified window.
        let hasStages: Bool
    }

    /// Ask for read access. iOS shows the sheet once; read grants are not
    /// introspectable afterwards — no data and denial look identical, and both
    /// simply mean "nothing to pre-fill".
    func requestAccess() async -> Bool {
        guard Self.isSupported else { return false }
        do {
            try await store.requestAuthorization(toShare: [], read: [Self.sleepType])
            return true
        } catch {
            return false
        }
    }

    /// Last night's sleep window (yesterday noon → now): bed = earliest sample
    /// start, wake = latest sample end, as wall-clock minutes for the diary.
    func lastNight() async -> Night? {
        guard Self.isSupported else { return nil }
        let cal = Calendar.current
        let now = Date()
        guard let windowStart = cal.date(bySettingHour: 12, minute: 0, second: 0,
                                         of: cal.date(byAdding: .day, value: -1, to: now) ?? now)
        else { return nil }

        let predicate = HKQuery.predicateForSamples(withStart: windowStart, end: now)
        let samples: [HKCategorySample] = await withCheckedContinuation { continuation in
            let query = HKSampleQuery(
                sampleType: Self.sleepType, predicate: predicate, limit: HKObjectQueryNoLimit,
                sortDescriptors: [NSSortDescriptor(key: HKSampleSortIdentifierStartDate, ascending: true)]
            ) { _, results, _ in
                continuation.resume(returning: (results as? [HKCategorySample]) ?? [])
            }
            store.execute(query)
        }

        let sleepValues: Set<Int> = [
            HKCategoryValueSleepAnalysis.inBed.rawValue,
            HKCategoryValueSleepAnalysis.asleepUnspecified.rawValue,
            HKCategoryValueSleepAnalysis.asleepCore.rawValue,
            HKCategoryValueSleepAnalysis.asleepDeep.rawValue,
            HKCategoryValueSleepAnalysis.asleepREM.rawValue,
        ]
        let stageValues: Set<Int> = [
            HKCategoryValueSleepAnalysis.asleepCore.rawValue,
            HKCategoryValueSleepAnalysis.asleepDeep.rawValue,
            HKCategoryValueSleepAnalysis.asleepREM.rawValue,
        ]
        let night = samples.filter { sleepValues.contains($0.value) }
        guard let bed = night.map(\.startDate).min(), let wake = night.map(\.endDate).max() else { return nil }

        func minutes(_ date: Date) -> Int {
            let c = cal.dateComponents([.hour, .minute], from: date)
            return (c.hour ?? 0) * 60 + (c.minute ?? 0)
        }
        return Night(bedMinutes: minutes(bed), wakeMinutes: minutes(wake),
                     hasStages: night.contains { stageValues.contains($0.value) })
    }
}
