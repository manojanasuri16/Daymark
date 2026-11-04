# Daymark

A calm, private, offline-first habit recovery and streak tracker for Android.
Every day you mark is a day you made. A slip never erases what you've built.

## Features

- **Multiple trackers:** follow several habits at once, such as alcohol, smoking or doomscrolling. Each has its own start date, streaks, calendar, insights and achievements, and one tap switches between them.
- **Today dashboard:** a live streak counter (days, plus ticking hours, minutes and seconds), a progress ring toward the next milestone, your longest streak, total clean days and a daily encouragement.
- **Achievements:** 12 milestones, from 24 hours to 5 years. They're earned against your *longest* streak, so they're never lost after a relapse.
- **Calendar:** a swipeable month view. Clean days are green, slips are red and untracked days are grey. Tap a day for details, or to log or edit a slip.
- **Quick slip logging:** date, time, mood (a 5-point scale), triggers (multi-select plus your own), situation and notes. Entries can be edited or deleted, and a delete can be undone.
- **Insights:** streak history, slips per month, common triggers, time of day and weekday patterns, mood before a slip, situations, and a supportive trend summary.
- **Home-screen widgets:** four types:
  - **Streak:** a live ticking counter (an embedded system Chronometer)
  - **Progress ring:** fills toward the next milestone
  - **Heatmap:** your recent weeks of clean and slip days
  - **Daily motivation:** tap it for another message

  There are six styles (Dusk, Dawn, Forest, Midnight, Sunrise, Minimal), and each widget can show any tracker. Pick them in the in-app gallery (*Settings → Widgets*) or from the launcher.
- **Privacy:**
  - There is no INTERNET permission, no account and no analytics.
  - Optional app lock uses biometrics or the device credential.
  - Optional "hide in recents" (FLAG_SECURE) blanks the app preview and blocks screenshots.
  - Android auto-backup is disabled.
- **Backup and restore:** export to, and import from, a JSON file that you control.
- **Themes:** light, dark or system, eight accent colours (Dusk indigo by default), or Material You wallpaper colours. Motion uses spring physics, instant press feedback and haptics, and falls back to fades when animations are turned off system-wide.

## Install

Download `Daymark-vX.Y.Z.apk` from the [Releases](../../releases) page and open it on your phone. You may need to allow *Install unknown apps* for your browser or file manager. Android 8.0 or newer is required.

## Google Play

See [docs/PLAY_STORE.md](docs/PLAY_STORE.md) for the publishing checklist. Store assets live in `fastlane/metadata/android/en-US/`, and the privacy policy is in [docs/privacy-policy.md](docs/privacy-policy.md).

## Tech

Kotlin 2.1 · Jetpack Compose (Material 3) · Room · DataStore · Glance app widget · BiometricPrompt · kotlinx.serialization.
It's a single module with manual DI. All streak, milestone and insight maths lives in pure Kotlin (`domain/`), and unit tests cover it.

```
app/src/main/java/com/daymark/app/
├── domain/   StreakEngine, Milestones, InsightsEngine, Messages (pure, tested)
├── data/     Room DB, DataStore settings, JSON backup codec, repositories
├── ui/       Compose screens: dashboard, calendar, log, insights, settings, onboarding, lock
└── widget/   Glance home-screen widget
```

## Build

```bash
./gradlew testDebugUnitTest assembleDebug   # debug APK
./gradlew assembleRelease                   # minified release APK
./gradlew bundleRelease                     # Play Store app bundle (.aab)
```

Release signing reads `DAYMARK_STORE_FILE`, `DAYMARK_STORE_PASSWORD`, `DAYMARK_KEY_ALIAS` and `DAYMARK_KEY_PASSWORD` from `~/.gradle/gradle.properties`. If they're absent, it falls back to the debug key.
