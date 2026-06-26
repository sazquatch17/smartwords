// Minimal host app. Widgets need a containing app; the app itself does little in
// v1 beyond hosting the widget (and, later, the daily background fetch).

import SwiftUI

@main
struct SmartWordsApp: App {
    var body: some Scene {
        WindowGroup {
            VStack(spacing: 12) {
                Text("SmartWords")
                    .font(.system(.largeTitle, design: .serif).weight(.semibold))
                Text("Add the SmartWords widget to your Home Screen or Lock Screen.")
                    .font(.system(.body, design: .serif))
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding()
        }
    }
}
