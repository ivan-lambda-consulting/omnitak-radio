#!/usr/bin/env bash
set -euo pipefail

serial="${1:-}"
if [[ "$serial" != emulator-* ]]; then
  echo "Pass an emulator serial (for example, emulator-5554); this test must not target a phone." >&2
  exit 2
fi

adb="${ANDROID_HOME:?Set ANDROID_HOME}/platform-tools/adb"
timeout 120 "$adb" -s "$serial" wait-for-device
if [[ "$("$adb" -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
  echo "Refusing to install: $serial is not an emulator." >&2
  exit 2
fi
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon

cleanup() {
  "$adb" -s "$serial" shell wm size reset >/dev/null 2>&1 || true
  "$adb" -s "$serial" shell wm density reset >/dev/null 2>&1 || true
}
trap cleanup EXIT

"$adb" -s "$serial" shell wm size 240x320
"$adb" -s "$serial" shell wm density 160
"$adb" -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb" -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
result=$("$adb" -s "$serial" shell am instrument -w \
  -e class soy.engindearing.omnitak.mobile.ui.RadioButtonNavigationTest,soy.engindearing.omnitak.mobile.ui.RadioFormNavigationTest \
  com.ivanlambdaconsulting.omnitakradio.test/androidx.test.runner.AndroidJUnitRunner)
printf '%s\n' "$result"
grep -q 'OK (21 tests)' <<< "$result"
