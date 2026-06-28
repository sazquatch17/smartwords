// SmartWords / Lexica widget. Renders the current word as a themed card; reads
// theme + accent from the App Group so it recolors live with the app.
// Model/store/theme live in Shared/.

import WidgetKit
import SwiftUI

// MARK: - Timeline

struct Entry: TimelineEntry { let date: Date; let word: Word; let index: Int }

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry {
        Entry(date: Date(), word: WordStore.words().first!, index: 0)
    }
    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) {
        completion(placeholder(in: context))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        let words = WordStore.words()
        let cal = Calendar.current
        let start = cal.startOfDay(for: Date())
        let entries = stride(from: 0, to: 24, by: WordStore.rotationHours).map { h -> Entry in
            let date = cal.date(byAdding: .hour, value: h, to: start)!
            let i = WordStore.index(at: date, in: words)
            return Entry(date: date, word: words[i], index: i)
        }
        let next = cal.date(byAdding: .day, value: 1, to: start)!
        completion(Timeline(entries: entries, policy: .after(next)))
    }
}

// MARK: - Fonts

private func wSerif(_ s: CGFloat, _ w: Font.Weight = .semibold, italic: Bool = false) -> Font {
    let f = Font.custom("Newsreader", size: s).weight(w); return italic ? f.italic() : f
}
private func wMono(_ s: CGFloat) -> Font { Font.custom("Spline Sans Mono", size: s) }

// MARK: - View

struct SmartWordsView: View {
    @Environment(\.widgetFamily) private var family
    @Environment(\.colorScheme) private var scheme
    let entry: Entry

    private var defaults: UserDefaults? { UserDefaults(suiteName: WordStore.appGroup) }
    private var accent: Color { AppSettings.accent(from: defaults).color }
    private var palette: Palette {
        switch AppSettings.mode(from: defaults) {
        case .light: return .light
        case .dark:  return .dark
        case .auto:  return scheme == .dark ? .dark : .light
        }
    }
    private var gloss: String {
        if let s = entry.word.short, !s.isEmpty { return s }
        return entry.word.definition
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .containerBackground(palette.bg, for: .widget)
            .widgetURL(URL(string: "smartwords://word/\(entry.index)"))
    }

    @ViewBuilder private var content: some View {
        switch family {
        case .systemSmall:        small
        case .accessoryRectangular: lockRect
        default:                  medium
        }
    }

    // Medium — "WORD OF THE DAY" card
    private var medium: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("WORD OF THE DAY").font(wMono(9)).tracking(1.6).foregroundStyle(palette.muted)
                Spacer()
                Text(dateLabel).font(wMono(9)).tracking(0.6).foregroundStyle(palette.muted)
            }
            HStack(alignment: .firstTextBaseline, spacing: 9) {
                Text(entry.word.word).font(wSerif(30)).tracking(-0.6)
                    .lineLimit(1).minimumScaleFactor(0.5).foregroundStyle(palette.fg)
                if let ipa = entry.word.ipa, !ipa.isEmpty {
                    Text(ipa).font(wMono(9)).foregroundStyle(palette.muted)
                }
            }
            .padding(.top, 8)
            HStack(alignment: .top, spacing: 8) {
                RoundedRectangle(cornerRadius: 2).fill(accent).frame(width: 2.5)
                VStack(alignment: .leading, spacing: 4) {
                    Text(entry.word.definition).font(.system(size: 12)).lineSpacing(1.5)
                        .foregroundStyle(palette.fg).lineLimit(2)
                    if let ex = entry.word.example, !ex.isEmpty {
                        Text("\u{201C}\(ex)\u{201D}").font(wSerif(11, .regular, italic: true))
                            .foregroundStyle(palette.muted).lineLimit(1)
                    }
                }
            }
            .padding(.top, 10)
            Spacer(minLength: 0)
        }
    }

    // Small — "WORD" card
    private var small: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("WORD").font(wMono(8)).tracking(1.4).foregroundStyle(palette.muted)
                Spacer()
                Circle().fill(accent).frame(width: 7, height: 7)
            }
            Spacer(minLength: 6)
            Text(entry.word.word).font(wSerif(26)).tracking(-0.5)
                .lineLimit(1).minimumScaleFactor(0.4).foregroundStyle(palette.fg)
            Text((entry.word.pos ?? "word").uppercased()).font(wMono(7.5)).tracking(1.1)
                .foregroundStyle(accent).padding(.top, 6)
            Text(gloss).font(.system(size: 10)).lineSpacing(1)
                .foregroundStyle(palette.muted).lineLimit(2).padding(.top, 5)
            Spacer(minLength: 0)
        }
    }

    // Lock screen — monochrome, system-tinted
    private var lockRect: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(entry.word.word).font(wSerif(16)).lineLimit(1).minimumScaleFactor(0.6)
            if let pos = entry.word.pos, !pos.isEmpty {
                Text(pos.uppercased()).font(wMono(8)).tracking(0.8).foregroundStyle(.secondary)
            }
            Text(gloss).font(.system(size: 12)).lineLimit(2)
        }
    }

    private var dateLabel: String {
        let f = DateFormatter(); f.dateFormat = "MMM d"
        return f.string(from: entry.date).uppercased()
    }
}

// MARK: - Widget

@main
struct SmartWordsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "SmartWordsWidget", provider: Provider()) { entry in
            SmartWordsView(entry: entry)
        }
        .configurationDisplayName("Lexica")
        .description("A word a day, with its meaning.")
        .supportedFamilies([.systemSmall, .systemMedium, .accessoryRectangular])
    }
}
