# Verification evidence — updated 2026-11-06

## Current Android UX execution baseline (2026-11-06)

This entry supersedes the earlier environment blockers below for this executing session.

- Feature branch `feat/android-ux-improvements`; preparation commit `e10f748`, product baseline `384f290`.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1`: exit 0, BUILD SUCCESSFUL. Actual Gradle XML: 13 suites, 84 tests, 0 failures, 0 errors. Lint: 0 errors, 16 warnings. App and instrumentation APK assembly passed with the pinned toolchain. The initial sandbox run failed dependency download with `Permission denied: getsockopt`; the approved retry passed.
- Installed Android Emulator 37.2.13 and API35 Google APIs x86_64 image revision 9 in ignored `.tools/`. Dedicated synthetic-data AVD `Zapys_UX_API35`, serial `emulator-5580`, API35, density 420; WHPX acceleration usable. No personal device/database was used. AVD creation printed missing `devices.xml` diagnostics but exited 0 and the emulator booted successfully.
- Existing `ua.zapys.CalendarViewInteractionTest`: PASS, 5 tests, 0 failures/errors on unchanged product baseline. Log: `artifacts/android-ux-2026-11-06/baseline-calendar-interactions-instrumentation.log`.
- Task 1 RED: `ua.zapys.CalendarHeaderLayoutTest`, one test, one expected failure: at 320dp day mode Monday.left expected 0, actual 110px (42dp spacer). Log: `artifacts/android-ux-2026-11-06/task1-red-instrumentation.log`.
- Task 1 first GREEN attempt exposed accumulated rounding in week headings (11 tests, 1 failure); preserved log `artifacts/android-ux-2026-11-06/task1-first-green-failure-instrumentation.log`. Independently rounded grid boundaries corrected it without weakening the 1px assertion. Final common build exited 0; 84 unit tests PASS, lint 0 errors/16 existing warnings, both APKs pass. Final device class pair: 11 tests PASS, exit 0; `task1-green-instrumentation.log` in the same artifacts directory.
- Task 1 rendering: 24 native-View fixture PNGs generated at 320/360/411dp, light/dark, font scales 1/2 and day/week; 16 directly inspected (all week renders plus narrow day renders). Seven cells fit; Monday left is 0 relative to selector; maximum measured heading-center drift 0.232px. Artifacts: `artifacts/android-ux-2026-11-06/task1-rendering/`. These are native fixture renders, not full-Activity screenshots. `/root/task1_review` independently approved spec and code quality, verifying saved test/coordinate evidence and four narrow enlarged-text renders; no suites rerun.
- Device runner is `artifacts/android-ux-2026-11-06/device-checks.ps1`; artifacts and SDK files are intentionally not committed. Full instrumentation suite, remaining UI matrices and all iOS checks have no new PASS.

## Earlier verification history

Update 2026-11-06: SDK35/Build Tools35 and Gradle8.11.1/AGP8.9.2 now compile the application and instrumentation sources with Kotlin2.1.20. Reproduced the Theme background receiver mismatch, fixed it with `this@Theme.background`, then added the missing platform test libraries (`android.test.runner/base/mock`). Final `--offline :app:assembleDebug :app:assembleDebugAndroidTest` completed with BUILD SUCCESSFUL, exit0; both debug APKs exist. Logs are in `artifacts/android-theme-fix-2026-11-06/`: `compile-before.log` (original failure), `build-after.log` (application compiles, JUnit resolution blocked), `cached-build.log` (missing test libraries and uncached lint dependencies), `apk-build-after.log` (both APKs pass). SDK XML/deprecation warnings remain; Kotlin daemon fell back successfully after a temporary-file error in the final run.

The complete build helper still requires a run with Maven access: JUnit4.13.2 and lint-gradle31.9.2 are not cached, and network access from this session is denied. Unit tests and lint have no new PASS. Existing84 offline results below remain evidence from November3. iOS simulator packaging, device execution and Appetize upload/runtime remain UNRUN.

Android APK compilation is verified; installed behavior and native test execution still await a device/emulator. iPhone compilation is still unverified. Source review and structural checks do not establish runtime behavior.

| Gate | Actual evidence | Status |
| --- | --- | --- |
| Android pure domain | Final frozen-fix-tree `./scripts/test-domain.ps1`: 84 JUnit tests passed with offline Kotlin 2.0.20 / JUnit 4.13.2, exit 0 | PASS, domain only |
| Pinned Android compiler/build | Application and instrumentation Kotlin2.1.20 sources compiled with AGP8.9.2/SDK35 | PASS, compilation only |
| Android APK / test APK | Both assemble tasks completed with exit0; both debug APKs generated | PASS, packaging only |
| Gradle Android unit tests / lint | JUnit4.13.2 and lint-gradle31.9.2 cannot resolve in this restricted session | BLOCKED |
| Android SQLite / View / lifecycle instrumentation | Synthetic fixtures authored, no emulator/device execution | UNRUN |
| Swift package / real SQLite XCTest | Tests authored; no Swift tool on this host | UNRUN |
| Xcode app / UI tests / simulator | Shared scheme and simulator script authored; Xcode absent on Windows | UNRUN |
| Native visuals / accessibility / permissions / installed messenger behavior | Manual matrices in platform QA files | UNRUN |
| CI YAML | SnakeYAML 2.0 safe parser rejects duplicate keys; four jobs parsed | PASS, structural only |
| Xcode project membership | 59 unique/resolved PBX IDs; all 14 app Swift sources occur once in actual app Sources and App group | PASS, structural only |
| XML / JSON / scripts | Five XML documents and two JSON resources parsed; PowerShell script ASTs parsed; shell files use LF | PASS, structural only |
| Source inventory | 105 UTF-8 source/tooling text files; only expected wrapper JAR and 1024px app icon binaries; selected credential-pattern scan clean | PASS, limited scan |
| Approved prototypes | Both SHA256 hashes unchanged (below) | PASS |
| Source reviews | Tasks 1–11 accepted; final whole-source review followed by one scoped re-review accepted F1–F6 with no new blocking source finding | ACCEPTED, source only |
| Git / publication | Baseline `4716bb6`, `feat/local-mvp`, source untracked; session policy keeps `.git` read-only. User confirmed `gh api user --jq .login` returns `antonkolosok` in ordinary PowerShell; a fresh identical call here returns HTTP401. Git/connector reads work; no commit/push/PR performed | BLOCKED in this session; user CLI login verified |

## Reproducible checks

From repository root on the prepared Windows environment:

```powershell
./scripts/test-domain.ps1
```

The offline script compiles only Android domain and domain test files with libraries bundled in Gradle. It does not compile Android framework/UI/SQLite code or test the configured Kotlin 2.1.20 plugin.

Final output after the consolidated source fix wave (exit 0):

```text
JUnit version 4.13.2
....................................................................................
Time: 0,367

