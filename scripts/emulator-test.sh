#!/usr/bin/env bash
set -uo pipefail
mkdir -p emulator-artifacts
adb shell settings put secure location_mode 3
adb shell cmd location set-location-enabled true || true
adb emu geo fix 121.4737 31.2304 || true
set +e
gradle --no-daemon --stacktrace :app:connectedDebugAndroidTest
result=$?
adb logcat -d -s AndroidRuntime:E > emulator-artifacts/crash-log.txt
adb pull /sdcard/Android/data/io.github.meteorsss.gpstoolnew/files/screenshots emulator-artifacts/ || true
exit "$result"
