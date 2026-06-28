// Lexica — word-of-the-day app. Today / Word Detail / Settings with live theming.
// Model/store/theme are shared with the widget (Shared/).

import SwiftUI
import WidgetKit
import AVFoundation
import UserNotifications

@main
struct SmartWordsApp: App {
    var body: some Scene { WindowGroup { RootView() } }
}

// Daily 9 AM word-of-the-day notifications. Schedules a rolling 14-day window,
// each with that day's word (local notifications need no server/push).
enum Notifier {
    static func apply(enabled: Bool) {
        let center = UNUserNotificationCenter.current()
        guard enabled else { center.removeAllPendingNotificationRequests(); return }
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            if granted { DispatchQueue.main.async { schedule() } }
        }
    }

    /// Refresh the rolling window (safe to call on launch; never prompts).
    static func schedule() {
        let center = UNUserNotificationCenter.current()
        center.removeAllPendingNotificationRequests()
        let words = WordStore.words()
        let cal = Calendar.current
        for offset in 0..<14 {
            guard let day = cal.date(byAdding: .day, value: offset, to: Date()) else { continue }
            var comps = cal.dateComponents([.year, .month, .day], from: day)
            comps.hour = 9; comps.minute = 0
            guard let fire = cal.date(from: comps), fire > Date() else { continue }
            let word = WordStore.word(at: fire, in: words)
            let content = UNMutableNotificationContent()
            content.title = word.word
            content.body = word.short ?? word.definition
            content.sound = .default
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: cal.dateComponents([.year, .month, .day, .hour, .minute], from: fire),
                repeats: false)
            center.add(UNNotificationRequest(identifier: "word-\(offset)", content: content, trigger: trigger))
        }
    }
}

// Speaks a word aloud. Synthesizer is retained so speech isn't cut off.
enum Speaker {
    private static let synth = AVSpeechSynthesizer()
    static func speak(_ text: String) {
        let u = AVSpeechUtterance(string: text)
        u.rate = AVSpeechUtteranceDefaultSpeechRate
        synth.stopSpeaking(at: .immediate)
        synth.speak(u)
    }
}

// MARK: - Fonts (system equivalents of the design's Google fonts)

private func serif(_ size: CGFloat, _ weight: Font.Weight = .medium, italic: Bool = false) -> Font {
    let f = Font.system(size: size, weight: weight, design: .serif)
    return italic ? f.italic() : f
}
private func mono(_ size: CGFloat, _ weight: Font.Weight = .regular) -> Font {
    .system(size: size, weight: weight, design: .monospaced)
}

// Accent-washed background (color-mix(accent 13%, surface)).
private struct AccentWash: View {
    let palette: Palette; let accent: Color
    var body: some View { ZStack { palette.surface; accent.opacity(0.13) } }
}

// MARK: - Root: custom tab container + theming

enum Tab { case today, saved, settings }

struct RootView: View {
    @StateObject private var settings = AppSettings.shared
    @Environment(\.colorScheme) private var scheme
    @State private var tab: Tab = .today
    @State private var searchOpen = false
    private let words = WordStore.words()

    private var resolved: ResolvedTheme {
        ResolvedTheme(palette: settings.palette(for: scheme), accent: settings.accent.color)
    }
    // Today is the single word page; it shows the current word of the day.
    private var todayIndex: Int { WordStore.index(at: Date(), in: words) }

    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch tab {
                case .today:    TodayView(word: words[todayIndex], index: todayIndex, onSearch: { searchOpen = true })
                case .saved:    SavedView()
                case .settings: SettingsView()
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)

            BottomBar(tab: $tab)
        }
        .background(resolved.palette.bg.ignoresSafeArea())
        .environment(\.theme, resolved)
        .environmentObject(settings)
        .preferredColorScheme(settings.preferredScheme)
        .tint(resolved.accent)
        .sheet(isPresented: $searchOpen) {
            SearchView()
                .environmentObject(settings)
                .environment(\.theme, resolved)
                .preferredColorScheme(settings.preferredScheme)
        }
        // Widget tap opens the app to Today (which is this word).
        .onOpenURL { url in
            if url.scheme == "smartwords", url.host == "word" { tab = .today }
        }
        .onChange(of: settings.notifications) { _, on in Notifier.apply(enabled: on) }
        .task { if settings.notifications { Notifier.apply(enabled: true) } }
    }
}

// MARK: - Search