OK (84 tests)

Offline domain checks passed (Kotlin 2.0.20); Android Gradle/SDK gates remain separate.
```

Targeted calendar regression had a recorded missing-contract compilation RED, followed by eight passing focused tests. That was an offline compiler result, not native Android execution. The new Swift deletion/reopen, 100/10,000-row reconciliation counts, unchanged-write triggers and accessibility tests are authored UNRUN. No native performance timing is claimed.

On a complete Android SDK35/JDK17 machine:

```powershell
./android/gradlew.bat -p android --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
./android/gradlew.bat -p android --no-daemon connectedDebugAndroidTest
```

The second command needs an emulator/device. Use synthetic data only. On macOS:

```bash
swift test --package-path ios
bash scripts/test-ios.sh
```

CI contains `android`, `android-device`, `ios-core`, `ios-app` jobs and retains reports, APK or xcresult/log artifacts. `android-device` uses an API35 Google APIs x86_64 emulator. No workflow run exists yet. The Ubuntu runner inventory and emulator action setup were inspected through their upstream repositories; availability is still checked at runtime. The emulator action is pinned to commit `a421e43855164a8197daf9d8d40fe71c6996bb0d` (resolved from upstream v2).

## Preserved reference hashes

| File | SHA256 |
| --- | --- |
| `zapys-demo.html` | `01D526B48C8F1E97078CBC25C4C6D505B8148D3928C4875AC6661420FE9BDEA1` |
| `zapys-calendar-preview.html` | `ACDC5D94518D35CE80DF69EF5D4D69A8FC6B3420A02ADA56FF24D8599F677023` |

## Limits and recovery

No real contacts, message delivery, permission prompts, merge or release were used as test evidence. Source checks do not prove absence of all secrets or defects. Temporary test databases and SDK/build outputs are excluded from publication.

The initial inventory assertion omitted the existing app icon; it was corrected to allow the known 1024px PNG and the audit reran successfully. This was an audit-script assumption, not an application failure.

Source snapshots and exact review diffs are retained locally because no implementation commit or native clean run exists. Git metadata protection is not bypassed. Previous SDK DNS failures and GitHub403 are not retried without changed access. Restoring a writable checkout, repository connection with actual contents/PR write access, and a native-capable build environment enables the remaining gates; no additional product specification is needed.
