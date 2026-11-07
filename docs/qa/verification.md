# Verification evidence — updated 2026-11-07

## Task 6 test and visual evidence (2026-11-07)

- RED actual create/edit ordering tests:2tests2expectedfailures0errors,contact child index19 instead of2; product unchanged. Common helper compiled tests successfully. Log artifacts/android-ux-2026-11-06/task6-red-instrumentation.log.
- Final common helper exit0,BUILD SUCCESSFUL56s;101unit0failures/errors,lint0errors16originalwarnings,bothAPKs. Dedicated syntheticAPI35 runner classes ClientContactFormTest,ClientFormValidationTest,ClientPhonePersistenceTest,ColorPickerActivityTest,ColorPickerViewTest:28PASS,59.729s,exit0. Authoritative task6-green-instrumentation.log under the same artifacts directory.
- Eight contact cases cover create/edit ordering, intercepted ACTION_PICK with synthetic result, cancellation, selected-provider read failure and manual save, actual recreation/pending ownership/raw metadata/stable IDs/open palette, stale draft owner,24light/dark width/font/create/edit renders. No actual contact provider launch/read; unavailable launch exception is preserved but not simulated.
- Implementer inspected light320/font1/create,light320/font2/edit,dark320/font1/edit,dark320/font2/create in task6-final-rendering. Correct order and full contact label; large text wraps into two visible lines without overlap/clipping. Other renders generated and measured, not claimed directly inspected. /root/task6_review independently inspected the four images/logs and approved specification and quality; no blockers.

## Task 5 final test evidence (2026-11-07)

- Behavioral RED: existing Rules.validateClient accepted five over-limit cases; common helper exit1,99tests5failures. Saved task5-red-unit.xml contains7Task5tests5expectedfailures0errors; no product edits before RED.
- Final common command: powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1, exit0,BUILD SUCCESSFUL15s (captured repeat because prior completion response was lost). Actual XML16suites101tests0failures/errors; lint0errors16originalwarnings; app/testAPKs.
- Dedicated synthetic API35 emulator-5580 runner with9affected classes:40PASS,exit0,58.059s. Authoritative artifacts/android-ux-2026-11-06/task5-green-instrumentation.log. New cases exercise actual51character typing/native clipboard paste,50/51 supplementary Unicode boundaries, focus ordering, synthetic imported names, actual full/inline Activity recreation, legacy overlong client reads/corrected save and preserved appointment snapshot. No real contacts or messages.
- Independent /root/task5_review: specification PASS, quality APPROVED, no material evidence gaps. Commit tracked in active ledger.

## Task 4 final verification (2026-11-07)

- Initial behavioral RED: common helper ran 89 tests with four expected failures for short phone writes/inline creation and canonical/invalid duplicate comparison; product unchanged. Saved `artifacts/android-ux-2026-11-06/task4-red-unit.xml`.
- Final standard helper exited 0, `BUILD SUCCESSFUL in 25s`; final XML has 15 suites/92 tests/0 failures/errors. Lint 0 errors/16 original warnings; app/test APKs built. Two existing-selected-client fixtures with invalid legacy phones were restored before the final gate; valid-write fixtures alone were migrated.
- Eight affected device classes ran 35 tests PASS in 30.264s, exit 0: ClientFormValidationTest (4), ClientPhonePersistenceTest (2), ClientCrudTest (2), PersistenceTest (9), DevicePersistenceTest (4), DeviceClockPersistenceTest (2), AppointmentEditorPersistenceTest (3), ColorPickerActivityTest (9). Log `artifacts/android-ux-2026-11-06/task4-green-instrumentation.log`.
- Device checks exercised invalid full/inline/contact saves, focused errors and retained raw input, zero invalid writes, edge-space/NBSP formatting after save/reopen, canonical duplicate cancel/confirm with distinct UUIDs, and unchanged invalid legacy client data with usable appointments. Contact results are synthetic; no real address book or messages accessed.
- An initial device failure was traced to native uppercase button accessibility labels. The test locator now uses native button ID plus expected case-insensitive text; all behavioral assertions remain. Diagnostic/first-run logs preserved. `/root/task4_review` independently inspected the evidence and approved both specification and code quality without rerunning suites.

## Task 3 final verification (2026-11-07)

- Coordinator's exact common helper exited 0 (`BUILD SUCCESSFUL in 36s`), 84 unit tests with 0 failures/errors, lint 0 errors/16 original warnings, app and test APKs. Existing platform test API deprecations remain; no lint suppression or framework migration.
- Final `ColorPickerViewTest,ColorPickerActivityTest`: 10 tests PASS, exit 0; actual framework activity recreation covers pending open HSV/null/latent hue and confirmed form state. Tests exercise presets/custom touch and native accessible sliders, confirm/cancel/null, callback count, arbitrary stored ARGB, hierarchy state and client/service save-reopen. Final calendar header/interaction regression: 11 PASS.
- Recorded REDs: original HEX spinner lacks named swatch; dark native unchecked ring contrast 1.12875 instead of at least 3; expanded custom dialog Cancel visible height 67px instead of 142px. The initially collapsed-content visibility test passed and was not misreported as RED. Matching expanded content reproduced the real defect; final unchanged visibility and contrast assertions pass.
- Authoritative final logs: `artifacts/android-ux-2026-11-06/task3-green-instrumentation.log` and latest `task1-green-instrumentation.log`. Authoritative final renders: `task3-final-rendering/` under the same directory, 12 native light/dark normal/enlarged picker/gradient images plus `large-live-screen.png`. All 13 directly inspected by implementer and independently reviewed. Earlier `task3-first-rendering/` and nested `task3-rendering/task-3/` preserve failed/intermediate evidence, not final output.
- `/root/task3_review` approved specification and code quality with no blocking findings. Full review and implementation report are in the plan's ignored SDD workspace; durable task status/commits are in the active progress ledger.

## Task 2 final verification (2026-11-07)

- Standard build helper exited 0: 84 unit tests, 0 failures/errors; lint 0 errors/16 original warnings; application and instrumentation APKs built. Intermediate subclass lint warning removed by a navigation factory; no suppression. Final artifact was rebuilt and all device configurations repeated after that correction.
- `BottomNavigationLayoutTest`: 4 PASS in portrait/gesture. `testAttachedNavigationStaysInsideSystemInsets`: 1 PASS each in portrait/buttons, landscape/buttons, landscape/gesture. Existing calendar header/interaction classes: 11 PASS. Commands and logs are in the active ledger and `artifacts/android-ux-2026-11-06/task2-green-*-instrumentation.log`.
- 36 native navigation fixture images generated across light/dark, widths 320/360/411/640/720/822dp and font scales 1/1.3/2; seven fixture images and all four actual orientation/navigation screenshots inspected. Text, common bounds, 48dp minimum, selected semantics and every callback have measured/interaction coverage. Actual window insets were 63/126px bottom or 126px right, with navigation wholly outside them.
- Review's landscape Calendar clipping question resolved with exact BASE `283782e` APK built from an ignored source archive. Matched current/baseline cold launches (rotation1/font1/density420) have identical action and nav bounds in gesture and buttons configurations. Evidence `task2-{baseline,current}-landscape-{gesture,buttons}.{png,xml}` under the same artifacts directory. Existing calendar defect remains required Task 9/final-regression follow-up. `/root/task2_review`: spec APPROVED, quality APPROVED; no remaining Task 2 blockers.

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
