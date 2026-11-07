# Android UX improvements — execution ledger

Plan: `docs/superpowers/plans/2026-11-06-android-ux-improvements.md`.
Spec: `docs/superpowers/specs/2026-11-06-android-ux-improvements.md`.

Prepared 2026-11-06. Observed baseline HEAD `384f290`; no implementation of this batch has been performed by the preparation chat. Recheck Git state before execution.

## Preflight

- Superpowers in executing local Codex: VERIFIED; required skill directories exist and coordinator/reviewer loaded actual applicable files.
- Feature branch: feat/android-ux-improvements created from 384f290 with approved Git escalation. Commit access VERIFIED by e10f748. GitHub CLI api user returned antonkolosok (exit 0).
- Fresh baseline: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1` PASS, exit 0, 84 tests across 13 suites, 0 failures/errors; lint and app/test APK assembly passed. Initial sandbox run failed JUnit download with `Permission denied: getsockopt`; approved retry succeeded. No connected device; dedicated emulator image installation is running, device checks UNRUN.
- Preparation/rules commit: e10f748 (`docs: record Android UX improvement plan and execution gates`).
- Aggregate PR: NOT CREATED; only after all ten task gates.

| Task | Deliverable | Status | Test evidence | Independent review | Commit |
| --- | --- | --- | --- | --- | --- |
| 1 | Monday/date-selector alignment | COMPLETE | 84 unit PASS; lint 0 errors/16 existing warnings; APKs PASS; 11 device PASS; measured + 16 render inspections | /root/task1_review: spec APPROVED, quality APPROVED | 283782e |
| 2 | Settings/nav alignment | COMPLETE | 84 unit PASS; lint 0 errors/16 original warnings; APKs; 4 nav + 3 config + 11 regression PASS; renders inspected | /root/task2_review: spec APPROVED, quality APPROVED | 17ba516 |
| 3 | Preset and custom-color palette | COMPLETE | 84 unit; lint 0 errors/16 original warnings; APKs; 10 color + 11 calendar PASS; 13 final images inspected | /root/task3_review: spec APPROVED, quality APPROVED | 14c2bdd |
| 4 | Ukrainian phone validation | COMPLETE | 92 unit PASS; lint 0 errors/16 original warnings; APKs; 35 device PASS | /root/task4_review: spec APPROVED, quality APPROVED | d4ac5c5 |
| 5 | Separate 50-character name limits | COMPLETE | 101 unit PASS; lint 0 errors/16 original warnings; APKs; 40 device PASS | /root/task5_review: spec PASS, quality APPROVED | aee0b0c |
| 6 | Contact picker at top | COMPLETE | 101 unit PASS; lint 0 errors/16 original warnings; APKs; 28 device PASS; four images inspected | /root/task6_review: spec APPROVED, quality APPROVED | 94bd905 |
| 7 | Recipient removal and selected-client precedence | VERIFIED; COMMIT PENDING | 106 unit PASS; lint 0 errors/16 original warnings; APKs; 35 device PASS | /root/task7_review: 7a/7b spec APPROVED, quality APPROVED | pending |
| 8 | Planned status by default | PENDING | — | — | — |
| 9 | Calendar move/drag | PENDING | — | — | — |
| 10 | Week mode by default | PENDING | — | — | — |

For each task append commands, exit codes, executed test counts, device details, reviewer verdict and actual commit SHA. Record blockers explicitly. Do not mark a task complete based only on authored tests, code review, compilation or another agent's unsupported claim.

### Task 1 execution evidence

- BASE e10f748. Implementer `/root/task1_implementer` loaded actual TDD/debugging/verification skills; no worker-created agents.
- Runtime RED command: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File artifacts/android-ux-2026-11-06/device-checks.ps1 -Action Test -TestClass ua.zapys.CalendarHeaderLayoutTest -Label task1-red`. Exit 1; one expected assertion failure, 0 errors: 320dp day mode Monday.left expected 0, actual 110px. Product code unchanged at RED.
- Dedicated synthetic AVD `Zapys_UX_API35`, API35, emulator-5580, density 420. Same runner with `-TestClass ua.zapys.CalendarViewInteractionTest -Label baseline-calendar-interactions`: exit 0, 5 tests PASS on unchanged baseline.
- Common baseline command `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1`: exit 0; 84 unit tests, 0 failures/errors; lint 0 errors/16 warnings; both APKs pass. Worker test-compilation run also exit 0.
- Corrected GREEN: common helper exit 0, 84 unit tests/0 failures/errors; lint 0 errors/16 existing warnings; app/test APKs pass. Exact class pair runner command with `-TestClass ua.zapys.CalendarHeaderLayoutTest,ua.zapys.CalendarViewInteractionTest -Label task1-green` exited 0, 11 tests PASS. Log `artifacts/android-ux-2026-11-06/task1-green-instrumentation.log`.
- 24 native fixture PNGs and measurements pulled; 16 inspected directly (all light/dark week width/font combinations plus narrow day). Monday.left 0; seven cells within 1px equal width; Sunday fits. Maximum week heading-center drift 0.232px; original <=1px tolerance retained. Images are native View renders, not full-Activity screenshots.
- Independent reviewer `/root/task1_review`: specification APPROVED, code quality APPROVED, no Critical/Important findings. Minor stale-status/log-path documentation findings corrected before commit. Full report `.superpowers/sdd/2026-11-06-android-ux-improvements/task-1-review.md`. Product commit: 283782e (`fix(android): align calendar date selector and week headings`).
- First GREEN attempt (2026-11-07 local): common Android gate passed; emulator class pair ran 11 tests with 1 failure/0 errors. Week heading at 320dp column 2 center was 347.5px versus grid center 348.73215px, exceeding the existing 1px tolerance. Preserved log `artifacts/android-ux-2026-11-06/task1-first-green-failure-instrumentation.log`. Same implementer corrected accumulated integer rounding; assertion unchanged, final result above.
- A worker emulator command was held at a permission prompt and ultimately aborted; it produced no runtime evidence. Coordinator now owns emulator commands. This was an execution delay, not a product test result.


