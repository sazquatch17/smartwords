// Shared between the app and the widget (included in both targets via project.yml).
// The widget renders the current word and rotates by the clock; the app reads the
// same store to show expanded detail and to change the rotation speed.

import Foundation

// MARK: - Model

struct Word: Codable, Hashable {
    let word: String
    let definition: String
    let short: String?     // 2-3 word gloss for the widget; falls back to definition
    let pos: String?       // optional: not all sources provide part of speech
    let example: String?   // optional usage sentence
    // Lexica design fields — rendered only when present (seed has none yet).
    let ipa: String?
    let synonyms: [String]?
    let antonyms: [String]?
    let origin: String?
}

struct Batch: Codable { let date: String?; let words: [Word] }

// MARK: - Store (App Group cache + bundled seed fallback)

enum WordStore {
    // App Group shared by the app + widget. Works on the simulator with the
    // entitlement declared in project.yml.
    static let appGroup = "group.com.example.smartwords"
    static let defaultRotationHours = 3

    private static var shared: UserDefaults? { UserDefaults(suiteName: appGroup) }

    /// How often the widget advances, in hours. User-set (App Group), else default.
    /// Always a divisor of 24 so a whole number of slots fits in a day.
    static var rotationHours: Int {
        let v = shared?.integer(forKey: "rotationHours") ?? 0
        return v > 0 ? v : defaultRotationHours
    }
    static func setRotationHours(_ hours: Int) { shared?.set(hours, forKey: "rotationHours") }

    /// Cache a fetched daily batch (validated JSON) for the widget + app to read.
    static func cache(batchData: Data) { shared?.set(batchData, forKey: "batch") }
    static var cachedBatchDate: String? {
        guard let data = shared?.data(forKey: "batch") else { return nil }
        return (try? JSONDecoder().decode(Batch.self, from: data))?.date
    }

    /// Today's words: cached batch if present, else bundled seed. Never empty.
    static func words() -> [Word] {
        if let data = shared?.data(forKey: "batch"),
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
                            pos: "noun", example: nil,
                            ipa: nil, synonyms: nil, antonyms: nil, origin: nil)] }
        return batch.words
    }

    /// Index of the word for a given moment — pure function of clock + batch.
    /// Advances one slot every `rotationHours` and one full day's worth per day,
    /// so it walks the whole list and never repeats the same day's set.
    /// rotation/calendar are injectable so the logic is unit-testable.
    /// ponytail: sequential walk; for non-repeating order, shuffle the batch server-side.
    static func index(at date: Date, in words: [Word],
                      rotationHours hours: Int = rotationHours,
                      calendar cal: Calendar = .current) -> Int {
        guard !words.isEmpty else { return 0 }
        let slotsPerDay = 24 / hours
        let day = cal.ordinality(of: .day, in: .era, for: date) ?? 0
        let slot = cal.component(.hour, from: date) / hours
        return (day * slotsPerDay + slot) % words.count
    }

    static func word(at date: Date, in words: [Word]) -> Word {
        words[index(at: date, in: words)]
    }
}
