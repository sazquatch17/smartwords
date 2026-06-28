# Design fonts

The Lexica design uses three Google Fonts (SIL Open Font License 1.1). The
**variable** font files are vendored per platform:

- iOS: `ios/App/Fonts/*.ttf` (registered via `UIAppFonts` in `project.yml`)
- Android: `android/app/src/main/res/font/*.ttf`

| Role  | Family            | Source (google/fonts, `main`) |
|-------|-------------------|-------------------------------|
| serif | Newsreader        | `ofl/newsreader/Newsreader[opsz,wght].ttf` + `Newsreader-Italic[opsz,wght].ttf` |
| sans  | Hanken Grotesk    | `ofl/hankengrotesk/HankenGrotesk[wght].ttf` |
| mono  | Spline Sans Mono  | `ofl/splinesansmono/SplineSansMono[wght].ttf` |

To refresh, re-download from `https://github.com/google/fonts/raw/main/<path>`
and overwrite the per-platform copies (iOS keeps CamelCase names, Android uses
lowercase `snake_case` to satisfy resource naming).

The Glance home-screen widget keeps system fonts — Glance 1.1 has no custom-font API.