## Execution preflight review and accepted rulings

# Android UX preflight review — 2026-11-06

Read-only product review. No builds, tests, Git mutations or product edits performed. This report is source/document evidence, not runtime verification.

Loaded actual `AGENTS.md`, Superpowers `using-superpowers` (subagent exemption applies), `subagent-driven-development`, its `references/preflight.md`, and `requesting-code-review`. Read the spec, plan, handoff, prepared ledger, status/verification documents and relevant Android source.

## Findings and rulings

Coordinator accepted the rulings below on 2026-11-06, including the narrow Task 9 snapshot-preserving persistence path. Authority is explicit: current user instructions/AGENTS take precedence over skill process defaults; the current specification takes precedence over implementation-plan omissions and existing code behavior. Thus spec section 9's preserve-all-fields requirement wins over naively reusing a save operation that refreshes snapshots. Coordinator separately reports branch `feat/android-ux-improvements` created at `384f290` and working CLI authentication; baseline checks and emulator setup are in progress, with no PASS asserted by this report.

1. **Important — Task 9 persistence currently changes snapshots.** Spec section 9 requires preservation of all non-date/time fields, explicitly snapshots. The plan mandates using existing transactional persist/ConflictException flow. `SQLiteRepository.saveAppointment` currently copies current client display name, phone and color onto the appointment before writing. Thus moving a historical appointment with differing stored snapshots changes metadata, even if `CalendarGeometry.move` is correct. Proposed ruling: preserve the existing transactional conflict/reminder machinery but add a narrowly scoped move operation that loads/validates the current record and changes only date/start/end. Extend Task 9 affected files to repository/interface as needed; do not globally alter editor save semantics. Add a persistence test with current client values deliberately different from appointment snapshots, including missing/legacy client cases appropriate to existing data rules. Cost if wrong: extra Task 9 implementation/review, rather than silent history alteration.
2. **Process precedence.** AGENTS requires tests and independent spec+quality review before the separate task commit. SDD's generic implementer-commits-first order does not apply. Proposed ruling: implementer supplies working diff/report; coordinator generates a review package that includes uncommitted task paths; after review/fixes/checks coordinator stages explicit paths and commits. Never claim a clean committed range excludes the actual uncommitted implementation. A blocked mandatory check/commit stops progression, overriding SDD continuous-execution language. Open Important/Critical findings cannot be parked at a skill cap to satisfy the stricter clean-review completion/PR gate.
3. **Ledger ownership.** Plan identifies durable `docs/superpowers/progress/2026-11-06-android-ux-improvements.md`; SDD expects its own ignored workspace ledger identity. Proposed ruling: durable project ledger remains authoritative for resumable task states/SHAs/checks; initialize the SDD workspace ledger with the required plan identity and a clear pointer, keeping task completion checkpoints synchronized. Do not delete the durable ledger when cleaning scratch artifacts. Record this report's rulings in the active ledger before implementation.
4. **Documentation file lists are minima.** Spec requires corresponding REQUIREMENTS and DESIGN updates in the task changing behavior. Per-task lists do not consistently list both. Proposed ruling: include both documents where their actual statements conflict (notably Task 7 recipient fields; Task 3 palette guidance), plus STATUS/ledger per task. Preserve explicit Android scope; do not overwrite iOS requirements silently. These are task-scoped documentation edits, not extra product tasks.
5. **Task 3 restoration scope.** 'Restore unsaved selection on activity recreation' must include a custom pending dialog selection, not merely the closed control's committed color. Proposed ruling: keep dialog preview separate, restore pending hue/SV/null/selection state without callback, and test rotation with dialog open as well as form after confirmation. Confirmation of a new choice fires once; render/restore/cancel never fires.
6. **Historical status is not current evidence.** Handoff/verification retain historical `384f290`, read-only Git and empty-remote observations. Plan correctly orders rechecking the actual session, so these are not inherent blockers. Proposed ruling: append dated current evidence and preserve the historical attribution; never reuse historical 84 tests as current PASS.

