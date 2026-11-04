# Publishing Daymark on Google Play

Everything that can be prepared in the repo is ready. The rest happens in Play Console under your own developer account.

| Ready in the repo | Where |
|---|---|
| Signed app bundle (AAB), targetSdk 36 | `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab` |
| Store icon 512×512 | `fastlane/metadata/android/en-US/images/icon.png` |
| Feature graphic 1024×500 | `fastlane/metadata/android/en-US/images/featureGraphic.png` |
| 8 phone screenshots (1200×2400) | `fastlane/metadata/android/en-US/images/phoneScreenshots/` |
| Title, short and full description | `fastlane/metadata/android/en-US/*.txt` |
| Release notes | `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` |
| Privacy policy text | `docs/privacy-policy.md` (it must be hosted at a public URL, see step 2) |

---

## 1. Create a developer account (one time)

1. Go to <https://play.google.com/console/signup> and pay the one-time **US$25** fee.
2. Complete identity verification. This can take a few days.
3. **New personal accounts must run a closed test first.** Before you can publish to production, at least **12 testers** must stay opted in to a closed test for **14 consecutive days**. Plan for this (step 6).

## 2. Host the privacy policy

Play needs a public URL. The repo is private, so pick one of these:

- **Public gist** (simplest): `gh gist create --public docs/privacy-policy.md`, then use the gist URL.
- **GitHub Pages:** make the repo public, or host the file from a separate public repo.
- Any personal website.

## 3. Create the app

In Play Console, choose **Create app**:

- **App name:** `Daymark: Sobriety & Streaks`
- **Default language:** English (United States)
- **App or game:** App
- **Free or paid:** Free
- Accept the declarations.

## 4. App content (Policy → App content)

| Form | Answer for Daymark |
|---|---|
| Privacy policy | The URL from step 2 |
| Ads | **No**, the app contains no ads |
| App access | **All functionality is available without special access** (there is no login) |
| Content rating | Category **"All other app types"**. Answer honestly: the app refers to alcohol, tobacco and drugs only in a recovery context. It has no violence, no user-to-user communication, no purchases and no location sharing. |
| Target audience | **18 and over**. This keeps the app out of the Families policy, which suits a recovery app. |
| Data safety | **No data collected** and **No data shared**. All data is stored only on the device, and the app has no INTERNET permission. Answer "No" to data collection for every data type. |
| Health apps | Category is Health & Fitness, so fill in the health declaration. Select the categories that match self-tracking of habit and behaviour change, and confirm it is **not** a medical device and offers no medical treatment. |
| Government, financial, news | Not applicable |

> These forms change from time to time. If a question doesn't match this table, answer it from what the app actually does: it is offline, collects nothing and contains no ads.

## 5. Store listing (Grow → Store presence → Main store listing)

Copy the text from `fastlane/metadata/android/en-US/` and upload the icon, feature graphic and screenshots.

- **Category:** Health & Fitness
- **Tags:** habit tracker, sobriety, self-improvement
- **Contact email:** manojanasuri1@gmail.com

## 6. Release

1. **Testing → Closed testing → Create track.** Upload `app-release.aab`. Accept **Play App Signing** when prompted. Your local key then becomes the *upload key*.
2. Add testers by email or a Google Group, at least 12 people, and share the opt-in link.
3. After 14 days with 12 or more opted-in testers, go to **Dashboard → Apply for production**.
4. Once approved, promote the release to **Production**. The first review usually takes from a few hours to a few days.

## 7. Updates

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
3. Run `./gradlew bundleRelease` and upload the AAB to the track.

Optional automation: create a Play service account in Google Cloud and grant it access in Play Console. `fastlane supply` can then upload builds and metadata from this folder structure.

---

## Signing and the GitHub APK

- The **upload key** is `~/.config/daymark/release.jks`, and its passwords are in `~/.gradle/gradle.properties`. **Back both up somewhere safe.** If they're lost, you can request an upload-key reset from Play support, but it is slow.
- With Play App Signing, **Google re-signs** the app it delivers to users. A build installed from Play therefore can't update over the GitHub Releases APK, and the reverse is also true. Switching between them requires an uninstall, so export a backup first under *Settings → Export backup*.
- Recommendation: once the app is live on Play, install it from Play, restore your backup, and keep GitHub Releases for testers only.
