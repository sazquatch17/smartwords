// Lexica — word-of-the-day app. Today / Word Detail / Settings with live theming.
// Model/store/theme are shared with the widget (Shared/).

import SwiftUI
import WidgetKit

@main
struct SmartWordsApp: App {
    var body: some Scene { WindowGroup { RootView() } }
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
    private let words = WordStore.words()

    private var resolved: ResolvedTheme {
        ResolvedTheme(palette: settings.palette(for: scheme), accent: settings.accent.color)
    }
    // Today is the single word page; it shows the current word of the day.
    private var todayWord: Word { words[WordStore.index(at: Date(), in: words)] }

    var body: some View {
        VStack(spacing: 0) {
            Group {
                switch tab {
                case .today:    TodayView(word: todayWord)
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
        // Widget tap opens the app to Today (which is this word).
        .onOpenURL { url in
            if url.scheme == "smartwords", url.host == "word" { tab = .today }
        }
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
    @Environment(\.theme) private var theme

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
                    HStack {
                        Text(dateLine).font(mono(10.5)).tracking(1.2).foregroundStyle(theme.palette.muted)
                        Spacer()
                        Text(dayCount).font(mono(10.5)).tracking(1.2).foregroundStyle(theme.accent)
                    }
                    .padding(.top, 8)

                    // Hero word + accent bar + pos/ipa (Today highlight)
                    Text(word.word)
                        .font(serif(60, .medium)).tracking(-1.5)
                        .lineLimit(1).minimumScaleFactor(0.5)
                        .foregroundStyle(theme.palette.fg)
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
    @Environment(\.theme) private var theme
    var body: some View {
        VStack(spacing: 10) {
            Text("Saved").font(serif(32, .medium)).foregroundStyle(theme.palette.fg)
            Text("Words you save will appear here.")
                .font(.system(size: 14)).foregroundStyle(theme.palette.muted)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.palette.bg)
    }
}
