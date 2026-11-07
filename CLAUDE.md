# Daymark: guide for Claude (and humans)

Daymark is a private, offline-first Android app for habit recovery and streak tracking. It has one module (`:app`), package `com.daymark.app`, and is written in Kotlin with Jetpack Compose (Material 3), Room, DataStore and Glance widgets. A human-friendly runbook is in `docs/MAINTAINING.md`, and Play Store steps are in `docs/PLAY_STORE.md`.

## Non-negotiables

- **Privacy:** never add the `INTERNET` permission, analytics, crash SDKs, ads or accounts. Data lives only on the device. The store listing, privacy policy and Data-safety form all promise this.
- **Tone:** say "slip", never "failure" or "relapse" in UI copy. Keep it supportive and non-judgmental. A slip must never erase history: longest streak, total clean days and achievements are always kept.
- **Never change** `applicationId` (`com.daymark.app`) or the signing key. Doing so would break every user's updates.
- **`versionCode` must go up with every release** (`scripts/release.sh` does this).
- **Never rename widget receivers.** `DaymarkWidgetReceiver`, `RingWidgetReceiver`, `HeatmapWidgetReceiver`, `QuoteWidgetReceiver`, `ActionsWidgetReceiver` and `StatsWidgetReceiver` are referenced by widgets already placed on users' home screens.
- **Every Room schema change needs a migration** (see "Recipes"). Losing user data is the worst possible bug.

## Git

- Commit as **Manoj Anasuri <manojanasuri16@gmail.com>**, which is already set in this repo's git config. GitHub user: `manojanasuri16`.
- **Don't add AI co-author trailers** (no `Co-Authored-By: Claude …`) to commits, and no "Generated with Claude Code" lines in PRs.

## Commands

```bash
export ANDROID_HOME=~/android-sdk          # SDK lives here (not in PATH)
./gradlew testDebugUnitTest                # unit tests (pure Kotlin: domain + backup)
./gradlew lintRelease                      # must have 0 errors (the release CI runs it)
./gradlew assembleDebug                    # debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease bundleRelease    # signed release APK + Play AAB
scripts/emulator.sh [release]              # boot headless emulator + install + launch
scripts/tap.sh "Text on screen" [n]        # tap UI by text (uiautomator)
adb shell am broadcast -n com.daymark.app/.DemoSeedReceiver   # debug only: 3 demo trackers
scripts/release.sh 1.2.0                   # bump, tag, push → CI builds, signs, publishes
```

Builds are slow on this machine (5–10 min, longer while the emulator runs). Run long Gradle commands in the background. Stop daemons before booting the emulator (`./gradlew --stop`), because RAM is tight (7 GB).

## Architecture

```
domain/     Pure Kotlin, no Android. Unit-tested in app/src/test/.../StreakEngineTest.kt
  StreakEngine.kt   spans between events, current/longest streak, total clean days, DayStatus, slipTimestamps (cut-down mode)
  Milestones.kt     12 milestones; earned() uses the LONGEST streak (so they're never lost)
  Insights.kt       RelapseRecord, Habit (money, dailyLimit, reasons), UrgeRecord, JournalEntry, InsightsEngine (+ urges)
  Messages.kt       motivational copy; forDate() is stable per day (app + widgets agree)
  Options.kt        triggers, situations, moods, tracker emojis and suggestions
data/
  Database.kt       Room v3: habits, relapses, urges, journal; DAOs; MIGRATION_1_2, MIGRATION_2_3
  Repository.kt     Relapse/Habit/Urge/JournalRepository (ensureLegacyHabit = v1→v2 data upgrade)
  SettingsRepository.kt  DataStore prefs → Settings (theme, accent, selectedHabitId, app lock, discreet, reminders, auto-backup…)
  Backup.kt         JSON export/import, format v3 (habits with nested relapses/urges/journal); reads v1+v2. BackupCrypto = optional AES-GCM password
  AutoBackup.kt     BackupWriter + daily SAF-folder backups (WorkManager), keeps the last 7
notify/
  Reminders.kt      Routes (deep-link extras), local check-in + milestone notifications (WorkManager), discreet text
  UrgeTileService.kt  Quick Settings tile → urge screen
DaymarkApp.kt       AppContainer (manual DI), runs the legacy migration, refreshes widgets on data change
ui/
  MainActivity.kt   FragmentActivity (needed by BiometricPrompt). Gate: LOCKED/ONBOARDING/LOADING/APP. NavHost + routes
  MainViewModel.kt  single shared VM: settings, habits, selectedHabit, relapses (selected), stats (ticks 1s), overview, insights
  theme/Theme.kt    Accent enum (8 accents, light+dark, hero gradient), neutral surfaces, DaymarkColors (clean/relapse/untracked)
  theme/Motion.kt   Apple-style springs: standard (no bounce), bouncy (only after gestures), press, gentle
  components/       DaymarkCard, pressScale, RollingNumber, ProgressRing, GrowingBar, Haptics…
  dashboard/ calendar/ log/ urge/ journal/ insights/ settings/ onboarding/ lock/ habits/ widgets/   (one screen each)
  share/ShareCard.kt  milestone image rendered on-device, shared via FileProvider + share sheet
widget/
  Widgets.kt        DaymarkGlanceWidget base + Streak, Ring, Heatmap, Quote (shows reasons when not discreet), Actions (urge/journal/log), Stats; receivers
  WidgetStyle.kt    WidgetStyle enum (6 styles → res/values/widget_colors.xml), WidgetKind, WidgetDefaults
  WidgetData.kt     WidgetSnapshot (per-tracker data), WidgetUpdater + WorkManager refresh at the next day boundary
  WidgetArt.kt      bitmaps Glance can't draw: ring, heatmap, progress bar
  WidgetPreview.kt  Compose mirror of each widget (used by the style picker and in-app gallery)
  WidgetConfigActivity.kt  style + tracker picker (launcher "configure"), WidgetPinnedReceiver
debug/…/DemoSeedReceiver.kt   demo data, debug builds only
```

