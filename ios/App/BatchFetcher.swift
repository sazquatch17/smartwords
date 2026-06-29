// Daily batch pipeline (iOS side). Once a day a BGAppRefresh task fetches that
// day's word batch from a static host, validates it, and writes it to the App
// Group cache; the widget + app then read the cache (WordStore.words()), falling
// back to the bundled seed. The widget itself never makes a network call.
//
// Hosting is deferred (see SPEC.md): `host` is empty, so refresh() is a no-op and
// the app runs entirely on the seed. Point `host` at a static base URL to enable.

import Foundation
import BackgroundTasks
import WidgetKit

enum BatchFetcher {
    static let taskID = "com.example.smartwords.refresh"
    // e.g. "https://example.com" -> fetches "<host>/words/2026-06-24.json".
    static let host = ""

    /// Fetch today's batch and cache it. Safe to call from foreground or background.
    /// No-ops (keeps the seed/last cache) on any failure or when no host is set.
    static func refresh() async {
        guard !host.isEmpty, let url = url(for: Date()) else { return }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            guard (response as? HTTPURLResponse)?.statusCode == 200 else { return }
            // Validate before caching so a bad payload never replaces good data.
            let batch = try JSONDecoder().decode(Batch.self, from: data)
            guard !batch.words.isEmpty else { return }
            WordStore.cache(batchData: data)
            WidgetCenter.shared.reloadAllTimelines()
        } catch {
            // Leave the previous cache (or seed) in place.
        }
    }

    private static func url(for date: Date) -> URL? {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return URL(string: "\(host)/words/\(f.string(from: date)).json")
    }

    // MARK: - Background scheduling

    /// Ask the OS to run the refresh ~daily (best-effort; the OS decides timing).
    static func schedule() {
        let request = BGAppRefreshTaskRequest(identifier: taskID)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 6 * 60 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }
}
