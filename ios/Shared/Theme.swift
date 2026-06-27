// Design system for the Lexica redesign — shared by the app and the widget.
// Theme (light/dark/auto) and accent are persisted in the App Group so changing
// them in the app recolors the widget live.

import SwiftUI
#if canImport(WidgetKit)
import WidgetKit
#endif

// MARK: - Color from hex

extension Color {
    init(hex: String) {
        let s = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        var v: UInt64 = 0
        Scanner(string: s).scanHexInt64(&v)
        let r, g, b: Double
        if s.count == 6 {
            r = Double((v >> 16) & 0xFF) / 255
            g = Double((v >> 8) & 0xFF) / 255
            b = Double(v & 0xFF) / 255
        } else { r = 0; g = 0; b = 0 }
        self = Color(red: r, green: g, blue: b)
    }
}

// MARK: - Accents

struct Accent: Identifiable, Hashable {
    let id: String
    let name: String
    let hex: String
    var color: Color { Color(hex: hex) }
}

enum Accents {
    static let all: [Accent] = [
        Accent(id: "amber",      name: "Amber", hex: "c87d2a"),
        Accent(id: "gold",       name: "Gold",  hex: "b8862a"),
        Accent(id: "coral",      name: "Coral", hex: "d96b52"),
        Accent(id: "terracotta", name: "Clay",  hex: "c25e3a"),
        Accent(id: "rose",       name: "Rose",  hex: "c25a73"),
        Accent(id: "plum",       name: "Plum",  hex: "a85a86"),
    ]
    static func by(_ id: String) -> Accent { all.first { $0.id == id } ?? all[0] }
}

// MARK: - Palette (per theme)

struct Palette {
    let bg, surface, fg, muted, line, line2: Color

    static let light = Palette(
        bg: Color(hex: "f6f3ee"), surface: .white, fg: Color(hex: "1b1714"),
        muted: Color(hex: "8c837b"),
        line: Color(hex: "1b1714").opacity(0.09), line2: Color(hex: "1b1714").opacity(0.17))

    static let dark = Palette(
        bg: Color(hex: "15120f"), surface: Color(hex: "1f1b17"), fg: Color(hex: "f1ece4"),
        muted: Color(hex: "9a9088"),
        line: Color.white.opacity(0.10), line2: Color.white.opacity(0.20))
}

enum ThemeMode: String, CaseIterable { case light, dark, auto }

// MARK: - Resolved theme passed through the environment

struct ResolvedTheme {
    let palette: Palette
    let accent: Color

    // Quote/widget tint: accent mixed into the surface (~13%).
    var accentWash: Color { accent.opacity(0.13) }
}

private struct ThemeKey: EnvironmentKey {
    static let defaultValue = ResolvedTheme(palette: .light, accent: Accents.all[0].color)
}
extension EnvironmentValues {
    var theme: ResolvedTheme {
        get { self[ThemeKey.self] }
        set { self[ThemeKey.self] = newValue }
    }
}

// MARK: - Settings (App Group, observable for live recolor)

final class AppSettings: ObservableObject {
    static let shared = AppSettings()
    private let store = UserDefaults(suiteName: WordStore.appGroup)

    @Published var mode: ThemeMode { didSet { store?.set(mode.rawValue, forKey: "theme"); reloadWidgets() } }
    @Published var accentID: String { didSet { store?.set(accentID, forKey: "accent"); reloadWidgets() } }
    @Published var notifications: Bool { didSet { store?.set(notifications, forKey: "notif") } }
    // Saved word indices (into WordStore.words()), most-recent first.
    @Published var savedIDs: [Int] { didSet { store?.set(savedIDs, forKey: "saved") } }

    init() {
        mode = ThemeMode(rawValue: UserDefaults(suiteName: WordStore.appGroup)?.string(forKey: "theme") ?? "") ?? .auto
        accentID = UserDefaults(suiteName: WordStore.appGroup)?.string(forKey: "accent") ?? "amber"
        notifications = UserDefaults(suiteName: WordStore.appGroup)?.object(forKey: "notif") as? Bool ?? true
        savedIDs = UserDefaults(suiteName: WordStore.appGroup)?.array(forKey: "saved") as? [Int] ?? []
    }

    var accent: Accent { Accents.by(accentID) }

    func isSaved(_ index: Int) -> Bool { savedIDs.contains(index) }
    func toggleSaved(_ index: Int) {
        if let i = savedIDs.firstIndex(of: index) { savedIDs.remove(at: i) }
        else { savedIDs.insert(index, at: 0) }
    }

    func palette(for scheme: ColorScheme) -> Palette {
        switch mode {
        case .light: return .light
        case .dark:  return .dark
        case .auto:  return scheme == .dark ? .dark : .light
        }
    }

    /// preferredColorScheme value: nil for auto (follow system).
    var preferredScheme: ColorScheme? {
        switch mode { case .light: return .light; case .dark: return .dark; case .auto: return nil }
    }

    private func reloadWidgets() {
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
    }

    // Static readers for the widget (no observation needed there).
    static func accent(from d: UserDefaults?) -> Accent { Accents.by(d?.string(forKey: "accent") ?? "amber") }
    static func mode(from d: UserDefaults?) -> ThemeMode { ThemeMode(rawValue: d?.string(forKey: "theme") ?? "") ?? .auto }
}
