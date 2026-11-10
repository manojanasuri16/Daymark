#!/usr/bin/env bash
# Cut a release:  scripts/release.sh 1.2.0
#   1. bumps versionName to 1.2.0 and versionCode by +1 in app/build.gradle.kts
#   2. makes sure release notes exist (fastlane/.../changelogs/<versionCode>.txt), creating a template if not
#   3. runs unit tests, commits, tags v1.2.0 and pushes → GitHub Actions builds, signs and publishes the release
set -euo pipefail
cd "$(dirname "$0")/.."

NEW="${1:-}"
[[ "$NEW" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Usage: scripts/release.sh X.Y.Z   (current: $(grep -oP 'versionName = "\K[^"]+' app/build.gradle.kts))"; exit 1; }
[ "$(git branch --show-current)" = "main" ] || { echo "Switch to main first (git checkout main)."; exit 1; }
git rev-parse "v$NEW" >/dev/null 2>&1 && { echo "Tag v$NEW already exists."; exit 1; }

OLD_CODE=$(grep -oP 'versionCode = \K[0-9]+' app/build.gradle.kts)
CODE=$((OLD_CODE + 1))
NOTES="fastlane/metadata/android/en-US/changelogs/$CODE.txt"

if [ ! -s "$NOTES" ]; then
  mkdir -p "$(dirname "$NOTES")"
  printf 'New in %s:\n• \n' "$NEW" > "$NOTES"
  echo "Wrote a template at $NOTES"
  echo "→ Fill in what changed (shown on GitHub), then run this again."
  exit 1
fi
grep -q '^• *$' "$NOTES" && { echo "$NOTES still has an empty bullet — finish it first."; exit 1; }

# Only the release notes (and CHANGELOG.md) may be uncommitted.
DIRTY=$(git status --porcelain | grep -v -e "$NOTES" -e 'CHANGELOG.md' || true)
[ -z "$DIRTY" ] || { echo "Commit or stash other changes first:"; echo "$DIRTY"; exit 1; }

sed -i "s/versionCode = $OLD_CODE/versionCode = $CODE/; s/versionName = \"[^\"]*\"/versionName = \"$NEW\"/" app/build.gradle.kts

# Prepend to CHANGELOG.md
{ echo "## $NEW — $(date +%Y-%m-%d) (versionCode $CODE)"; echo; sed '1{/^New in/d}' "$NOTES"; echo; cat CHANGELOG.md 2>/dev/null | sed '1{/^# Changelog/d}'; } > CHANGELOG.tmp
{ echo "# Changelog"; echo; cat CHANGELOG.tmp; } > CHANGELOG.md && rm CHANGELOG.tmp

echo "Running unit tests…"
./gradlew -q testDebugUnitTest

git add app/build.gradle.kts "$NOTES" CHANGELOG.md
git commit -q -m "Release v$NEW"
git tag "v$NEW"
git push -q origin main "v$NEW"
echo "Pushed v$NEW. Watch the build:  gh run watch   (or the Actions tab on GitHub)"
echo "When it's green: https://github.com/$(gh repo view --json nameWithOwner -q .nameWithOwner)/releases/tag/v$NEW"
