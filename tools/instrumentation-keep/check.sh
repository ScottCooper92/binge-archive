#!/usr/bin/env bash
# Lists what the instrumentation test calls that the minified debug app does not carry, as keep
# rules for app/src/debug/keepRules/instrumentation.keep. Empty output means nothing is missing.
# Run from the repository root with ANDROID_HOME set.
set -euo pipefail
./gradlew -q :app:assembleDebug :app:assembleDebugAndroidTest -PminifyDebug
dexdump=$(ls -d "$ANDROID_HOME"/build-tools/*/ | sort -V | tail -n 1)dexdump
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q app/build/outputs/apk/debug/app-debug.apk 'classes*.dex' -d "$work/app"
unzip -q app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk 'classes*.dex' -d "$work/test"
here=$(dirname "$0")
python3 -I "$here/dexrefs.py" "$dexdump" "$work/app" "$work/test" | python3 -I "$here/torules.py"
