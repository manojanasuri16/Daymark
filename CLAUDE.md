# Daymark: guide for Claude (and humans)

Daymark is a private, offline-first Android app for building good habits and breaking unwanted ones (two habit kinds: BREAK = streak since last slip, BUILD = daily completions). It has one module (`:app`), package `com.daymark.app`, and is written in Kotlin with Jetpack Compose (Material 3), Room, DataStore and Glance widgets. A human-friendly runbook is in `docs/MAINTAINING.md`.

## Non-negotiables

- **Privacy:** never add the `INTERNET` permission, analytics, crash SDKs, ads or accounts. Data lives only on the device. The store listing, privacy policy and Data-safety form all promise this.
- **Tone:** say "slip", never "failure" or "relapse" in UI copy. Keep it supportive and non-judgmental. A slip must never erase history: longest streak, total clean days and achievements are always kept.
- **Distribution is AppDrop only** (`appdrop.json`, `appdrop/`, APKs from GitHub Releases). No Play Store work.
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
./gradlew assembleRelease                  # signed release APK
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
  Insights.kt       NonSlipNote ("Not a slip" note), RelapseRecord, Habit (money, dailyLimit, reasons), UrgeRecord, JournalEntry, InsightsEngine (+ urges)
  Messages.kt       motivational copy; forDate() is stable per day (app + widgets agree)
  Build.kt          HabitKind, Schedule (weekday bitmask), ReminderConfig, BuildEngine (streak over scheduled days; rest days and a pending today never break it), BuildStats, Completion
  BuildInsights.kt  weekday stats / streak runs for build habits
  Events.kt         EventKind (COUNTDOWN/SINCE/TIMER), EventRepeat, CountEvent, EventOptions (counting + style, ONE JSON column; `decode` never throws, unknown keys ignored), enums, EventSort/EventFilter/NotifType
  EventEngine.kt    pure event maths (`now` + zone): cycle/cycleAt (repeats via java.time from the ORIGINAL date, so Jan 31 +1 month clamps without drifting; ON_COMPLETION restarts timers), progress, format (units, round up/down, 'smaller unit below 2' precision, accessories, percent), calendar-correct Y M D H min breakdown, nextEvent (queue), notificationTimes
  EventTemplates.kt 8 wizard starters, ~60 ideas, EventDefaults (10 colour presets, 40-swatch palette, seeded Today/Week/Month/Year events)
  Options.kt        triggers, build presets, situations, moods, tracker emojis and suggestions
data/
  Database.kt       Room v6: habits (kind, goal, schedule, reminder columns), relapses, urges, journal, journal_photos, completions (one row per habit per day), notes (`NoteEntity`, "Not a slip"); DAOs (incl. `NoteDao`); MIGRATION_1_2 … MIGRATION_5_6
  Repository.kt     Relapse/Habit/Urge/Journal/NoteRepository (ensureLegacyHabit = v1→v2 data upgrade)
  SettingsRepository.kt  DataStore prefs → Settings (theme, accent, selectedHabitId, app lock, discreet, reminders, auto-backup…)
  Backup.kt         JSON export/import, format v6 (habits with nested completions and notes, with nested relapses/urges/journal, journal photos as base64); reads v1–v5. BackupCrypto = optional AES-GCM password
  PhotoStore.kt     journal photos: files/journal_photos/<uuid>.jpg (≤1600 px, re-encoded), thumbnails, base64, orphan sweep
  AutoBackup.kt     BackupWriter + daily SAF-folder backups (WorkManager), keeps the last 7
notify/
  Reminders.kt      Routes (deep-link extras), local check-in + milestone notifications (WorkManager), discreet text
  HabitReminders.kt + ReminderPlanner.kt  build-habit reminders: one inexact setAndAllowWhileIdle alarm per habit (no exact-alarm permission), next slot only, pure planner; notification actions +step/Done, Snooze 15, Skip; boot/time-change receiver
  UrgeTileService.kt  Quick Settings tile → urge screen
