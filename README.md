# SmartWords

Native homescreen & lockscreen widgets showing a dictionary word, its part of
speech, and its meaning. Built to sip battery: **one network call per day**, the
widget just rotates through a locally cached batch.

See [SPEC.md](./SPEC.md) for the full design.

## Decisions (v1)

- Native on both platforms — iOS (Swift/WidgetKit/SwiftUI), Android (Kotlin/Glance).
- Data: tiny bundled seed + once-a-day background fetch of a static JSON batch,
  cached locally; the widget rotates through the cache every ~3 hours.
- Shows: word + part of speech + definition.
- Editorial / serif look (native serif fonts, no bundling).
- Lockscreen: iOS yes; Android homescreen only (no lockscreen widgets on Android).

## Layout

```
smartwords/
├── SPEC.md                       full spec
├── scripts/
│   ├── scrape_seed.py            builds seed-words.json from the Word Warriors list
│   └── fill_pos.py               backfills part-of-speech via dictionaryapi.dev
├── shared/
│   ├── seed-words.json           bundled seed (831 words, ships in both apps)
│   └── daily-batch.example.json  shape of the per-day fetched batch
├── ios/
│   ├── project.yml               XcodeGen spec — generates SmartWords.xcodeproj
│   ├── App/SmartWordsApp.swift   minimal host app
│   └── Widget/SmartWordsWidget.swift   WidgetKit widget (rotation, seed fallback)
└── android/
    └── SmartWordsWidget.kt       reference widget impl (drop into Android project)
```

iOS is **project-as-code**: the `.xcodeproj` is generated from `project.yml` by
XcodeGen and gitignored, so there are no hand-clicked targets to drift. Android
is still a reference file pending its M2 project.

## Getting started

### iOS (M1 — builds today)
Needs Xcode + XcodeGen (`brew install xcodegen`).

```sh
xcodegen generate --spec ios/project.yml     # creates ios/SmartWords.xcodeproj
open ios/SmartWords.xcodeproj                 # run on a simulator/device
```

Headless build check (no simulator needed):

```sh
xcodebuild -project ios/SmartWords.xcodeproj -scheme SmartWords \
  -sdk iphonesimulator26.5 -arch arm64 CODE_SIGNING_ALLOWED=NO build
```

Renders the seed list with rotation. App Group + daily fetch come in M3.
(If a sim won't launch with "CoreSimulator is out of date", reboot.)

### Android (M2)
1. Android Studio → new project `SmartWords` (Empty, Kotlin, Compose).
2. Add Glance: `androidx.glance:glance-appwidget` + `androidx.work:work-runtime-ktx`.
3. Add `android/SmartWordsWidget.kt`; register the receiver in the manifest;
   put `shared/seed-words.json` in `assets/`.
4. Run — homescreen widget renders the seed list with rotation. Network in M3.