Data flow: Room/DataStore `Flow` → `MainViewModel` `StateFlow` → screens use `collectAsStateWithLifecycle()`. Widgets collect the same repository flows inside `provideContent`.

## Key behaviours

- A **streak** runs from the tracker's `startAt` (or an earlier logged slip) to the next slip. Future-dated slips are ignored. **Total clean days** is the number of calendar days from start to today with no slip.
- **Day status** colours: `CLEAN` is green, `RELAPSE` is red, `UNTRACKED` is grey. These are semantic colours and don't follow the accent.
- **Widgets:** the live h:m:s is a system `Chronometer` embedded via `AndroidRemoteViews` (`res/layout/widget_chronometer.xml`), so it ticks without waking the app. Each widget stores its style and tracker in Glance state (`StyleKey`, `HabitKey`). A new widget claims `WidgetDefaults` on its first render.
- **Cut-down mode:** a tracker with `dailyLimit > 0` counts every entry as a "use"; only the entry that takes a day over the limit is a slip. Always pass entry times through `Habit.slipTimes()` before `StreakEngine`.
- **Discreet mode** (on by default): widgets, notifications, the QS tile and share cards never name a tracker (`WidgetSnapshot.habit` is blank, reasons hidden). Keep it that way for any new surface outside the app.
- **Entry points:** shortcuts (`res/xml/shortcuts.xml`), the QS tile and notifications open `MainActivity` with `Routes.EXTRA`; `DaymarkNav` navigates once unlocked.
- **App lock** re-locks after more than 20 s in the background (`LOCK_GRACE_MS`), so file pickers for backup don't force a re-unlock.

## Recipes

**Add a field to a table**
1. Add it to the entity in `Database.kt`.
2. Bump `@Database(version = N+1)`.
3. Add `MIGRATION_N_N+1` (`ALTER TABLE … ADD COLUMN … DEFAULT …`) and register it in `build()`.
4. Build once. The new `app/schemas/.../N+1.json` must be committed.
5. If the field should survive backups, add it to `Backup.kt`, bump `BackupFile.VERSION`, and keep decoding older versions.
6. Add a backup round-trip test.

**Add a setting**
1. Add a field to `Settings`.
2. Add a key and a setter in `SettingsRepository` (also in `replaceAll`).
3. Add an action in `MainViewModel`.
4. Add a row in `SettingsScreen`.

**Add a screen or route**
1. Write the composable in `ui/<feature>/`.
2. Add a `composable(...)` in `DaymarkNav` (`MainActivity.kt`). Modal tasks slide up from the bottom and leave the same way.

**Add a widget style**
1. Add `wdg_<id>_*` colours to `res/values/widget_colors.xml`, plus `values-night` if the style is theme-aware.
2. Add a `drawable/widget_bg_<id>.xml`.
3. Add an entry to `WidgetStyle`.