## Internal task consistency scan

| Task | Own text, specified code and checks | Finding / proposed ruling |
| --- | --- | --- |
| 1 | Independent full-width selector; separate gutter-aligned headings; measured widths/date callbacks and visual checks | Consistent. GUTTER remains 42f; test row/grid coordinates in the same coordinate space and preserve public CalendarScreen signature. |
| 2 | Container-local navigation alignment; adaptive common bounds; full Settings label at scale 2.0 | Consistent. A fixed 48dp row would fail text accommodation; 48dp is minimum, not exact height. All four route callbacks/insets require real UI checks. |
| 3 | Stateful shared control; four presets, custom hue/SV, null; unchanged public closure interface | Consistent with restoration ruling 5. Existing `identify(lastChild)` requires the last child to own/save all picker state under a stable ID. Existing arbitrary ARGB remains exact until a confirmed new opaque RGB choice. |
| 4 | Pure normalizer, full/inline/contact validation, duplicate comparison, storage formatting retained | Consistent. Reject national part beginning zero, not just all-zero example; validate new/edited client writes, never reads or appointment's old client. Only valid fixtures are migrated; intentional invalid/legacy fixtures remain. |
| 5 | 50 code points after trim per field; optional surname/patronymic; no truncation; focus first error | Consistent. Use helper across domain/UI/import/inline rather than maxLength UTF-16 filters. 'First' follows actual visible field order including phone. |
| 6 | Button moved before fields; bindContactFields still called after construction; synthetic lifecycle tests | Consistent. Provider ownership/cancellation stays in MainActivity, ordering change does not justify replacing provider architecture. |
| 7 | Both recipient removal and selected-ID precedence in one task; legacy JSON retained; retry fixture adapted | Consistent. `ReminderPreferences` reconstructed without recipient would erase old value; retain `original.reminder.recipient`. Hidden input must be ignored in error routing as well as prepare. |
| 8 | Fresh PLANNED only; explicit non-draft enum list; persisted/in-memory DRAFT retained on render | Consistent. Initial Spinner callback must not overwrite DRAFT; primary-save maps separately. Tests requiring old fresh-draft behavior request DRAFT explicitly, retaining assertions. |
| 9 | Immutable move geometry; long press vs resize; real ScrollView; transactional persistence and accessible action | Important snapshot conflict in finding 1. Extend affected files/checks before implementation; all metadata assertions must be at reopened repository boundary, not only pure copy. |
| 10 | Initial mode week; restoration/navigation retain intentional day/date; no preference needed | Consistent. Change initial state, not every render. Verify Task 1 heading layout plus Task 9 interaction under the default week mode. |

## Pair conflict scan

Every pair is included, including documentation-only sharing. All tasks also share the durable progress ledger and STATUS. Shared production implementation must run sequentially; earlier task tests remain regression coverage.

