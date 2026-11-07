# Final whole-branch Android UX review

Final disposition (2026-11-07): **APPROVED for the single unmerged PR**. The original review below found one validation blocker; the appended independent scoped review closes it after the test-only fixture correction, focused PASS and full 104-device-test PASS. Specification and code quality are approved. Unit results:111 passing; lint:zero errors/16 baseline warnings. The earlier NOT READY assessment is historical, superseded by the final disposition and appended verdict. No merge or release authorization.

Reviewed range: `384f290..a5563b4`, using the supplied final review package. Scope: preparatory documentation plus the ten separately committed Android UX tasks. This is a PR-readiness review, not merge or release authorization.

## Strengths

- The ten changes remain within native Kotlin/Views and the existing SQLite schema. No framework, dependency, SDK, schema-version or iOS migration is present in the reviewed package.
- `SQLiteRepository.kt:94` gives movement a narrow transaction: compare the stored original, preserve duration, check conflicts, update only date/start/end JSON keys and reconcile reminders atomically. It retains foreign keys, ordered service rows, historical client snapshots, payment and manual-end state. Tests inject a reminder INSERT failure after the appointment UPDATE and verify rollback after reopening; stale-edit and exact clamped-start regressions are separate tests.
- `CalendarView.kt:15` distinguishes pending taps, body movement and resizing, and retains the existing positional callback contract. Real attached ScrollView tests exercise ordinary scroll arbitration, day/week movement, odd-minute horizontal moves, extra pointers, both scroll edges and cleanup. CalendarScreen supplies the same confirmation/transaction path to the accessible action and drag, with lifecycle dialog cleanup.
- `PhoneRules.kt:7` and `PersonNameRules.kt:4` centralize pure validation. Full/inline/imported client saves share those rules, raw accepted phone formatting survives storage, and legacy client data remains readable. Existing appointment operations do not revalidate old client names/phones.
- `AppointmentSaveSession.kt:10` enforces selected-client authority at the save boundary, beyond UI visibility. `AppointmentEditor.kt:38` hides the complete inactive container and retains its draft text. Failed appointment-save retry retains the already-created client ID. These interactions have domain, actual Activity recreation and SQLite coverage.
- `ColorPickerView.kt:46` separates committed form selection from pending dialog state and restores both without a dirty callback. Presets, null, arbitrary existing ARGB, new opaque HSV choices, accessible native sliders and exact-once confirmation are covered alongside client/service save-reopen paths.
- `AppointmentEditor.kt:65` maps an explicit non-draft status list and separates displayed Planned from a stored draft's identity. Fresh Planned, explicit/back draft saves, repeat resets and non-draft round trips are covered. `MainActivity.kt:50` changes only the fresh calendar mode while retaining the existing mode/date lifecycle state.
- Recipient removal retains `original.reminder.copy(...)` in `ClientsScreen.kt:49`; legacy JSON survives and manual sharing does not invent a phone-derived recipient or claim delivery. Schedule, stale-ID, terminal-state and lifecycle gates remain in place.

## Specification assessment

| Task | Whole-branch result |
| --- | --- |
| 1: Monday/full-width selector | Meets horizontal geometry and separate week-heading requirements; measured alignment/callback coverage retained. See nonblocking viewport limitation below. |
| 2: Bottom navigation | Common adaptive bounds, full labels, selected state and system-inset handling retained; width/font/theme and attached-window evidence recorded. |
| 3: Visual colors | Shared caller interface, presets/custom/null, transactional confirmation, recreation, accessible controls and readable gradient text covered. |
| 4: Ukrainian phones | One canonical comparison rule, strict accepted character/prefix/length bounds, formatted storage, duplicate identity and legacy behavior covered. |
| 5: Personal names | Trimmed Unicode code-point limits per field, optional surname/patronymic, no truncation and ordered form errors covered. |
| 6: Contact action | Action directly follows heading/back; existing selective result ownership and manual-entry paths retained. |
| 7a/7b: Recipient/selected client | Both requested parts implemented together; legacy recipient preserved and selected client wins over hidden drafts, including retry/recreation. |
| 8: Planned default | Fresh form only; explicit enum mapping, stored draft identity and separate draft/repeat paths retained. |
| 9: Move/viewport | Immutable gesture preview and accessible path converge on snapshot-preserving transaction; no-op/cancel/failure/conflict/lifecycle behavior covered. New Appointment and timeline remain reachable in the measured small-window cases. |
| 10: Week default | Fresh Week plus retained Day/date through recreation/editor/settings return covered. |

The accepted exact-start persistence ruling is technically justified: re-snapping an already-clamped preview would turn 1379 into 1372. The stored-original comparison and deriving duration from that original keep this narrower API safe without changing ordinary editor-save semantics. Raw-phone preservation and pending-color-dialog restoration rulings match the binding specification.

## Issues

### Critical

None found.

### Important

