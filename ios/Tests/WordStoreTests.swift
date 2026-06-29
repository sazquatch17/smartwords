// Unit tests for the core word-rotation logic and batch decoding.
// Mirrors android/.../WordStoreTest.kt — same properties, both platforms.

import XCTest
@testable import SmartWords

final class WordStoreTests: XCTestCase {

    // Fixed UTC calendar so hour/day are deterministic across machines.
    private var utc: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "UTC")!
        return c
    }

    private func date(_ iso: String) -> Date {
        let f = ISO8601DateFormatter()
        f.timeZone = TimeZone(identifier: "UTC")
        return f.date(from: iso)!
    }

    private let words = (0..<10).map {
        Word(word: "w\($0)", definition: "d", short: nil, pos: nil,
             example: nil, ipa: nil, synonyms: nil, antonyms: nil, origin: nil)
    }

    func testIndexInBounds() {
        for hour in 0..<24 {
            let d = date(String(format: "2026-06-24T%02d:00:00Z", hour))
            let i = WordStore.index(at: d, in: words, rotationHours: 3, calendar: utc)
            XCTAssertTrue((0..<words.count).contains(i), "index \(i) out of bounds at hour \(hour)")
        }
    }

    func testSameSlotSameIndex() {
        // 00:00 and 02:59 are in the same 3-hour slot.
        let a = WordStore.index(at: date("2026-06-24T00:00:00Z"), in: words, rotationHours: 3, calendar: utc)
        let b = WordStore.index(at: date("2026-06-24T02:59:00Z"), in: words, rotationHours: 3, calendar: utc)
        XCTAssertEqual(a, b)
    }

    func testNextSlotAdvancesByOne() {
        let a = WordStore.index(at: date("2026-06-24T00:00:00Z"), in: words, rotationHours: 3, calendar: utc)
        let b = WordStore.index(at: date("2026-06-24T03:00:00Z"), in: words, rotationHours: 3, calendar: utc)
        XCTAssertEqual(b, (a + 1) % words.count)
    }

    func testNextDayAdvancesBySlotsPerDay() {
        let a = WordStore.index(at: date("2026-06-24T00:00:00Z"), in: words, rotationHours: 3, calendar: utc)
        let b = WordStore.index(at: date("2026-06-25T00:00:00Z"), in: words, rotationHours: 3, calendar: utc)
        XCTAssertEqual(b, (a + 8) % words.count)   // 24/3 = 8 slots/day
    }

    func testSingleWordAlwaysZero() {
        let one = [words[0]]
        XCTAssertEqual(WordStore.index(at: date("2026-06-24T15:00:00Z"), in: one, rotationHours: 3, calendar: utc), 0)
    }

    func testEmptyWordsDoesNotCrash() {
        XCTAssertEqual(WordStore.index(at: Date(), in: [], rotationHours: 3, calendar: utc), 0)
    }

    func testBatchDecoding() {
        let json = """
        {"date":"2026-06-24","words":[
          {"word":"ubiquitous","definition":"found everywhere.","short":"everywhere",
           "pos":"adjective","example":"smartphones are ubiquitous.","ipa":"/juːˈbɪkwɪtəs/",
           "synonyms":["omnipresent"],"antonyms":["rare"],"origin":"from Latin ubique."}
        ]}
        """.data(using: .utf8)!
        let batch = try! JSONDecoder().decode(Batch.self, from: json)
        XCTAssertEqual(batch.date, "2026-06-24")
        XCTAssertEqual(batch.words.count, 1)
        XCTAssertEqual(batch.words[0].word, "ubiquitous")
        XCTAssertEqual(batch.words[0].synonyms ?? [], ["omnipresent"])
    }

    func testSeedDecodingOmitsOptionalFields() {
        // Seed shape: no date, words may omit optional fields.
        let json = #"{"words":[{"word":"abate","definition":"subside.","short":null,"pos":null,"example":null,"ipa":null,"synonyms":null,"antonyms":null,"origin":null}]}"#
            .data(using: .utf8)!
        let batch = try! JSONDecoder().decode(Batch.self, from: json)
        XCTAssertNil(batch.date)
        XCTAssertEqual(batch.words.first?.word, "abate")
        XCTAssertNil(batch.words.first?.pos)
    }
}
