#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.example.busiscoming"
AVD_NAME="${AVD_NAME:-Pixel_8_API_36}"
OUT_DIR="${HOME}/Desktop/appmock截图"
DEVICE_DIR="/sdcard/Pictures/BusIsComingDemoScreenshots"
FILES=(
  "home-favorites-results.png"
  "home-all-routes-sheet.png"
  "route-detail-expanded.png"
  "eta-arrivals-sheet.png"
  "lockscreen-monitor.png"
)

cd "${ROOT_DIR}"
mkdir -p "${OUT_DIR}"
rm -f "${OUT_DIR}"/*.png

if ! adb get-state >/dev/null 2>&1; then
  emulator -avd "${AVD_NAME}" -no-snapshot-load >/tmp/busiscoming-demo-emulator.log 2>&1 &
  adb wait-for-device
fi

until [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == "1" ]]; do
  sleep 2
done

adb shell input keyevent 224 >/dev/null 2>&1 || true
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell cmd uimode night no >/dev/null 2>&1 || true
adb shell settings put system font_scale 1.0 >/dev/null 2>&1 || true

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.example.busiscoming.DemoScreenshotInstrumentedTest \
  --no-daemon

for file in "${FILES[@]}"; do
  adb pull "${DEVICE_DIR}/${file}" "${OUT_DIR}/${file}" >/dev/null
  test -s "${OUT_DIR}/${file}"
  file "${OUT_DIR}/${file}" | grep -q 'PNG image data'
done

printf 'Generated demo screenshots:\n'
for file in "${FILES[@]}"; do
  printf '  %s/%s\n' "${OUT_DIR}" "${file}"
done
