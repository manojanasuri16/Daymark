#!/usr/bin/env bash
# Tap the Nth on-screen element whose text or content-description contains TEXT.
#   scripts/tap.sh "Add to home screen" [N]
set -euo pipefail
ADB="${ANDROID_HOME:-$HOME/android-sdk}/platform-tools/adb"
XML=$(mktemp)
"$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
"$ADB" shell cat /sdcard/ui.xml > "$XML"
XY=$(python3 -I - "$XML" "$1" "${2:-1}" <<'PY'
import re, sys
xml, text, n = open(sys.argv[1]).read(), sys.argv[2], int(sys.argv[3])
for m in re.finditer(r'<node [^>]*>', xml):
    node = m.group(0)
    t = re.search(r' text="([^"]*)"', node).group(1)
    d = re.search(r'content-desc="([^"]*)"', node).group(1)
    if text in t or text in d:
        n -= 1
        if n == 0:
            b = list(map(int, re.findall(r'\d+', re.search(r'bounds="([^"]*)"', node).group(1))))
            print((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)
            break
PY
)
rm -f "$XML"
[ -n "$XY" ] || { echo "Not found on screen: $1"; exit 1; }
"$ADB" shell input tap $XY
echo "Tapped \"$1\" at $XY"
