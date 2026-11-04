#!/usr/bin/env bash
# Boot the headless test emulator, wait until it's usable, then install a build.
#   scripts/emulator.sh            → installs the debug build (supports demo data)
#   scripts/emulator.sh release    → installs the release build
# Stop it with:  adb emu kill
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}" ANDROID_SDK_ROOT="${ANDROID_HOME:-$HOME/android-sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
if ! "$ADB" devices | grep -q "emulator-"; then
  nohup "$ANDROID_HOME/emulator/emulator" -avd daymark_test -no-window -no-audio -no-snapshot -no-boot-anim \
    -gpu swiftshader_indirect -memory 2048 -cores 4 >/tmp/daymark-emulator.log 2>&1 &
  "$ADB" wait-for-device
  until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 3; done
  echo "Booted. Letting it settle (the emulator is very slow for the first minute)…"; sleep 60
fi
TYPE="${1:-debug}"
APK="app/build/outputs/apk/$TYPE/app-$TYPE.apk"
[ -f "$APK" ] || ./gradlew -q "assemble${TYPE^}"
# Debug and release are signed differently, so switching types needs a clean install.
"$ADB" install -r "$APK" 2>/dev/null || { "$ADB" uninstall com.daymark.app >/dev/null; "$ADB" install "$APK"; }
"$ADB" shell am start -n com.daymark.app/.ui.MainActivity >/dev/null
echo "Running. Demo data (debug only): $ADB shell am broadcast -n com.daymark.app/.DemoSeedReceiver"
echo "Screenshot: $ADB exec-out screencap -p > shot.png    Tap by text: scripts/tap.sh \"Settings\""