struct SearchView: View {
    @Environment(\.theme) private var theme
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    private let words = WordStore.words()

    private var results: [Int] {
        let q = query.lowercased().trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return [] }
        // Prefix matches first, then other word matches, then definition matches.
        let scored = words.indices.compactMap { i -> (Int, Int)? in
            let w = words[i].word.lowercased()
            if w.hasPrefix(q) { return (i, 0) }
            if w.contains(q) { return (i, 1) }
            if words[i].definition.lowercased().contains(q) { return (i, 2) }
            return nil
        }
        return scored.sorted { $0.1 < $1.1 }.prefix(60).map { $0.0 }
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                HStack(spacing: 10) {
                    Image(systemName: "magnifyingglass").foregroundStyle(theme.palette.muted)
                    TextField("Search words", text: $query)
                        .font(.system(size: 16)).foregroundStyle(theme.palette.fg)
                        .autocorrectionDisabled().textInputAutocapitalization(.never)
                    if !query.isEmpty {
                        Button { query = "" } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(theme.palette.muted)
                        }.buttonStyle(.plain)
                    }
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 12).fill(theme.palette.surface)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(theme.palette.line, lineWidth: 1)))
                .padding(.horizontal, 20).padding(.top, 8)

                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        ForEach(results, id: \.self) { i in
                            NavigationLink(value: i) { resultRow(words[i]) }.buttonStyle(.plain)
                            Rectangle().fill(theme.palette.line).frame(height: 1).padding(.leading, 20)
                        }
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(theme.palette.bg)
            .navigationDestination(for: Int.self) { i in
                TodayView(word: words[i], index: i, isToday: false)
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Cancel") { dismiss() }.tint(theme.accent)
                }
            }
            .navigationTitle("Search").navigationBarTitleDisplayMode(.inline)
        }
    }

    private func resultRow(_ w: Word) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(w.word).font(serif(19, .semibold)).foregroundStyle(theme.palette.fg)
            Text(w.short ?? w.definition).font(.system(size: 13)).foregroundStyle(theme.palette.muted)
                .lineLimit(1)
        }
        .padding(.vertical, 12).padding(.horizontal, 20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .contentShape(Rectangle())
    }
}

// MARK: - Bottom tab bar (text labels, per design)

struct BottomBar: View {
    @Binding var tab: Tab
    @Environment(\.theme) private var theme

