#!/usr/bin/env bash
# Run on macOS with Xcode; uses synthetic UI-test data in a separate database.
set -euo pipefail
cd "$(dirname "$0")/.."
zapys_run="artifacts/ios-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$zapys_run"
xcodebuild -version
swift --version
xcrun simctl list devices available --json > "$zapys_run/devices.json"
read -r zapys_sim_id zapys_sim_state < <(python3 - "$zapys_run/devices.json" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as source:
    data = json.load(source)
phones = [device for runtime, devices in data["devices"].items()
          if ".iOS-" in runtime
          for device in devices
          if device.get("isAvailable") and device["name"].startswith("iPhone")]
if not phones:
    raise SystemExit("No available iPhone simulator runtime")
phones.sort(key=lambda device: device.get("state") != "Booted")
print(phones[0]["udid"], phones[0]["state"])
PY
)
test -n "$zapys_sim_id"
if [ "$zapys_sim_state" != "Booted" ]; then
  xcrun simctl boot "$zapys_sim_id"
fi
xcrun simctl bootstatus "$zapys_sim_id" -b
xcodebuild test \
  -project ios/Zapys.xcodeproj \
  -scheme Zapys \
  -configuration Debug \
  -destination "platform=iOS Simulator,id=$zapys_sim_id" \
  -derivedDataPath "$zapys_run/DerivedData" \
  -resultBundlePath "$zapys_run/tests.xcresult" \
  CODE_SIGNING_ALLOWED=NO \
  2>&1 | tee "$zapys_run/xcodebuild.log"