| Pair | Producer → consumer / shared files or interface | Finding / proposed ruling |
| --- | --- | --- |
| 1–2 | Calendar header and navigation → common available layout width; DESIGN | Compatible; measure each content coordinate space after navigation/insets. |
| 1–3 | Selector → Theme.button; palette → Theme.colors; DESIGN | Shared Theme surface only. Keep Task 3 localized to colors and preserve header button styling. |
| 1–4 | Calendar client display → validated client writes; shared docs/checks | No direct file conflict. Legacy phones remain readable in calendar. |
| 1–5 | Calendar client display → names potentially over 50 combined; docs | No direct conflict. Do not assume displayName has 50-character ceiling. |
| 1–6 | Calendar navigation vs client layout; shared ledger/STATUS only | No direct behavior dependency. |
| 1–7 | Calendar snapshots/reminders vs client identity; DESIGN | Preserve date/header behavior while editor/client changes. |
| 1–8 | Date tap creates persistent calendar DRAFT → editor status picker; DESIGN | Task 8 must preserve calendar DRAFT despite fresh-form PLANNED. |
| 1–9 | CalendarScreen/header column geometry → CalendarView horizontal move date hit-testing; DESIGN | Preserve gutter and actual dates; move uses grid columns, not full-width selector cell geometry. |
| 1–10 | Header selector/week headings → default mode; DESIGN | Compatible; week headings only week, full selector both. Run header regression after default change. |
| 2–3 | Navigation → Theme.button; picker uses same Theme file; DESIGN | Color replacement must not globally alter buttons or navigation bounds. |
| 2–4 | Insets/navigation → focusable validation fields; shared docs/checks | No direct production overlap; error focusing must remain usable above nav/IME. |
| 2–5 | Insets/navigation → name validation UI; docs/checks | No direct conflict; retain adaptive rows and readable enlarged fields. |
| 2–6 | MainActivity navigation/form ownership → contact form/provider; docs/checks | If Task 2 extracts navigation, preserve existing MainActivity contact lifecycle. |
| 2–7 | Navigation/route state → appointment and reminders screens; DESIGN | Preserve four route keys and route lifecycle while forms change. |
| 2–8 | Navigation editor opening → fresh status; DESIGN | No direct conflict; fresh status belongs draft factory, not navigation re-render. |
| 2–9 | MainActivity shell/insets → calendar scroll/drag viewport; DESIGN | Real ScrollView test must retain viewport/insets; do not move gesture behavior into bottom navigation. |
| 2–10 | MainActivity nav extraction → MainActivity calendarMode initial state; DESIGN | Same file, sequential. Task 10 preserves Task 2 nav integration and callbacks. |
| 3–4 | Theme.colors → ClientsScreen form state/validation | Shared ClientsScreen possible caller edits. Task 4 must preserve arbitrary color on failed validation/recreation. |
| 3–5 | Color form state → full client form name validation | Shared ClientsScreen possible. Invalid name must not reset color; no dirty callback from restored selection. |
| 3–6 | Form hierarchy IDs/picker → reordered contact action | Shared ClientsScreen. Preserve stable identified child ordering/state restoration after button relocation. |
| 3–7 | Color and reminder controls → recipient removal; ClientsScreen/DESIGN | Recipient removal must preserve color/pending form state and not shift restore identity incorrectly. |
| 3–8 | Shared field restoration → editor status callback; DESIGN | Separate controls. Neither initial callback may dirty/mutate restored form. |
| 3–9 | Arbitrary colors/client snapshots → moved calendar drawing; DESIGN | Move must preserve stored color, gradient and contrast; finding 1 applies. |
| 3–10 | Theme/header navigation/default mode; DESIGN | Documentation sharing only; existing color checks remain. |
| 4–5 | PhoneRules/error routing → PersonNameRules in Rules/ClientsScreen/AppointmentEditor/ClientFormValidationTest | Sequential shared validation. Name check ordering must keep phone routing correct; retain canonical duplicate comparison and unmodified formatting. |
| 4–6 | Validating imported phone → contact action/lifecycle; ClientsScreen | Same validated save path; moving button cannot bypass phone checks or normalize stored formatting. |
| 4–7 | Inline/full phone validation → authoritative selection; Rules/ClientsScreen/AppointmentEditor | Selected ID suppresses hidden invalid inline phone errors; blank-recipient change must not relax phone validation for actual client writes. |
| 4–8 | Fresh PLANNED requires client → existing/inline client validation; AppointmentEditor | Fixtures need valid full phone only when creating clients. Existing legacy invalid phone does not block appointment save. |
| 4–9 | Client write normalization → repository appointment move; REQUIREMENTS | Move is appointment update, must not revalidate or rewrite legacy client phone; finding 1 matters for preserved phone snapshots. |
| 4–10 | Phone validation vs initial week; shared ledger/STATUS only | No direct dependency. |
| 5–6 | Name limits → contact selected names; ClientsScreen/ClientFormValidationTest | Do not truncate imported over-limit names; preserve full text and reject/focus at save. |
| 5–7 | Inline-name validation → hidden selected-client fields; Rules/ClientsScreen/AppointmentEditor | Hidden over-limit strings retained but ignored; switching to inline re-enables same rule. |
| 5–8 | Required client/name → fresh PLANNED and explicit draft; AppointmentEditor | Preserve valid inline creation and explicit draft exceptions; do not alter model constructor status globally. |
| 5–9 | Client names/snapshots → move metadata; REQUIREMENTS | Do not revalidate legacy over-limit name as part of moving appointment or refresh stored name snapshot. |
| 5–10 | Name limits vs default mode; shared ledger/STATUS only | No direct dependency. |
| 6–7 | Reordered contact picker → recipient field removal; ClientsScreen | Keep contact action directly under heading/back and selective name/phone updates; recipient removal must not erase existing property. |
| 6–8 | Contact provider route ownership → appointment editor fresh status; shared docs/checks | No direct source overlap planned; retain pending provider ownership across editor navigation. |
| 6–9 | Client form contact lifecycle vs calendar move; shared ledger/STATUS only | No direct dependency; no real contacts accessed in gesture tests. |
| 6–10 | Contact return/recreation → MainActivity mode/date restoration | Intentional mode/date survives returning from client/contact flow where applicable; no reset on render. |
| 7–8 | Authoritative existing ID/save-session retry → filtered status picker; AppointmentEditor/DESIGN | Initialize status without invoking hidden client validation. Retry uses persisted inline client ID for PLANNED or explicit DRAFT. |
| 7–9 | Recipient-free manual reminder eligibility → moved appointment reminder rescheduling; REQUIREMENTS/DESIGN | Move tests after Task 7 use blank recipient, due/stale/cancelled rules and same transactional preservation. Never reopen obsolete recipient gate. |
| 7–10 | Selection/restoration state → MainActivity calendar restoration; DESIGN | Preserve separate inline draft/client state and selected date/mode across return. |
| 8–9 | DRAFT/non-draft persisted identities → move geometry/persistence; DESIGN | Move preserves status for all statuses including calendar DRAFT; never routes through fresh() or promotes to PLANNED. |
| 8–10 | Fresh appointment editor → return to intentional calendar mode/date; DESIGN | Opening fresh PLANNED form does not reset chosen calendar mode; cancel/back retains it. |
| 9–10 | Calendar week date columns/gesture → week default; DESIGN | Task 10 tests both fresh week and retained day; run Task 9 gesture/header checks afterward. |

