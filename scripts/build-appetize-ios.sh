#!/usr/bin/env bash
# Build the ARM iOS Simulator bundle accepted by Appetize. Run on macOS/Xcode.
set -euo pipefail
cd "$(dirname "$0")/.."
zapys_run="artifacts/appetize-ios-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$zapys_run"
xcodebuild -version
xcodebuild build \
  -project ios/Zapys.xcodeproj \
  -scheme Zapys \
  -configuration Release \
  -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath "$zapys_run/DerivedData" \
  ARCHS=arm64 ONLY_ACTIVE_ARCH=NO CODE_SIGNING_ALLOWED=NO \
  2>&1 | tee "$zapys_run/xcodebuild.log"
zapys_app="$zapys_run/DerivedData/Build/Products/Release-iphonesimulator/Zapys.app"
python3 - "$zapys_app" <<'PY'
import pathlib, plistlib, subprocess, sys
app = pathlib.Path(sys.argv[1])
with (app / "Info.plist").open("rb") as source:
    info = plistlib.load(source)
if "iPhoneSimulator" not in info.get("CFBundleSupportedPlatforms", []):
    raise SystemExit("Expected an iOS Simulator app bundle")
binary = app / info["CFBundleExecutable"]
subprocess.run(["xcrun", "lipo", "-verify_arch", "arm64", str(binary)], check=True)
PY
ditto -c -k --keepParent "$zapys_app" "$zapys_run/Zapys.app.zip"
test -s "$zapys_run/Zapys.app.zip"
printf 'Appetize upload file: %s/Zapys.app.zip\n' "$zapys_run"