DaymarkApp.kt       AppContainer (manual DI), runs the legacy migration, refreshes widgets on data change
ui/
  MainActivity.kt   FragmentActivity (needed by BiometricPrompt). Gate: LOCKED/ONBOARDING/LOADING/APP. NavHost + routes
  MainViewModel.kt  single shared VM (`messages` = snackbar flow; `events` = all CountEvents; saveEvent/deleteEvent/restoreEvent/duplicateEvent/archiveEvent, seedEventsIfNeeded is idempotent via Settings.eventsSeeded): settings, habits, selectedHabit, relapses (selected), stats (ticks 1s), overview, insights
  theme/Theme.kt    Accent enum (8 accents, light+dark, hero gradient), neutral surfaces, DaymarkColors (clean/relapse/untracked)
  theme/Motion.kt   Apple-style springs: standard (no bounce), bouncy (only after gestures), press, gentle
  components/       DaymarkCard, pressScale, RollingNumber, ProgressRing, GrowingBar, Haptics…
  dashboard/ calendar/ log/ urge/ journal/ insights/ settings/ onboarding/ lock/ habits/ widgets/   (one screen each)
  share/ShareCard.kt  milestone image rendered on-device, shared via FileProvider + share sheet
  ui/events/EventArt.kt  the ONE Canvas renderer of event cards (Text/Ring/Grid/Bars/Minimal; FULL/TRANSLUCENT/TRANSPARENT), pure function used by in-app cards, editor preview and the widget
  ui/events/EventLayoutMath.kt  pure, unit-tested layout maths (aspect, ring arc, grid fit, item counts, text-fit search)
  ui/events/EventCard.kt  Compose wrapper: renders EventArt off the main thread, minute granularity; EventCardPreviewSizes, rememberMinuteNow
  ui/events/EventFonts.kt  10 bundled fonts (assets/fonts, subset variable TTFs, licences in third_party/FONTS.md); typeface()/weighted() cached, system fallback
  ui/events/EventEditorScreen.kt  full editor: pinned live EventCard (hidden while the keyboard is up), floating pill tab bar Dates/Counting/Style (+ overflow Duplicate/Archive/Delete for saved events), Save disabled unless valid and changed, discard confirm; the draft survives rotation via `EventDraftCodec` JSON in rememberSaveable; `newEventDraft(kind)` gives per-kind defaults + random preset. Tabs in EventDatesTab.kt (incl. full-screen DateEditor overlay, type switch only for NEW events), EventCountingTab.kt, EventStyleTab.kt (design pager, per-design options, colour/font/icon sheets in EventEditorSheets.kt, shared widgets in EventEditorParts.kt); EventShare.kt shares the card as PNG via the `.share` FileProvider
  domain/EventEditing.kt  pure editor helpers (timer duration caps/validation, normalize = timer end from duration, repeat availability vs duration, kind conversion, span/total text, accessory choices, hex/HSV colour maths, EventDraftCodec); tests in EventEditingTest
  ui/events/EventIcons.kt  fixed ~40-icon set mapped to Material Rounded vectors; draw() paints them onto a Canvas
widget/
  Widgets.kt        DaymarkGlanceWidget base + Streak, Ring, Heatmap, Quote (shows reasons when not discreet), Actions (urge/journal/log), Stats; receivers
  WidgetStyle.kt    WidgetStyle enum (6 styles → res/values/widget_colors.xml), WidgetKind, WidgetDefaults
  WidgetData.kt     WidgetSnapshot (per-tracker data), WidgetUpdater + WorkManager refresh at the next day boundary
  WidgetArt.kt      bitmaps Glance can't draw: ring, heatmap, progress bar
  EventWidget.kt    EventWidget (WidgetKind.EVENT, receiver EventWidgetReceiver): EventArt as one Fit-scaled bitmap per Responsive breakpoint (artDensity budget), tap opens Routes.event(id), empty state "Choose an event" opens the config. Glance keys EventIdKey/QueueKey/QueueNKey/BgModeKey. Never shows habit data; honours showNameOutside. Own content path (provideEventContent), not the habit snapshot
  EventWidgetConfigActivity.kt  "Select an event": queue switch + Nth nearest, event grid, background Full/Translucent/Transparent
  WidgetPreview.kt  Compose mirror of each widget (used by the style picker and in-app gallery)
  WidgetConfigActivity.kt  style + tracker picker (launcher "configure"), WidgetPinnedReceiver