## Package against AGENTS

The prepared package preserves all ten numbered task boundaries, task 7's two parts, test/review/commit gates, synthetic data, feature branch, one final unmerged PR and no release. Each task records acceptance criteria/files/checks before implementation. Both spec and plan forbid claiming device PASS from compilation. Handoff truthfully identifies preparation rather than implementation. No mandatory always-failing or assertion-free tests, mandated verbatim logic duplication, destructive migration, or new dependency/framework migration was found in plan text.

Apply the proposed rulings before dispatching Task 1; finding 1 must be carried explicitly into the Task 9 brief and affected files. Rulings 2–3 are operational requirements before the first task. This scan does not establish baseline device/build/Git availability; coordinator owns that evidence and stop gates.

### Task 2 execution evidence

- BASE 283782e; implementer /root/task2_implementer loaded actual applicable Superpowers skills. Acceptance/files/checks recorded in Task 2 plan and scoped brief before implementation.
- RED: standard common helper compiled the failing test, exit 0. Dedicated API35 emulator runner with `-TestClass ua.zapys.BottomNavigationLayoutTest -Label task2-red` exited 1: one test, one expected failure, 0 errors. At 320dp Clients button top was 0px while the first item's was 12px. Log `artifacts/android-ux-2026-11-06/task2-red-instrumentation.log`. Production Task 2 code unchanged at reproduction.
- Implementation, GREEN/configuration/render checks, independent review and separate commit pending.

### Remote preflight refresh

- GitHub connector confirmed repository default branch `main`, push access and existing MVP PR #1 already merged upstream before this batch. No PR exists yet for this Android UX batch. Read-only observation only; this session has not merged or published anything. Final aggregate PR gate remains all ten completed tasks.
### Task 2 final verification and cross-task finding

