// SmartWords — iOS widget (reference implementation).
// Drop into a Widget Extension target. Requires an App Group shared with the app.
//
// Architecture: a background fetch (BGAppRefresh, in the app target) writes the
// day's batch to the App Group; this widget only reads the cache and rotates
// through it by the clock. Never makes a network call itself.

import WidgetKit
import SwiftUI

// MARK: - Model

struct Word: Codable, Hashable {
    let word: String
    let definition: String
    let short: String?     // 2-3 word gloss for the widget; falls back to definition
    let pos: String?       // optional: not all sources provide part of speech
    let example: String?   // optional usage sentence
}

private struct Batch: Codable { let date: String?; let words: [Word] }

// MARK: - Store (App Group cache + bundled seed fallback)

enum WordStore {
    // TODO: set to your real App Group id.
    static let appGroup = "group.com.example.smartwords"
    static let rotationHours = 3   // 8 slots/day

    /// Today's words: cached batch if present, else bundled seed. Never empty.
    static func words() -> [Word] {
        if let data = UserDefaults(suiteName: appGroup)?.data(forKey: "batch"),
           let batch = try? JSONDecoder().decode(Batch.self, from: data),
           !batch.words.isEmpty {
            return batch.words
        }
        return seed()
    }

    private static func seed() -> [Word] {
        guard let url = Bundle.main.url(forResource: "seed-words", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let batch = try? JSONDecoder().decode(Batch.self, from: data)
        else { return [Word(word: "smartwords",
                            definition: "a word a day, on your home screen.",
                            short: "word a day",
                            pos: "noun", example: nil)] }
        return batch.words
    }

    /// Word for a given moment — pure function of clock + batch. Advances one slot
    /// every `rotationHours` and one full day's worth of slots per day, so it walks
    /// the whole list and never repeats the same day's set.
    /// ponytail: sequential walk; if you want non-repeating shuffle, shuffle server-side.
    static func word(at date: Date, in words: [Word]) -> Word {
        let cal = Calendar.current
        let slotsPerDay = 24 / rotationHours
        let day = cal.ordinality(of: .day, in: .era, for: date) ?? 0
        let slot = cal.component(.hour, from: date) / rotationHours
        return words[(day * slotsPerDay + slot) % words.count]
    }
}

// MARK: - Timeline

struct Entry: TimelineEntry { let date: Date; let word: Word }

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry {
        Entry(date: Date(), word: WordStore.words().first!)
    }
    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) {
        completion(placeholder(in: context))
    }
    /// One entry per rotation slot for today; WidgetKit renders each at its time.
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        let words = WordStore.words()
        let cal = Calendar.current
        let start = cal.startOfDay(for: Date())
        let entries = stride(from: 0, to: 24, by: WordStore.rotationHours).map { h -> Entry in
            let date = cal.date(byAdding: .hour, value: h, to: start)!
            return Entry(date: date, word: WordStore.word(at: date, in: words))
        }
        // Rebuild after midnight so tomorrow picks up a freshly fetched batch.
        let next = cal.date(byAdding: .day, value: 1, to: start)!
        completion(Timeline(entries: entries, policy: .after(next)))
    }
}

// MARK: - View (editorial / serif, native New York font)

struct SmartWordsView: View {
    @Environment(\.widgetFamily) var family
    let entry: Entry

    // Prefer the short gloss; fall back to the full definition when absent.
    private var gloss: String {
        if let s = entry.word.short, !s.isEmpty { return s }
        return entry.word.definition
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(entry.word.word)
                .font(.system(.title2, design: .serif).weight(.semibold))
                .lineLimit(1).minimumScaleFactor(0.5)
            // pos when present; until POS data exists this line just doesn't show.
            if let pos = entry.word.pos, !pos.isEmpty {
                Text(pos)
                    .font(.system(.caption, design: .serif).italic())
                    .foregroundStyle(.secondary)
            }
            if family != .systemSmall {
                Divider().padding(.vertical, 2)
            }
            Text(gloss)
                .font(.system(family == .accessoryRectangular ? .caption2 : .footnote,
                              design: .serif))
                .foregroundStyle(family == .accessoryRectangular ? .primary : .secondary)
                .lineLimit(family == .systemSmall ? 3 : 5)
            // example sentence fills the larger families with real content we already have.
            if family == .systemMedium, let ex = entry.word.example, !ex.isEmpty {
                Text("“\(ex)”")
                    .font(.system(.caption2, design: .serif).italic())
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .containerBackground(.clear, for: .widget)
    }
}

// MARK: - Widget

@main
struct SmartWordsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SmartWordsWidget", provider: Provider()) { entry in
            SmartWordsView(entry: entry)
        }
        .configurationDisplayName("SmartWords")
        .description("A word a day, with its meaning.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}