1. **Full regression is blocked by an unattached-Activity lifecycle fixture.** `android/app/src/androidTest/java/ua/zapys/ManualOpenLifecycleTest.kt:23` directly constructs `MainActivity()` and line 31 reflectively invokes `onPause`. Reflection does not bypass the superclass call: the unchanged `MainActivity.kt:34` invokes `super.onPause()`, which dispatches through the framework-attached Application. The actual full-run XML records a null-Application crash in `Activity.dispatchActivityPaused`, reached through those exact lines. The run ended with 95 of 104 planned tests represented, one failure, zero recorded errors/skips, and a process-crash system error; it is not a passing regression run. This is a demonstrated test-fixture failure, not evidence of a launched product Activity crashing. Convert the fixture to a framework-attached Activity with controlled lifecycle transitions, retain both assertions (a delayed paused request is dropped and a fresh resumed request succeeds once), avoid external launches and ensure failure-safe latch/fixture cleanup. Do not remove/filter the test, weaken its assertions or alter production lifecycle behavior to accommodate an unattached test object. Verify the focused class, then the complete instrumentation suite and appropriate common checks before the PR gate.

### Minor

1. **Calendar date context and selector discoverability need refinement.** `android/app/src/main/java/ua/zapys/ui/CalendarScreen.kt:33`, `:48` and `:57` cap the controls region and place two full-width actions ahead of date context. This affects more than small landscape/font2: the supplied normal portrait `task10-final-rendering/fresh-week.png`, captured after revealing the Week switch, also exposes only the top slice of the independent date-selector row. The landscape/font2 Day capture shows the two actions but no current date until controls are scrolled. Native scrolling keeps the controls reachable, the Week headings retain date columns, and the actual action/timeline reachability defect is repaired, so this is nonblocking. A follow-up should keep a compact selected-date indicator visible and improve the controls-scroll affordance; document the ordinary-portrait case as well as landscape.
2. **Baseline lint debt remains.** `android/app/build/reports/lint-results-debug.txt` reports zero errors and 16 warnings. These were recorded at baseline and throughout the batch; this review found no justification for suppressing them or expanding this batch into unrelated cleanup. Describe the result as passing with warnings.

## Evidence inspected and limits

- Actually loaded repository AGENTS.md and the Superpowers using-superpowers, requesting-code-review, code-reviewer template, verification-before-completion and final-review instructions. Read the binding specification/plan, accepted rulings, durable task ledger, all changed production paths, relevant surrounding Activity/repository/drawing code, and the behavioral/regression test changes. Used the provided package; did not regenerate a Git diff.
- Parsed current unit XML directly: 16 suites, 111 tests, zero failures and zero errors. Read current lint summary directly: zero errors and 16 warnings. Coordinator records the final common unit/lint/app/test-APK helper exit 0, BUILD SUCCESSFUL in 1m23s; this reviewer did not rerun it.
- Read final Task 9 device summaries directly: normal 37 tests / 72.721s; font2 6 tests / 41.016s; explicit landscape/font2 2 tests / 18.207s, each `OK`. Read final Task 10 summary: 22 tests / 23.722s, `OK`. Ledger and independent task reports preserve the earlier RED results, repaired harness errors and final gates rather than treating failed attempts as passing evidence.
- Personally opened `task10-final-rendering/fresh-week.png`, `task9-verified-rendering/landscape-font2.0.png` and `task9-verified-rendering/move-confirmation-landscape-font2.0.png`. The landscape confirmation exposes the actual target date/range and complete Cancel/Save actions. Detailed per-task visual inspections remain attributed to their implementers/reviewers; this is not a claim to have personally opened every generated image.
- Final full `connectedDebugAndroidTest`: **FAILED**, coordinator-observed exit 1 / BUILD FAILED in 4m35s. Read `final-android-regression.log` terminal failure and the actual `android/app/build/outputs/androidTest-results/connected/debug/TEST-Zapys_UX_API35(AVD) - 15-_app-.xml`: 95 tests, one failure, zero errors/skips, 147.384s; the XML system-error section explicitly says the process crashed and supplies the null-Application stack. The runner had planned 104 tests, so remaining cases were not completed. This supersedes the earlier partial-progress observation. Coordinator is preserving the failure artifacts before rerunning.
- Loaded the actual systematic-debugging skill for that concrete failure. Traced the XML stack to the detached fixture and unchanged superclass lifecycle call, and compared the other working ActivityInstrumentationTestCase2 fixtures. No additional reproduction or mutation was performed by this reviewer; the full-run failure supplies the observed reproduction and the coordinator owns the focused validation/fix.
- No builds, device operations, subagents, Git mutations or production changes were performed by this reviewer. Only this scratch review report was written. The coordinator owns final evidence-only STATUS/QA/HANDOFF/ledger/PR-description updates and their commit.

## Declined to judge

