#!/usr/bin/env bash
# One-time: copy the release signing key + passwords into this repo's encrypted GitHub Actions secrets,
# so the Release workflow can sign builds. Reads ~/.config/daymark/release.jks and ~/.gradle/gradle.properties.
# Nothing is printed. Re-run any time you change the key.
set -euo pipefail
KEYSTORE="${DAYMARK_KEYSTORE:-$HOME/.config/daymark/release.jks}"
PROPS="$HOME/.gradle/gradle.properties"
[ -f "$KEYSTORE" ] || { echo "Keystore not found: $KEYSTORE"; exit 1; }
[ -f "$PROPS" ] || { echo "Missing $PROPS"; exit 1; }
get() { grep "^$1=" "$PROPS" | cut -d= -f2- | tr -d '\n'; }
cd "$(dirname "$0")/.."
base64 -w0 "$KEYSTORE" | gh secret set DAYMARK_KEYSTORE_BASE64
get DAYMARK_STORE_PASSWORD | gh secret set DAYMARK_STORE_PASSWORD
get DAYMARK_KEY_ALIAS      | gh secret set DAYMARK_KEY_ALIAS
get DAYMARK_KEY_PASSWORD   | gh secret set DAYMARK_KEY_PASSWORD
echo "Done. Secrets now set:"
gh secret list