    private func item(_ t: Tab, _ label: String) -> some View {
        Button { tab = t } label: {
            Text(label)
                .font(mono(10, .medium)).tracking(1.3)
                .foregroundStyle(tab == t ? theme.accent : theme.palette.muted)
                .frame(maxWidth: .infinity)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    var body: some View {
        VStack(spacing: 0) {
            Rectangle().fill(theme.palette.line).frame(height: 1)
            HStack {
                item(.today, "TODAY")
                item(.saved, "SAVED")
                item(.settings, "SETTINGS")
            }
            .padding(.top, 14)
        }
        .background(theme.palette.bg)
    }
}

// MARK: - Chip

private struct Chip: View {
    let text: String
    var dim: Bool = false
    @Environment(\.theme) private var theme
    var body: some View {
        Text(text)
            .font(.system(size: 12.5))
            .foregroundStyle(theme.palette.fg)
            .opacity(dim ? 0.7 : 1)
            .padding(.vertical, 6).padding(.horizontal, 13)
            .overlay(Capsule().stroke(theme.palette.line2, lineWidth: 1))
    }
}

private struct SectionLabel: View {
    let text: String
    @Environment(\.theme) private var theme
    var body: some View {
        Text(text).font(mono(10)).tracking(1.6).foregroundStyle(theme.palette.muted)
    }
}

// MARK: - Today

// The single word page: Today's highlights (accent bar, bottom example panel)
// merged with the full detail content (definition, synonyms/antonyms, origin).
struct TodayView: View {
    let word: Word
    let index: Int
    var isToday: Bool = true            // false when reused to show a browsed/searched word
    var onSearch: (() -> Void)? = nil   // shows a search button when provided
    @Environment(\.theme) private var theme
    @EnvironmentObject private var settings: AppSettings

    private var dateLine: String {
        let f = DateFormatter(); f.dateFormat = "MMMM yyyy"
        return f.string(from: Date()).uppercased()
    }
    private var dayCount: String {
        let d = Calendar.current.ordinality(of: .day, in: .year, for: Date()) ?? 1
        return "\(d) / 365"
    }
    private var hasSynAnt: Bool {
        !(word.synonyms ?? []).isEmpty || !(word.antonyms ?? []).isEmpty
    }
    private var divider: some View {
        Rectangle().fill(theme.palette.line).frame(height: 1).padding(.vertical, 22)
    }

    var body: some View {
        GeometryReader { geo in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 14) {
                        if isToday {
                            Text(dateLine).font(mono(10.5)).tracking(1.2).foregroundStyle(theme.palette.muted)
                            Spacer()
                            Text(dayCount).font(mono(10.5)).tracking(1.2).foregroundStyle(theme.accent)
                        } else {
                            Spacer()
                        }
                        if let onSearch {
                            Button { onSearch() } label: {
                                Image(systemName: "magnifyingglass").font(.system(size: 15))
                                    .foregroundStyle(theme.palette.muted)
                            }
                            .buttonStyle(.plain).accessibilityLabel("Search words")
                        }
                        Button { settings.toggleSaved(index) } label: {
                            Image(systemName: settings.isSaved(index) ? "bookmark.fill" : "bookmark")
                                .font(.system(size: 15))
                                .foregroundStyle(settings.isSaved(index) ? theme.accent : theme.palette.muted)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(settings.isSaved(index) ? "Saved" : "Save word")
                    }
                    .padding(.top, 8)

                    // Hero word + pronounce + accent bar + pos/ipa (Today highlight)
                    HStack(alignment: .center, spacing: 12) {
                        Text(word.word)
                            .font(serif(60, .medium)).tracking(-1.5)
                            .lineLimit(1).minimumScaleFactor(0.5)
                            .foregroundStyle(theme.palette.fg)
                        Spacer(minLength: 0)
                        Button { Speaker.speak(word.word) } label: {
                            Circle().fill(theme.accent).frame(width: 42, height: 42)
                                .overlay(Image(systemName: "play.fill")
                                    .font(.system(size: 14)).foregroundStyle(.white).offset(x: 1))
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Pronounce \(word.word)")
                    }
                    .padding(.top, 26)
                    RoundedRectangle(cornerRadius: 3).fill(theme.accent)
                        .frame(height: 4).padding(.top, 16)
                    HStack {
                        Text((word.pos ?? "").uppercased()).font(mono(11)).tracking(1.5)
                        Spacer()
                        if let ipa = word.ipa, !ipa.isEmpty { Text(ipa).font(mono(11)) }
                    }
                    .foregroundStyle(theme.palette.muted).padding(.top, 12)

                    // Definition (detail content)
                    Text(word.definition)
                        .font(.system(size: 16.5)).lineSpacing(4)
                        .foregroundStyle(theme.palette.fg).padding(.top, 22)

                    // Synonyms / antonyms
                    if hasSynAnt {
                        HStack(alignment: .top, spacing: 26) {
                            if let syn = word.synonyms, !syn.isEmpty {
                                VStack(alignment: .leading, spacing: 11) {
                                    SectionLabel(text: "SYNONYMS"); FlowChips(items: syn)
                                }.frame(maxWidth: .infinity, alignment: .leading)
                            }
                            if let ant = word.antonyms, !ant.isEmpty {
                                VStack(alignment: .leading, spacing: 11) {
                                    SectionLabel(text: "ANTONYMS"); FlowChips(items: ant, dim: true)
                                }.frame(maxWidth: .infinity, alignment: .leading)
                            }
                        }.padding(.top, 26)
                    }

                    // Origin
                    if let origin = word.origin, !origin.isEmpty {
                        divider
                        SectionLabel(text: "ORIGIN")
                        Text(origin).font(.system(size: 14)).lineSpacing(4)
                            .foregroundStyle(theme.palette.muted).padding(.top, 10)
                    }

                    // Push the example panel to the bottom when content is short.
                    Spacer(minLength: 28)

                    // Example panel (Today highlight) — full-bleed accent wash
                    if let ex = word.example, !ex.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("\u{201C}").font(serif(46)).foregroundStyle(theme.accent)
                                .frame(height: 22, alignment: .top)
                            Text(ex).font(serif(17, italic: true)).lineSpacing(3)
                                .foregroundStyle(theme.palette.fg)
                        }
                        .padding(.horizontal, 26).padding(.top, 20).padding(.bottom, 22)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(AccentWash(palette: theme.palette, accent: theme.accent))
                        .padding(.horizontal, -26)
                    }
                }
                .padding(.horizontal, 26)
                .frame(minHeight: geo.size.height, alignment: .top)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.palette.bg)
    }
}