- iOS parity, build and runtime behavior: this batch explicitly targets Android; iOS is unchanged and was not run locally on Windows.
- Real address-book/provider contents and real messenger sending/delivery: the approved checks use synthetic fixtures and prohibit real contact imports or message sends. Existing selective-provider and manual-intent boundaries were inspected, without claiming external end-to-end execution.
- Every Android/OEM/API configuration and a hands-on TalkBack session: supplied evidence targets the dedicated API35 emulator and named width/font/theme/orientation cases. Native accessible names/states/actions are covered; universal device compatibility is not established by that evidence.
- Toolchain/bootstrap and historical unrelated development-process defects: outside this product review; no changed toolchain/dependency files occur in the package.
- Changing ordinary editor saves to preserve historical snapshots: the binding ruling deliberately scopes that guarantee to calendar movement. This review verified the separate move path; it does not reinterpret unchanged editor snapshot refresh semantics as a task in this batch.

## Assessment

**Production specification: APPROVED. Production code quality/integration: APPROVED, with the nonblocking limitations above. Overall validation: NOT APPROVED until the Important lifecycle-fixture finding is resolved.** One test-fix wave is required; no production defect or production change is requested by this review.

**Ready for the single unmerged PR: NO, pending the lifecycle-fixture correction, successful complete regression, scoped fix re-review and final evidence-documentation gate.** The implementation is coherent and the inspected tests address the main data, lifecycle and interaction risks, but the actual full-run failure is a mandatory stop gate. Do not merge, enable auto-merge or release.
- **Unattached-Activity lifecycle fixture crashes the full regression** — ADDRESSED. `android/app/src/androidTest/java/ua/zapys/ManualOpenLifecycleTest.kt:10` now uses the framework-attached ActivityInstrumentationTestCase2 fixture; `:27` obtains its real Activity, and `:40`/`:47` call actual Instrumentation pause/resume callbacks. Private resumed/repository injection and direct MainActivity construction are removed. The production lifecycle implementation is unchanged.
- **Preserve delayed-paused rejection, fresh resumed retry and safe cleanup** — ADDRESSED. `ManualOpenLifecycleTest.kt:39` ensures the blocked request started before pause; `:42`–`:44` drain the serialized IO/main callback queue and assert zero launches while paused. `:47`–`:51` resume, issue a fresh request and require exactly one callback. `:53` releases the worker on every exit; `:55`–`:57` drain and balance pause; `:65`–`:67` let real Activity destruction close the repository/shut down IO and assert executor termination. The callback only increments a counter; there is no composer launch, assertion removal, filtering or production workaround.
- **Generated private test-prefixed SAM methods collide with JUnit3 discovery** — ADDRESSED. `ManualOpenLifecycleTest.kt:19`–`:22` move the unchanged empty Runnable barrier outside the test-prefixed method namespace. The corrected complete-class focused run executes exactly one intended test.

### New Breakage in the Fix Diff

- None found. UI-thread exception transport at `ManualOpenLifecycleTest.kt:13`–`:16` preserves failed-test reporting without an uncaught UI-thread assertion crash. The barrier and controlled lifecycle ordering remain deterministic for the callback gate under review.

### Out-of-Scope Observations

- None added. The earlier whole-branch review's nonblocking date-selector discoverability limitation and 16 baseline lint warnings remain outside this fixture fix.

### Checks Inspected

- Loaded actual repository AGENTS.md and Superpowers using-superpowers, requesting-code-review, verification-before-completion and SDD re-review-prompt.md. Read the supplied fix package once, prior final review, implementer fix report and relevant unchanged Activity/working-fixture context. No whole-branch re-review, build, device operation, subagent or Git mutation was performed.
- Read `artifacts/android-ux-2026-11-06/final-lifecycle-fix-instrumentation.log` directly: complete `ua.zapys.ManualOpenLifecycleTest` class, `OK (1 test)`, 2.271s. The earlier discovery-failure run remains a failed attempt; this final evidence supersedes the authored report's pending status.
- Parsed current connected test XML directly: `android/app/build/outputs/androidTest-results/connected/debug/TEST-Zapys_UX_API35(AVD) - 15-_app-.xml` has 104 tests and 104 testcase elements, zero failures/errors/skips, 183.506s and no system-error text. The intended lifecycle case is present once. Read the runner script: it invokes unfiltered `connectedDebugAndroidTest` with the common unit/lint/app/test-APK tasks on the designated synthetic emulator.
- Read `artifacts/android-ux-2026-11-06/final-terminal-evidence.txt`, which preserves the coordinator's terminal observation: final unfiltered command exit 0, `Finished 104 tests`, `BUILD SUCCESSFUL in 3m 46s`. The coordinator reports final common rebuild exit 0, `BUILD SUCCESSFUL in 1m 53s`. Attribution is explicit: the PowerShell transcript contains only header/footer, so this reviewer does not claim it contains the Gradle terminal output.
- Parsed current unit XML directly: 16 suites, 111 tests, zero failures/errors. Read current lint report directly: zero errors, 16 baseline warnings. These are passing-with-warnings results, not a zero-warning claim.

### Verdict

- **Specification: APPROVED. Code quality: APPROVED. Validation: APPROVED for this fix.**
- **Fix round: All findings addressed, no new Critical/Important breakage.** The prior final review's Important lifecycle-fixture validation blocker is closed by the attached fixture and successful focused/full execution. The coordinator still owns the task commit and final evidence-documentation/aggregate unmerged-PR gate; this verdict does not authorize merge, auto-merge or release.
