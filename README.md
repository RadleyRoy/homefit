# HomeFit

A personal Android home-workout app: body-focus workouts at three levels, 28/30-day challenges,
recovery plans, HIIT, stretching, running intervals, a voice coach, progress tracking, reminders,
a rule-based personal plan and a Claude coach that runs on your own Claude plan. No accounts, no
ads, no internet permission; data stays on the phone.

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
`HOMEFIT_KEY_PASSWORD`; without them a local release APK is signed with the debug key.
`HOMEFIT_VERSION_NAME` (e.g. `1.2.3`) sets the version; the version code is derived from it.

## CI and releases

- **CI** (`.github/workflows/ci.yml`): every push to `master` and every PR runs the unit tests and
  lint, builds the debug APK and uploads it as a build artifact.
- **Release** (`.github/workflows/release.yml`): pushing a tag `vX.Y.Z` (or running the workflow by
  hand with a tag) tests, builds a release APK signed with the release key, verifies the
  signature and publishes it as a GitHub Release.

```sh
git tag v1.2.0 && git push origin v1.2.0
```

The release key lives outside the repo and reaches CI through four repository secrets:
`HOMEFIT_KEYSTORE_B64` (the `.jks`, base64), `HOMEFIT_KEYSTORE_PASSWORD`, `HOMEFIT_KEY_ALIAS` and
`HOMEFIT_KEY_PASSWORD`. Releases refuse to publish without them. Keep a backup of the keystore:
without it no future release can install over the current one.

## Content

Everything a workout is made of lives in `app/src/main/assets/content/`:

| File | Written by | What |
|---|---|---|
| `exercises.json` + `images/` | `node tools/import-exercises.mjs` | Generated from `tools/catalog.mjs` |
| `workouts.json` | hand | Workouts as `"exercise_id x12"` / `"exercise_id 30s"` lines |
| `programs.json` | hand | Challenges and recovery plans: templates, rest days, progression |

To add or change an exercise, edit `tools/catalog.mjs` (what the move is) and `tools/photos.mjs`
(which photos show it), then re-run the importer:

```sh
node tools/import-exercises.mjs
```

Photos come from three sources, all free to bundle:

- [free-exercise-db](https://github.com/yuhonas/free-exercise-db): public domain, two frames per move.
- [Wikimedia Commons](https://commons.wikimedia.org): public domain, CC BY or CC BY-SA files;
  author and licence are read from the Commons API.
- [Pexels](https://www.pexels.com/license/): free to use; mostly for workout and plan covers.

The importer writes `credits.json` (shown under Me → About → Photo credits) and
`tools/photo-sources.json`, only downloads what changed, and deletes photos nothing uses. Covers
live in `covers/<key>.jpg`, looked up by workout or program id, then `focus_<AREA>`, then
`category_<CATEGORY>`. Moves without a photo show a "Watch how on YouTube" panel instead; every
exercise sheet also links to a YouTube search for the move.

`BundledContentTest` fails the build if a workout references a missing exercise or photo, if a
program or focus area has no cover, or if a photo has no recorded source or credit.

## Coach

The coach doesn't call any API. It builds a prompt from your profile, recent sessions and the
exercise catalog and hands it to the Claude app (or claude.ai), so it runs on your own Claude plan.
Ask a question, have Claude design a workout, plan your week or review your progress; when Claude
replies with a workout, share the reply back to HomeFit (or copy it and tap Paste) and it is saved
under Discover → My workouts. Replies are read from ```` ```homefit ```` blocks by `CoachImport`.