// Simple wrapping chip row.
private struct FlowChips: View {
    let items: [String]
    var dim: Bool = false
    var body: some View {
        // ponytail: small fixed-wrap via HStacks is overkill; use a lazy wrap.
        WrapHStack(items: items) { Chip(text: $0, dim: dim) }
    }
}

// Minimal flow layout (iOS 16+: use Layout).
private struct WrapHStack<Content: View>: View {
    let items: [String]
    let content: (String) -> Content
    var body: some View {
        FlowLayout(spacing: 8) { ForEach(items, id: \.self) { content($0) } }
    }
}

struct FlowLayout: Layout {
    var spacing: CGFloat = 8
    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxW = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > maxW { x = 0; y += rowH + spacing; rowH = 0 }
            x += sz.width + spacing; rowH = max(rowH, sz.height)
        }
        return CGSize(width: maxW == .infinity ? x : maxW, height: y + rowH)
    }
    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let maxW = bounds.width
        var x: CGFloat = 0, y: CGFloat = 0, rowH: CGFloat = 0
        for s in subviews {
            let sz = s.sizeThatFits(.unspecified)
            if x + sz.width > maxW { x = 0; y += rowH + spacing; rowH = 0 }
            s.place(at: CGPoint(x: bounds.minX + x, y: bounds.minY + y), proposal: .unspecified)
            x += sz.width + spacing; rowH = max(rowH, sz.height)
        }
    }
}

// MARK: - Settings

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.theme) private var theme
    private let previewWord = WordStore.word(at: Date(), in: WordStore.words())

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Settings").font(serif(32, .medium)).tracking(-0.6)
                    .foregroundStyle(theme.palette.fg).padding(.top, 6)

                SectionLabel(text: "APPEARANCE").padding(.top, 30)
                segmented.padding(.top, 12)

                SectionLabel(text: "ACCENT COLOR").padding(.top, 30)
                HStack {
                    Text("Selected").font(.system(size: 14)).foregroundStyle(theme.palette.muted)
                    Spacer()
                    Text(settings.accent.name).font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(theme.accent)
                }.padding(.top, 8)
                HStack {
                    ForEach(Accents.all) { a in swatch(a) }
                }.padding(.top, 15).frame(maxWidth: .infinity)

                SectionLabel(text: "DAILY WORD").padding(.top, 30)
                notifRow
                Rectangle().fill(theme.palette.line).frame(height: 1)
                cycleRow
                Rectangle().fill(theme.palette.line).frame(height: 1)
                widgetRow
            }
            .padding(.horizontal, 26).padding(.bottom, 20)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.palette.bg)
    }

    // Light / Dark / Auto
    private var segmented: some View {
        HStack(spacing: 4) {
            seg("Light", .light); seg("Dark", .dark); seg("Auto", .auto)
        }
        .padding(4)
        .background(RoundedRectangle(cornerRadius: 13).fill(theme.palette.line))
    }
    private func seg(_ label: String, _ mode: ThemeMode) -> some View {
        let on = settings.mode == mode
        return Button { withAnimation(.easeOut(duration: 0.15)) { settings.mode = mode } } label: {
            Text(label)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(on ? theme.palette.fg : theme.palette.muted)
                .frame(maxWidth: .infinity).padding(.vertical, 10)
                .background(RoundedRectangle(cornerRadius: 10).fill(on ? theme.palette.surface : .clear)
                    .shadow(color: .black.opacity(on ? 0.14 : 0), radius: 3, y: 1))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private func swatch(_ a: Accent) -> some View {
        let on = settings.accentID == a.id
        return Button { settings.accentID = a.id } label: {
            Circle().fill(a.color).frame(width: 40, height: 40)
                .overlay(Circle().stroke(theme.palette.bg, lineWidth: on ? 2.5 : 0))
                .overlay(Circle().stroke(on ? a.color : .clear, lineWidth: 2).padding(-2.5))
                .frame(maxWidth: .infinity)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(a.name)
    }

    private var notifRow: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text("Daily notification").font(.system(size: 15, weight: .medium))
                    .foregroundStyle(theme.palette.fg)
                Text("Delivered at 9:00 AM").font(.system(size: 12.5)).foregroundStyle(theme.palette.muted)
            }
            Spacer()
            ToggleSwitch(on: $settings.notifications)
        }.padding(.vertical, 15)
    }

    // Keeps the working rotation control (design's DAILY WORD section).
    private var cycleRow: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text("New word").font(.system(size: 15, weight: .medium)).foregroundStyle(theme.palette.fg)
                Text("How often the widget advances").font(.system(size: 12.5)).foregroundStyle(theme.palette.muted)
            }
            Spacer()
            Menu {
                ForEach([1, 3, 6, 12, 24], id: \.self) { h in
                    Button(h == 24 ? "Daily" : "\(h)h") { WordStore.setRotationHours(h); WidgetCenter.shared.reloadAllTimelines() }
                }
            } label: {
                Text(WordStore.rotationHours == 24 ? "Daily" : "\(WordStore.rotationHours)h")
                    .font(.system(size: 14, weight: .semibold)).foregroundStyle(theme.accent)
            }
        }.padding(.vertical, 15)
    }

    private var widgetRow: some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text("Widgets").font(.system(size: 15, weight: .medium)).foregroundStyle(theme.palette.fg)
                Text("Small & Medium · on Home").font(.system(size: 12.5)).foregroundStyle(theme.palette.muted)
            }
            Spacer()
            VStack(alignment: .leading, spacing: 4) {
                Text(previewWord.word).font(serif(14, .semibold)).lineLimit(1).minimumScaleFactor(0.5)
                    .foregroundStyle(theme.palette.fg)
                Text((previewWord.pos ?? "word").uppercased()).font(mono(7)).tracking(0.8)
                    .foregroundStyle(theme.accent)
            }
            .padding(.horizontal, 11).frame(width: 78, height: 48, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 12).fill(theme.palette.surface))
            .overlay(RoundedRectangle(cornerRadius: 12).fill(theme.accent.opacity(0.10)))
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(theme.palette.line, lineWidth: 1))
        }.padding(.vertical, 15)
    }
}