- Final factory build: common helper exit0, 84 unit tests/0 failures/errors, lint 0 errors/16 original warnings, both APKs. An intermediate custom subclass introduced a ViewConstructor warning; replacing it with the factory removed that warning without suppression. One intermediate compile error was corrected; neither intermediate build is final PASS evidence.
- Final device runs: BottomNavigationLayoutTest full class 4 PASS in portrait/gesture; attached inset test 1 PASS in each portrait/buttons, landscape/buttons, landscape/gesture. Task1 calendar regression 11 PASS. Logs `artifacts/android-ux-2026-11-06/task2-green-*-instrumentation.log`; latest Task1 regression uses `task1-green-instrumentation.log`.
- Native renders: 36 width/theme/font combinations plus four attached-screen configurations; 11 images inspected by implementer, including all four actual screenshots. Coordinates captured for real bottom/right system insets.
- Reviewer found Important evidence gap: calendar New Appointment action clips in landscape, causality unknown. Built exact BASE283782e from git archive in ignored scratch storage (assembleDebug exit0), then captured matched cold launches at rotation1,font1,density420 with gesture and buttons. Baseline/current PNG/XML files: `artifacts/android-ux-2026-11-06/task2-{baseline,current}-landscape-{gesture,buttons}`. Bounds identical in both pairs: gesture action [31,876][1889,891], navigation [0,891][1920,1017]; buttons action [31,876][1763,954], navigation [0,954][1794,1080]. Clipping is demonstrated before Task2 and unchanged by Task2. One screenshot transfer transiently failed; successful retry produced all four images.
- Ruling: carry this verified existing calendar viewport defect into Task9 calendar/ScrollView work and the final visual regression. The calendar New Appointment action and usable timeline must remain reachable in landscape and large text. Do not claim it fixed in Task2. Cost: scoped viewport work/tests in Task9, no extra numbered task or silent omission.
- Independent `/root/task2_review` scoped re-review verified all matched evidence: specification APPROVED, code quality APPROVED, no remaining Task2 blockers. Original source review and scoped evidence review are in `.superpowers/sdd/2026-11-06-android-ux-improvements/task-2-review.md`. Task2 commit: 17ba516 (`fix(android): align bottom navigation buttons`).

### Task 3 execution evidence

- BASE17ba516; implementer /root/task3_implementer loaded actual relevant Superpowers skills. Task3 brief carries approved scope and the open-dialog restoration ruling.
- Test-only common build passed. Actual RED runner class `ua.zapys.ColorPickerViewTest#testSharedControlShowsNamedSwatchAndPreservesArbitraryArgbWithoutDirtyCallback`, label task3-red: exit1, one expected failure,0errors: shared color control must show named visual swatch instead of HEX spinner. Product unchanged at RED. Log `artifacts/android-ux-2026-11-06/task3-red-instrumentation.log`.
- Implementation, common/device/render checks, independent review and separate commit pending.- First GREEN candidate: 8 color-control/activity tests PASS plus 11 calendar regression PASS; 12 native picker/gradient renders generated. Visual inspection found a real remaining Task3 defect: large-text dialog Cancel action clipped, dark radio rings low contrast. Root preserved evidence in `task3-first-rendering/` and `task3-first-green-instrumentation.log`; implementer adding meaningful visibility regression before fixing. These passing functional tests do not complete the visual gate; independent review has not started.- Follow-up live shown-dialog visibility check `ColorPickerActivityTest#testLargeTextDialogActionsRemainFullyVisible` PASS1/1 before any layout fix (`task3-large-red-instrumentation.log`, label does not imply failure). Implementer investigating discrepancy between exported render and live glyph/window bounds; no unproven production layout fix authorized.
- Dark native unchecked radio drawable regression reproduced: contrast1.1287513965484368 versus required3, one test/onefailure/0errors (`task3-dark-red-instrumentation.log`). Scoped tint fix in progress; full final GREEN/visual/review/commit remain pending.- Matched expanded-custom live test reproduced the large-text defect: 10 tests,1failure0errors; Cancel visible height67px versus measured142px. Other9 tests including corrected dark contrast passed. Log preserved `task3-expanded-large-red-instrumentation.log`; latest live screenshot is `task3-rendering/task-3/large-live-screen.png` (second adb directory pull nested by basename). Explicit action space/scrollable content correction in progress. Final rendering pull will use a fresh named directory to avoid stale/nested artifacts.
- FINAL Task3: coordinator standard helper exit0, BUILD SUCCESSFUL36s,84unit0failures/errors,lint0errors16originalwarnings,bothAPKs. Exact full class pair `ua.zapys.ColorPickerViewTest,ua.zapys.ColorPickerActivityTest` with labeltask3-green:10PASS,exit0,12.38s. Calendar regression11PASS. Final fresh13images `artifacts/android-ux-2026-11-06/task3-final-rendering/` all inspected; expanded actions fully visible and dark indicators readable. All earlier defects/tests remain documented, no assertion weakened.
- `/root/task3_review` independently verified final evidence/images and approved specification+quality, no blocking findings. Report `.superpowers/sdd/2026-11-06-android-ux-improvements/task-3-review.md`. Task3 commit: 14c2bdd (`feat(android): add visual preset and custom color picker`).
- Execution ruling: root/coordinator owns ALL builds and emulator commands. Worker combined search+build invocation was held at approval and aborted without running; exact root build then passed. Workers send readiness/filter details and inspect actual root results. Do not combine approved helper commands with searches/pipelines.
### Task 4 execution evidence

