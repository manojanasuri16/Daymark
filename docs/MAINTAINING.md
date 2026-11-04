# Maintaining Daymark

This guide is for coming back to the project after a long break. Each section is one situation: find yours and follow the steps.

> **Easiest path:** open this folder in Claude Code and describe what you want, e.g. "add a reminder notification" or "release 1.2.0". Claude reads `CLAUDE.md` automatically, so it already knows the architecture, the rules and the pitfalls.

---

## Where things are

| What | Where |
|---|---|
| App code | `app/src/main/java/com/daymark/app/` (a map is in `CLAUDE.md`) |
| Version number | `app/build.gradle.kts` → `versionCode` and `versionName` |
| Release notes shown on Play and GitHub | `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` |
| History of changes | `CHANGELOG.md` |
| Store text, icon and screenshots | `fastlane/metadata/android/en-US/` |
| Privacy policy | `docs/privacy-policy.md` |
| Play Store steps | `docs/PLAY_STORE.md` |
| Signing key (**not in the repo**) | `~/.config/daymark/release.jks` + passwords in `~/.gradle/gradle.properties` |
| Android SDK | `~/android-sdk` |
| Automation | `.github/workflows/` (`build.yml` runs on every push to `main`; `release.yml` runs on version tags) |

---

## Ship an update

1. Make sure the change is on `main` and the latest **Build** check on GitHub is green.
2. Pick the new version, e.g. `1.2.0`. Use the last number for fixes, the middle one for features, and the first for big redesigns.
3. Run:
   ```bash
   scripts/release.sh 1.2.0
   ```
   The first run creates `fastlane/metadata/android/en-US/changelogs/<code>.txt` and stops. Write 2–4 friendly bullet points about what changed (500 characters at most), then run the same command again.
4. The script bumps the version, updates `CHANGELOG.md`, runs the tests, commits, tags and pushes.
5. GitHub then builds, signs and publishes the release, which takes about 10 minutes. Watch it with `gh run watch` or in the **Actions** tab.
6. When it's done, the APK is on the **Releases** page. For Google Play, upload the `.aab` from that release in Play Console (or let CI do it once `PLAY_SERVICE_ACCOUNT_JSON` is set; see below).

**If the release workflow fails:**
- *"Secret DAYMARK_KEYSTORE_BASE64 is missing":* run `scripts/setup-signing-secrets.sh` once, then re-run the failed workflow from the Actions tab.
- *"Tag … doesn't match versionName":* the tag and `versionName` disagree. Delete the tag (`git push --delete origin vX.Y.Z && git tag -d vX.Y.Z`) and use `scripts/release.sh`.
- *Tests or lint failed:* open the failed step's log. Run the same command locally (`./gradlew testDebugUnitTest lintRelease`) to see the details.

## Try a change on the emulator

```bash
scripts/emulator.sh                                    # boots, installs the debug build, opens the app
adb shell am broadcast -n com.daymark.app/.DemoSeedReceiver   # optional: fills in 3 demo trackers
adb exec-out screencap -p > shot.png                   # screenshot
adb emu kill                                           # stop it
```

The emulator is slow for its first minute and may show "System UI isn't responding". Tap *Wait*; it isn't the app.

## Try a change on your phone

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk   # phone connected over USB with USB debugging on
```

Use the **release** build on your own phone, because it's signed like the GitHub and Play versions, so your data is kept. A debug build would need an uninstall, which deletes your data.

## Library updates (Dependabot pull requests)

Once a month, GitHub opens pull requests that update libraries. For each one:

1. Wait for its **Build** check.
2. If it's green, merge it.
3. If it's red, ask Claude: *"Dependabot PR #N fails, please fix it"*.

## Yearly Android requirement

Google Play requires apps to target a recent Android version, with a new deadline each August, and Play Console emails you beforehand. To comply:

1. Raise `compileSdk` and `targetSdk` in `app/build.gradle.kts`.
2. Install the new SDK platform: `~/android-sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-NN"`.
3. Test, then release.

Or just ask Claude: *"update Daymark to target the latest Android"*.

## Changing what data the app stores

This is the riskiest kind of change, because a mistake can wipe users' history. Always:

- Write a database **migration**. The exact steps are in `CLAUDE.md` → Recipes → "Add a field to a table".
- Keep backups working: older backup files must still restore.
- Test by installing the **old** release, adding data, then installing the new build over it. Confirm everything is still there.

## Signing key: keep it safe

- Without the key, you can't update the GitHub version, and Play updates need a slow key-reset request.
- Keep a copy of `~/.config/daymark/release.jks` **and** `~/.gradle/gradle.properties` somewhere outside this laptop, such as a password manager or an encrypted USB drive.

**Moving to a new computer:**
1. Copy both files back to the same paths.
2. Install the Android SDK to `~/android-sdk`.
3. Run `./gradlew assembleRelease` to check that signing works.

## Turning on automatic Play uploads (after your Play account exists)

1. In Google Cloud, create a service account and download its JSON key.
2. In Play Console, go to **Users and permissions**, invite the service account's email, and grant it release permissions for Daymark.
3. Add the JSON to GitHub: `gh secret set PLAY_SERVICE_ACCOUNT_JSON < key.json`, then delete the local file.
4. From then on, every release tag also uploads the AAB to Play's **internal testing** track as a draft. Review it and roll it out in Play Console.

## Free plan limits (private repo)

GitHub Actions includes 2,000 minutes a month. A build takes about 8 minutes, so that's roughly 250 builds. Build artifacts expire after 7–14 days so storage stays under the 500 MB limit. Your Releases don't count toward that limit.

## Asking Claude for help (example prompts)

- "Add a feature: daily reminder notification at a time I choose"
- "Users say the heatmap widget is blank on Samsung. Investigate"
- "Release 1.2.0 with the changes since 1.1.0"
- "Prepare the Play Store update for the new features (screenshots and listing text)"
- "Explain how streaks are calculated"