private struct ToggleSwitch: View {
    @Binding var on: Bool
    @Environment(\.theme) private var theme
    var body: some View {
        Button { withAnimation(.easeOut(duration: 0.2)) { on.toggle() } } label: {
            ZStack(alignment: on ? .trailing : .leading) {
                Capsule().fill(on ? theme.accent : theme.palette.line2).frame(width: 46, height: 28)
                Circle().fill(.white).frame(width: 22, height: 22)
                    .shadow(color: .black.opacity(0.3), radius: 1.5, y: 1).padding(3)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Daily notification")
        .accessibilityValue(on ? "On" : "Off")
    }
}

// MARK: - Saved (placeholder — feature out of scope)

struct SavedView: View {
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.theme) private var theme
    private let words = WordStore.words()

    var body: some View {
        let saved = settings.savedIDs.filter { words.indices.contains($0) }
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Saved").font(serif(32, .medium)).tracking(-0.6)
                    .foregroundStyle(theme.palette.fg).padding(.top, 6).padding(.bottom, 8)

                if saved.isEmpty {
                    Text("Words you save will appear here.")
                        .font(.system(size: 14)).foregroundStyle(theme.palette.muted).padding(.top, 14)
                } else {
                    ForEach(saved, id: \.self) { i in
                        savedRow(words[i], index: i)
                        Rectangle().fill(theme.palette.line).frame(height: 1)
                    }
                }
            }
            .padding(.horizontal, 26).padding(.bottom, 20)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.palette.bg)
    }

    private func savedRow(_ w: Word, index: Int) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: 12) {
            VStack(alignment: .leading, spacing: 5) {
                Text(w.word).font(serif(20, .semibold)).foregroundStyle(theme.palette.fg)
                HStack(spacing: 8) {
                    if let pos = w.pos, !pos.isEmpty {
                        Text(pos.uppercased()).font(mono(9.5)).tracking(1.2).foregroundStyle(theme.accent)
                    }
                    if let s = w.short, !s.isEmpty {
                        Text(s).font(.system(size: 13)).foregroundStyle(theme.palette.muted)
                    }
                }
            }
            Spacer()
            Button { settings.toggleSaved(index) } label: {
                Image(systemName: "xmark").font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(theme.palette.muted)
            }
            .buttonStyle(.plain).accessibilityLabel("Remove \(w.word)")
        }
        .padding(.vertical, 14)
    }
}
