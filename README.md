# Daymark

A calm, private, offline-first app for building good habits and breaking unwanted ones, for Android.
Every day you mark is a day you made. A slip never erases what you've built.

## Features

- **Build a habit:** water, reading, gym, meditation and more. Daily amount goals or check-offs, weekly schedules, reminders every 30–180 minutes within active hours or at set times (log, snooze or skip from the notification), streaks, completion rate and weekly history.
- **Multiple trackers:** follow several habits at once, such as alcohol, smoking or doomscrolling. Each has its own start date, streaks, calendar, insights and achievements, and one tap switches between them.
- **Today dashboard:** a live streak counter (days, plus ticking hours, minutes and seconds), a progress ring toward the next milestone, your longest streak, total clean days and a daily encouragement.
- **Achievements:** 12 milestones, from 24 hours to 5 years. They're earned against your *longest* streak, so they're never lost after a relapse.
- **Calendar:** a swipeable month view. Clean days are green, slips are red and untracked days are grey. Tap a day for details, or to log or edit a slip.
- **Quick slip logging:** date, time, mood (a 5-point scale), triggers (multi-select plus your own), situation and notes. Entries can be edited or deleted, and a delete can be undone.
- **Urge log:** "Feeling an urge? Ride it out" records a craving you got through without slipping: its strength, triggers and what helped. Your own reasons are shown first.
- **Journal:** write on any day, with a mood, a gentle daily prompt and up to 3 photos. Photos stay inside Daymark (never added to your gallery) and are re-encoded, which removes location and other camera metadata.
- **Your reasons:** list why you're doing this. They rotate on the dashboard, appear on the urge screen and, if you choose, on the motivation widget.
- **Money kept:** add what a habit used to cost per week or month, and see what you've kept.
- **Cut-down mode:** set a daily limit instead of quitting outright. Only days over the limit count as a slip.
- **Reminders:** an optional daily check-in and milestone celebrations, as local notifications.
- **Quick access:** a Quick Settings tile and launcher shortcuts (urge, journal, log).
- **Share a milestone:** an image drawn on your phone, shared only where you choose.
- **Insights:** streak history, slips per month, common triggers, time of day and weekday patterns, mood before a slip, situations, and a supportive trend summary.
- **Home-screen widgets:** six types:
  - **Streak:** a live ticking counter (an embedded system Chronometer); "Today 1 of 2" in cut-down mode
  - **Progress ring:** fills toward the next milestone
  - **Heatmap:** your recent weeks of clean and slip days
  - **Daily motivation:** your own reasons, or a gentle message; tap for another
  - **Quick actions:** ride out an urge, journal or log in one tap
  - **Your progress:** money kept, urges ridden out, clean days and best streak

  There are six styles (Dusk, Dawn, Forest, Midnight, Sunrise, Minimal), and each widget can show any tracker. Pick them in the in-app gallery (*Settings → Widgets*) or from the launcher.
- **Events:** countdowns, time-since and timers as cards in five designs (Text, Ring, Grid, Bars, Minimal) with ten fonts, a home-screen Event widget (full, translucent or transparent background, plus an upcoming queue), optional notifications, built-in Today / Week / Month / Year progress, about 60 ideas with a first-run wizard, and an optional one-time import from the device calendar (`READ_CALENDAR`, asked only when you tap the import row).
- **Privacy:**
  - There is no INTERNET permission, no account and no analytics. The only extra permission is the optional, user-initiated calendar read.
  - Optional app lock uses biometrics or the device credential.
  - Optional "hide in recents" (FLAG_SECURE) blanks the app preview and blocks screenshots.
  - **Discreet mode** (on by default): widgets, notifications, the Quick Settings tile and share cards never name what you're tracking.
  - Android auto-backup is disabled.
- **Backup and restore:** export to, and import from, a JSON file that you control, optionally protected with a password (AES-256-GCM). Journal photos are included unless you turn that off. Optional automatic daily backups go to a folder you pick, keeping the last 7.
- **Themes:** light, dark or system, eight accent colours (Dusk indigo by default), or Material You wallpaper colours. Motion uses spring physics, instant press feedback and haptics, and falls back to fades when animations are turned off system-wide.

## Install

Download `Daymark-vX.Y.Z.apk` from the [Releases](../../releases) page and open it on your phone. You may need to allow *Install unknown apps* for your browser or file manager. Android 8.0 or newer is required.

## Maintaining

- **[docs/MAINTAINING.md](docs/MAINTAINING.md):** step-by-step guides (ship an update, test on emulator or phone, library updates, key safety).
- **[CLAUDE.md](CLAUDE.md):** architecture, rules and recipes. Claude Code reads it automatically.
- **Release:** run `scripts/release.sh X.Y.Z`. GitHub Actions then builds, signs and publishes the release.

## Distribution

Daymark is distributed through AppDrop (listing in `appdrop.json` and `appdrop/`, APKs from GitHub Releases). The privacy policy is in [docs/privacy-policy.md](docs/privacy-policy.md).

## Tech

Kotlin 2.1 · Jetpack Compose (Material 3) · Room · DataStore · Glance app widget · BiometricPrompt · kotlinx.serialization.
It's a single module with manual DI. All streak, milestone and insight maths lives in pure Kotlin (`domain/`), and unit tests cover it.

```
app/src/main/java/com/daymark/app/
├── domain/   StreakEngine, Milestones, InsightsEngine, Messages (pure, tested)
├── data/     Room DB, DataStore settings, JSON backup codec (+ encryption), auto-backup, repositories
├── notify/   local reminders, Quick Settings tile, deep-link routes
├── ui/       Compose screens: dashboard, calendar, log, urge, journal, insights, settings, onboarding, lock
└── widget/   Glance home-screen widgets
```

## Build

```bash
./gradlew testDebugUnitTest assembleDebug   # debug APK
./gradlew assembleRelease                   # minified release APK
```

Release signing reads `DAYMARK_STORE_FILE`, `DAYMARK_STORE_PASSWORD`, `DAYMARK_KEY_ALIAS` and `DAYMARK_KEY_PASSWORD` from `~/.gradle/gradle.properties`. If they're absent, it falls back to the debug key.