ui/events/   Events tab (5th bottom-bar tab): EventsScreen (filter/sort/size pills persisted in settings, LazyVerticalGrid of EventCard, long-press menu, speed-dial FAB, battery nag, empty state), EventEditorScreen(vm, eventId, kind, onClose, onDeleted) behind route `event?id=&kind=` (modal slide-up; full editor, see below), EventCard/EventArt renderer. Pure list logic in domain/EventListLogic.kt (archived only under Archived; repeating/open-ended events are always ongoing). Notifications open an event via Routes.event(id) ("event:<id>").
debug/…/DemoSeedReceiver.kt   demo data, debug builds only
```

Data flow: Room/DataStore `Flow` → `MainViewModel` `StateFlow` → screens use `collectAsStateWithLifecycle()`. Widgets collect the same repository flows inside `provideContent`.

## Key behaviours

- A **streak** runs from the tracker's `startAt` (or an earlier logged slip) to the next slip. Future-dated slips are ignored. **Total clean days** is the number of calendar days from start to today with no slip.
- **Day status** colours: `CLEAN` is green, `RELAPSE` is red, `UNTRACKED` is grey. These are semantic colours and don't follow the accent.
- **Widgets:** the live h:m:s is a system `Chronometer` embedded via `AndroidRemoteViews` (`res/layout/widget_chronometer.xml`), so it ticks without waking the app. Each widget stores its style and tracker in Glance state (`StyleKey`, `HabitKey`). A new widget claims `WidgetDefaults` on its first render.
- **"Not a slip" notes:** the Log screen has a Slip / Not a slip choice. Not-a-slip entries are `NonSlipNote`s in the `notes` table (`NoteRepository`), shown on the Calendar as a "📝 time · Not a slip" card plus a dot on the day. They are NEVER fed into `StreakEngine`, stats or insights, so the streak is unaffected.
- **Cut-down mode:** a tracker with `dailyLimit > 0` counts every entry as a "use"; only the entry that takes a day over the limit is a slip. Always pass entry times through `Habit.slipTimes()` before `StreakEngine`.
- **Discreet mode** (on by default): widgets, notifications, the QS tile and share cards never name a tracker (`WidgetSnapshot.habit` is blank, reasons hidden). Keep it that way for any new surface outside the app.
- **Journal photos** are files; the DB only stores their names. Deleting an entry deletes its files, and `PhotoStore.sweep()` (at app start and after a restore) removes any file no row references, e.g. after a tracker is deleted. Files younger than an hour are never swept (an entry may still be open).
- **Entry points:** shortcuts (`res/xml/shortcuts.xml`), the QS tile and notifications open `MainActivity` with `Routes.EXTRA`; `DaymarkNav` navigates once unlocked.
- **App lock** re-locks after more than 20 s in the background (`LOCK_GRACE_MS`), so file pickers for backup don't force a re-unlock.
- **Build habits:** `Habit.isBuild`. Progress is a `completions` row per habit per day (amount in the goal unit; goal 0 = check-off). Screens branch on kind (`BuildDashboard`, `BuildCalendar`, `BuildInsightsScreen`, `BuildProgressScreen`); `MainViewModel.stats` maps build streaks into `StreakStats` so streak-shaped UI and widgets still work. Kind can't be changed after creation. Never run break-habit maths (StreakEngine/relapses) on a build habit.

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

**Event widgets** (done in `EventWidget.kt`): `domain/WidgetRefresh.kt` holds the pure timing (`nextEventRefresh`: earliest of midnight, event completion and `widgetRefreshMinutes` 15/30; never under 15 s; WorkManager only, no exact alarms) and `nextKey`. `WidgetUpdater.scheduleNext` considers those only while an event widget is placed.

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
- **Glance keeps one running session per widget id, and every `update()` for that id (from any `GlanceAppWidget` class) goes into it.** If the wrong class ever starts the session, the widget draws that class's layout until the session times out (a ring widget showed the streak widget's slim row after unlocking). So `DaymarkGlanceWidget` renders by the widget's real provider (`kindOfAppWidget`), and nothing may guess a kind: no `?: STREAK` fallbacks.
- In Glance, `ColorFilter.tint` on a drawable **drops the colour's alpha** (tiles came out solid white). For translucent tiles use `background(colorProvider).cornerRadius(…)`.
- `DaymarkApp` keeps a far-future WorkManager job (`daymark-keep-alive`). Without pending work, WorkManager toggles its receivers, and some launchers then reset every widget to `initialLayout` (`layout/widget_loading.xml`).
- Emulator: `adb emu kill` can drop recent writes, so run `adb shell sync` first. Widgets pinned from the gallery may land on the **second** home-screen page. When app lock is on, every reopen after 20 s shows BiometricPrompt; type the PIN with `adb shell input text 1234`.
- The Gradle wrapper's `networkTimeout` was raised to 120 s because the default 10 s times out here.
- Under load the emulator shows "isn't responding" dialogs for System UI or the launcher. That's the emulator, not the app. Wait about 60 s after boot before testing.
- Debug and release builds have different signatures, so switching between them on a device needs an uninstall (`scripts/emulator.sh` handles it).
- AppDrop screenshots must be at most 2:1. The emulator's 1080×2400 shots are padded to 1200×2400.
- `.agents/` and `.claude/` (the installed `apple-design` skill) are tooling and are git-ignored.

- **`RefreshKey` (stale widgets after a day change):** a still-running Glance session re-uses its composition when nothing it reads changed, so the Ring widget kept yesterday's day count after DATE_CHANGED. `WidgetUpdater.updateAll` therefore writes `RefreshKey` (`WidgetRefresh.nextKey`, strictly increasing millis) into every placed widget's Glance state BEFORE `update()`, and `provideContent` reads it and rebuilds the snapshot from `System.currentTimeMillis()`. Every new widget kind must go through `WidgetUpdater.refresh`, and must read `currentState` in its content.

## Signing and secrets

- The release key is `~/.config/daymark/release.jks`, alias `daymark`. Its passwords are in `~/.gradle/gradle.properties` (`DAYMARK_*`). Both are outside the repo; never commit them.
- CI signs builds using the secrets `DAYMARK_KEYSTORE_BASE64`, `DAYMARK_STORE_PASSWORD`, `DAYMARK_KEY_ALIAS` and `DAYMARK_KEY_PASSWORD`. Set them with `scripts/setup-signing-secrets.sh`.