- BASE14c2bdd; implementer /root/task4_implementer loaded actual relevant Superpowers skills. Root owns all build/device commands.
- RED common helper exit1, BUILD FAILED22s,89unit tests4expected failures in ClientPhoneValidationTest: short client phone accepted, inline short phone accepted, equivalent0/+380 duplicate missed, invalid legacy phones treated as duplicate. Legacy appointment-read/save case passed. Product unchanged at RED. Saved XML `artifacts/android-ux-2026-11-06/task4-red-unit.xml`.
- Ruling: preserve accepted raw phone text including permitted edge spaces/NBSP on full/inline save; normalize only for validation/comparison, consistent with spec's preserve-user-formatting requirement. Names retain their existing trim rules. Add a formatted edge-space/NBSP save-reopen assertion; no legacy read normalization.
- Implementation, GREEN domain/device checks, independent review and commit pending.- Final common helper exit0, BUILD SUCCESSFUL25s,92tests/15suites/0failures/errors; lint0errors16originalwarnings; bothAPKs. Final affected device suite8classes35tests PASS,exit0,30.264s. Authoritative `task4-green-instrumentation.log`.
- First device run35tests1failure was a test locator issue, not accepted as PASS. Diagnostic observed native uppercase СКАСУВАТИ/ПІДТВЕРДИТИ at android:id/button2/button1; test now selects nativeID+case-insensitive expected label while retaining actual clicks and all cancel/no-write/confirm/distinctUUID assertions. Intermediate logs preserved.
- Two overbroad unit fixture replacements for existing selected clients100/200 were reverted before final unit gate to preserve invalid-legacy-selected-client coverage. Only fixtures reaching validated writes/new inline creation were migrated.
- Independent reviewer /root/task4_review: specification APPROVED, code quality APPROVED, no blockers/material evidence gaps. Inspected unit XML, lint/APKs and authoritative device log without rerunning suites. Review `.superpowers/sdd/2026-11-06-android-ux-improvements/task-4-review.md`. Task4 commit: d4ac5c5 (`feat(android): validate Ukrainian client phone formats`).

### Task 5 execution evidence

- BASEd4ac5c5; implementer /root/task5_implementer loaded actual relevant Superpowers skills. Root owns builds/device commands.
- Behavioral RED against existing Rules.validateClient: common helper exit1, BUILD FAILED31s,99tests5expected failures in PersonNameRulesTest (first/surname/patronymic51, supplementary code points, outer-trim boundaries). Optional fields/combined valid names passed. Product unchanged at RED. Saved XML `artifacts/android-ux-2026-11-06/task5-red-unit.xml`.
- Implementation/shared Unicode limit helper, actual typing/paste/import/recreation/error focus/legacy tests, GREEN, independent review and separate commit pending.- Final coordinator common helper captured exit0, BUILD SUCCESSFUL15s (up-to-date rerun after final build response was lost at context compaction). Unit XML16suites101tests0failures/errors; lint0errors16originalwarnings; both APKs. No source/test edits during verification.
- Exact affected9class device filter ClientFormValidationTest,ClientPhonePersistenceTest,ClientCrudTest,PersistenceTest,DevicePersistenceTest,DeviceClockPersistenceTest,AppointmentEditorPersistenceTest,ColorPickerActivityTest,ColorPickerViewTest:40PASS,exit0,58.059s. Authoritative artifacts/android-ux-2026-11-06/task5-green-instrumentation.log. Includes actual typing/paste/import/recreation/error focus, raw phone formatting and legacy snapshot preservation. Independent review pending.