**Add a widget type**
1. Add a `WidgetKind` and a subclass of `DaymarkGlanceWidget`, then a receiver.
2. Add `res/xml/widget_<kind>_info.xml` and `layout/widget_preview_<kind>.xml`.
3. Add the receiver to the manifest and update `receiver()`/`widget()` in `Widgets.kt`.
4. Add a case to `WidgetPreview`.
5. Add it to `WidgetUpdater.updateAll`.

**Add an accent**
- Add an entry to `Accent` in `Theme.kt`. The picker lists the entries automatically.

**Release:** see `docs/MAINTAINING.md` → "Ship an update".

## Gotchas (learned the hard way)

- Compose `Text` defaults to **black** outside a `Surface`, which is invisible in dark mode. The app root is a `Surface`; keep it that way, and wrap any new Activity's content in one too.
- In Glance 1.1.1, `ColorProvider(@ColorRes)` is a restricted API and lint fails the release on it. Use `dayNight(context, colorRes)` in `Widgets.kt`.
- Don't read `StateFlow.value` inside a composable (lint error). Use `collectAsStateWithLifecycle()`.
- `compileSdk 36` with AGP 8.7.3 needs `android.suppressUnsupportedCompileSdk=36` in `gradle.properties`. Upgrading AGP (via Dependabot) lets you remove that line.
- **Pending toolchain upgrade (noted 2026-11-04):** the latest AndroidX releases (Compose BOM 2026.09, navigation 2.10, Room 2.8…) need **AGP ≥ 9.1 and compileSdk 37**, so Dependabot's androidx group PR fails `checkDebugAarMetadata` on AGP 8.7.3. Do the upgrade as a dedicated change:
  1. Upgrade AGP to 9.x and Gradle to the version it requires.
  2. Install `platforms;android-37` and set `compileSdk = 37`.
  3. Migrate to AGP 9's built-in Kotlin: drop the `kotlin-android` plugin and move `kotlinOptions` to `kotlin { compilerOptions }`.
  4. Update KSP to a matching version.
  5. Then take the androidx bumps, run tests and lint, do an emulator smoke test, and release.
- **Glance widgets use `SizeMode.Responsive`** with fixed breakpoints (`sizes` in each widget). `SizeMode.Exact` re-measured on every refresh, so an update pushed while the app was open could leave a widget stuck in its small layout. Bitmaps (ring, heatmap, bar) are drawn at the breakpoint size; use `ContentScale.FillBounds` where they must stretch.
- In Glance, `ColorFilter.tint` on a drawable **drops the colour's alpha** (tiles came out solid white). For translucent tiles use `background(colorProvider).cornerRadius(…)`.
- `DaymarkApp` keeps a far-future WorkManager job (`daymark-keep-alive`). Without pending work, WorkManager toggles its receivers, and some launchers then reset every widget to `initialLayout` (`layout/widget_loading.xml`).
- Emulator: `adb emu kill` can drop recent writes, so run `adb shell sync` first. Widgets pinned from the gallery may land on the **second** home-screen page. When app lock is on, every reopen after 20 s shows BiometricPrompt; type the PIN with `adb shell input text 1234`.
- The Gradle wrapper's `networkTimeout` was raised to 120 s because the default 10 s times out here.
- Under load the emulator shows "isn't responding" dialogs for System UI or the launcher. That's the emulator, not the app. Wait about 60 s after boot before testing.
- Debug and release builds have different signatures, so switching between them on a device needs an uninstall (`scripts/emulator.sh` handles it).
- Play screenshots must be at most 2:1. The emulator's 1080×2400 shots are padded to 1200×2400.
- `.agents/` and `.claude/` (the installed `apple-design` skill) are tooling and are git-ignored.

## Signing and secrets

- The release key is `~/.config/daymark/release.jks`, alias `daymark`. Its passwords are in `~/.gradle/gradle.properties` (`DAYMARK_*`). Both are outside the repo; never commit them.
- CI signs builds using the secrets `DAYMARK_KEYSTORE_BASE64`, `DAYMARK_STORE_PASSWORD`, `DAYMARK_KEY_ALIAS` and `DAYMARK_KEY_PASSWORD`. Set them with `scripts/setup-signing-secrets.sh`.
- `PLAY_SERVICE_ACCOUNT_JSON` is optional. If present, releases are also uploaded to Play's internal track as drafts.
