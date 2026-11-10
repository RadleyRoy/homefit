# HomeFit

A personal Android home-workout app: body-focus workouts at three levels, 28/30-day challenges,
recovery plans, HIIT, stretching, running intervals, a voice coach, progress tracking, reminders,
a rule-based personal plan and an optional AI coach. No accounts, no ads; data stays on the phone.

Kotlin + Jetpack Compose, Room, DataStore, Health Connect, Glance. Dark-only "Deep Teal" theme.

## Build and install

Needs JDK 17 and the Android SDK (API 36). Android Studio is optional; the Gradle wrapper is enough.

```sh
./gradlew installDebug          # phone connected over USB with USB debugging on
./gradlew testDebugUnitTest     # unit tests (engine, plan generator, content validation…)
./gradlew connectedDebugAndroidTest   # repository tests against a real Room DB, on the phone
./gradlew assembleRelease       # R8-shrunk APK in app/build/outputs/apk/release/
```

Release signing reads `HOMEFIT_KEYSTORE_PATH`, `HOMEFIT_KEYSTORE_PASSWORD`, `HOMEFIT_KEY_ALIAS` and
`HOMEFIT_KEY_PASSWORD`; without them the release APK is signed with the debug key.

## Content

Everything a workout is made of lives in `app/src/main/assets/content/`:

| File | Written by | What |
|---|---|---|
| `exercises.json` + `images/` | `node tools/import-exercises.mjs` | Generated from `tools/catalog.mjs` |
| `workouts.json` | hand | Workouts as `"exercise_id x12"` / `"exercise_id 30s"` lines |
| `programs.json` | hand | Challenges and recovery plans: templates, rest days, progression |

To add or change an exercise, edit `tools/catalog.mjs` and re-run the importer; it fetches
[free-exercise-db](https://github.com/yuhonas/free-exercise-db) (public domain) for instructions
and photos, validates every link, and only downloads photos it doesn't have yet.
`BundledContentTest` fails the build if a workout references a missing exercise or photo.

## AI coach

Optional. Add an Anthropic API key under Me → AI coach → settings; it's encrypted with an Android
Keystore-backed key and never included in backups. Usage is billed to that key.