- Independent /root/task5_review: specification PASS, quality APPROVED, no Critical/Important findings or material evidence gaps. Existing16lint warnings recorded as Minor debt. Task5 separate commit pending.

- Task5 complete in commit aee0b0c (feat(android): enforce fifty-character personal name limits); independent review clean. Task6 BASEaee0b0c, acceptance/files/checks in approved Task6 brief.
### Task 6 execution evidence

- BASEaee0b0c; implementer /root/task6_implementer loaded actual relevant Superpowers skills. Root owns build/device commands; no real contact provider access.
- Test-only common helper exit0, BUILD SUCCESSFUL39s. Actual RED runner ClientContactFormTest, label task6-red: exit1,2tests2failures0errors,3.441s. Both create/edit expected contact action directly after heading at child index2; actual19. Production unchanged at RED. Log artifacts/android-ux-2026-11-06/task6-red-instrumentation.log.
- Scoped action relocation and synthetic selection/cancel/provider-read-failure/recreation ownership checks in progress. Final GREEN, independent review and commit pending.
- Final common helper exit0, BUILD SUCCESSFUL56s;101unit tests/16suites/0failures/errors; lint0errors16originalwarnings; both APKs. Exact final device classes ClientContactFormTest,ClientFormValidationTest,ClientPhonePersistenceTest,ColorPickerActivityTest,ColorPickerViewTest with label task6-green:28PASS,exit0,59.729s. Log artifacts/android-ux-2026-11-06/task6-green-instrumentation.log.
- Downloaded24synthetic native form images with runner -Action PullRendering -TaskNumber6 -Label task6-final to artifacts/android-ux-2026-11-06/task6-final-rendering. Visual inspection and independent review pending. Provider-unavailable test means selected-provider read failure; no OS launch-exception simulation is claimed.
- Implementer directly inspected four narrow light/dark normal/large images named in task-6-report.md: full contact label, correct order, no clipping/overlap. Remaining renders generated/measured. Independent reviewer /root/task6_review dispatched.

- Independent /root/task6_review: specification APPROVED, quality APPROVED, no blockers. Read actual logs and four named native images; fixture limitations and existing warnings recorded. Separate Task6 commit pending.

- Task6 complete in commit94bd905 (fix(android): place contact picker at top of client form); review clean. Task7 BASE94bd905; both7a/7b acceptance/files/checks recorded in approved scoped brief.

### Task 7 execution evidence

- BASE94bd905; implementer /root/task7_implementer loaded actual relevant Superpowers skills. Both parts remain one task.
- Behavioral RED common helper exit1, BUILD FAILED42s,106tests7failures. Three selected-client/hidden-inline/retry failures; four blank-recipient validation/actionability failures across DeviceRulesTest and IndependentDeviceTest. Product unchanged. Saved XML in artifacts/android-ux-2026-11-06/task7-red-unit/. UI test APK assembly did not complete; no UI RED claimed.
- Product implementation, GREEN device/domain tests, independent review and separate commit pending.
- Final common helper exit0,BUILD SUCCESSFUL59s;106unit tests/16suites/0failures/errors;lint0errors16originalwarnings,bothAPKs. Final7class device run AppointmentClientSelectionTest,ClientContactFormTest,AppointmentEditorPersistenceTest,DeviceIntentTest,ClientFormValidationTest,DevicePersistenceTest,DeviceClockPersistenceTest:35PASS,exit0,48.594s. Authoritative artifacts/android-ux-2026-11-06/task7-green-instrumentation.log.
- Device checks cover whole inline-container visibility initial/recreated/toggled, retained raw drafts, selected-client saves, real SQLite conflict retry with same client ID, exact hidden legacy recipient, blank-recipient client save, and synthetic manual intent payload without recipient extras. No messenger launched or message sent. Independent review pending.

- Independent /root/task7_review approved specification7a/7b and code quality; no Critical/Important findings or acceptance evidence gaps. Reviewed unchanged stale/lifecycle and persisted-ID paths plus current XML/device evidence. Separate Task7 commit pending.
