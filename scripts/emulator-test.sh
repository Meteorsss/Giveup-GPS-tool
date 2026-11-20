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
adb pull /sdcard/Pictures/GPS-Tool-New-test emulator-artifacts/screenshots || true
if [ "$result" -eq 0 ]; then
  adb uninstall io.github.meteorsss.gpstoolnew || true
  adb install release-smoke/*.apk || exit 1
  adb shell pm grant io.github.meteorsss.gpstoolnew android.permission.ACCESS_FINE_LOCATION
  adb shell pm grant io.github.meteorsss.gpstoolnew android.permission.ACCESS_COARSE_LOCATION
  adb shell pm grant io.github.meteorsss.gpstoolnew android.permission.POST_NOTIFICATIONS
  adb logcat -c
  adb shell am start -W -n io.github.meteorsss.gpstoolnew/.MainActivity || exit 1
  sleep 5
  adb shell pidof io.github.meteorsss.gpstoolnew || exit 1
  mkdir -p emulator-artifacts/screenshots
  adb exec-out screencap -p > emulator-artifacts/screenshots/07-signed-release-home.png
  adb logcat -d -s AndroidRuntime:E > emulator-artifacts/release-crash-log.txt
  if grep -q 'FATAL EXCEPTION' emulator-artifacts/release-crash-log.txt; then exit 1; fi
fi
exit "$result"
