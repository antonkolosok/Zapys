# Android UX Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task with appropriate implementer and reviewer agents. Every created agent must load the relevant Superpowers skills. Steps use checkbox syntax for tracking.

**Goal:** Implement all ten user-requested Android UX changes with tests and a separate commit per task, followed by one unmerged aggregate PR.

**Architecture:** Keep Kotlin native Views, the existing domain rules, SQLiteRepository and manual messenger adapter. Separate the date selector from grid headings; centralize phone/name rules; keep gesture preview separate from persistence. Preserve existing data and draft semantics.

**Tech Stack:** Android SDK35/min26, Kotlin2.1.20, Java17, AGP8.9.2, Gradle8.11.1, JUnit4 unit tests and existing platform instrumentation runner.

**Spec:** `docs/superpowers/specs/2026-11-06-android-ux-improvements.md`. Read it before implementation. This plan does not authorize extra iOS work.

## Global Constraints

- All coordinator-created agents must load and apply the relevant Superpowers skills.
- Execute tasks 1–10 sequentially: relevant failing check, implementation, tests, independent review, separate commit, recorded SHA. One aggregate PR only after all ten pass. No merge or auto-merge.
- Keep Android minSdk 26, target/compileSdk 35, Java17, Kotlin2.1.20 and AGP8.9.2 unless a demonstrated blocker requires a separate explicit decision. Do not migrate UI frameworks or test frameworks for this batch.
- Use synthetic data. No real contact imports, external message sending, credentials in files, or destructive database migration.
- Preserve UUIDs, client/service snapshots, appointment history, payment, notes and reminder correctness. No silent data deletion, truncation or bulk normalization.
- Preserve light/dark themes, Ukrainian UI, accessible names/selected states, system insets and readable large text. No fixed heights that clip enlarged text.
- Date/time bounds remain 00:00–23:59 within one day. Moving a record preserves its duration. Conflicts require explicit confirmation and cancellation leaves original data untouched.
- Device/UI checks are distinct from compilation and offline domain tests. Record PASS/FAIL/BLOCKED/UNRUN honestly.

## Review Focus

1. Layout at narrow widths and enlarged fonts must not clip or mislabel dates: tasks 1–2 include measured-position and rendering checks.
2. Arbitrary saved colors and imported/legacy client data must survive recreation and validation errors: tasks 3–6 include these fixtures.
3. Hidden stale inline input must not override a selected client, including a failed-save retry: task 7 tests the save-session boundary.
4. Picker initialization must not silently convert stored drafts or shift status mappings: task 8 tests all enum choices and initial callbacks.
5. Drag inside a real ScrollView, out-of-grid release and failed/conflicting persistence must never corrupt an appointment: task 9 owns geometry, event, repository and reminder tests.

## Execution preflight — no product task completed yet

- [ ] Read `AGENTS.md`, the spec, `docs/STATUS.md`, and `docs/qa/verification.md`. Later status entries supersede older ones.
- [ ] Confirm Superpowers is available in THIS local Codex session. Expected relevant skills: using-superpowers, brainstorming, writing-plans, subagent-driven-development, systematic-debugging, test-driven-development, requesting-code-review, receiving-code-review and verification-before-completion. Use supported installation/connection if missing; never fake a skill invocation. The user already authorized execution of this batch; do not repeatedly ask to approve the unchanged ten-item scope.
- [ ] Run `git status --short`, `git log -3 --oneline`, `git branch --show-current`, `git remote -v`. On preparation, HEAD was `384f290` (`feat: add native local calendar MVP`), branch `feat/local-mvp`, with unrelated untracked `android/.idea/`. Preserve it and do not stage it. Re-read actual state instead of assuming this observation is current.
- [ ] Check CLI authentication and Git write permissions in the local session. User's ordinary PowerShell login works; HTTP401/read-only `.git` were observations of the previous isolated chat, not universal project blockers. Use legitimate approval requests for required Git commands; never bypass managed denials.
- [ ] Create/use one feature branch for the batch, suggested `feat/android-ux-improvements`, from the reviewed current MVP. Use an isolated worktree if supported and appropriate; do not create a second worktree inside an existing one. Never discard another chat's changes.
- [ ] Establish a test emulator/device with synthetic data only. Do not run database-resetting instrumentation against a phone containing real Zapys data. Verify baseline Android checks before implementation; dependencies may now download with approved network access. Record pre-existing failures separately.
- [ ] Open and maintain the prepared task ledger `docs/superpowers/progress/2026-11-06-android-ux-improvements.md`: each task records status, test commands/results, reviewer and commit SHA. The prepared ledger contains pending rows, not completion claims.
- [ ] Commit the approved rules/spec/plan/handoff/ledger as a preparatory documentation commit before task 1; exclude unrelated changes. This is not one of the ten product commits. Do not create a PR yet.

## Common test and commit gate

From the repository root in Windows PowerShell:

```powershell
. .\scripts\env.ps1
.\android\gradlew.bat -p android --no-daemon testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Required for each task: relevant behavior regression and Android checks above. For UI tasks run their device tests on the designated emulator. Existing runner supports a focused class using:

```powershell
.\android\gradlew.bat -p android --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ua.zapys.REPLACE_WITH_TASK_TEST_CLASS
```

Replace the class argument with the actual task test class; it is not a literal runnable fixture name. For the final gate omit the class property and run the full instrumentation suite. For pure-domain diagnosis, `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/test-domain.ps1` is an offline fallback, not a replacement for Gradle/native gates. Avoid overlapping Gradle builds in the same checkout.

Each task below ends with: run its tests; inspect actual results; independent spec+quality review; fix findings and rerun affected checks; stage only that task's files plus its ledger/documentation updates; commit with the provided message; record SHA before starting the next task. Do not use `git add .`, bypass failing tests, or report an unrun device test as PASS. A blocked commit/test stops progression at that gate.

## File responsibilities

Production files are under `android/app/src/main/java/ua/zapys/`: `MainActivity.kt` owns navigation/lifecycle; `ui/CalendarScreen.kt` composes controls and save confirmations; `ui/CalendarView.kt` draws and handles gestures; `ui/Theme.kt` contains shared form controls; `ui/ClientsScreen.kt` and `ui/AppointmentEditor.kt` own forms; `domain/Rules.kt`, `AppointmentDraft.kt`, `AppointmentSaveSession.kt`, `CalendarGeometry.kt`, `DeviceRules.kt` own behavior; `data/SQLiteRepository.kt` owns atomic persistence. Device tests live in `android/app/src/androidTest/java/ua/zapys/`, domain tests in `android/app/src/test/java/ua/zapys/domain/`.

### Task 1: Align Monday in the full-width date selector

**Modify:** `ui/CalendarScreen.kt`; `docs/DESIGN.md`. **Create:** `ui/CalendarDateHeader.kt` for independently measurable date-selector/grid-heading rows; device test `CalendarHeaderLayoutTest.kt`.

**Interfaces:** helper takes `Context`, `Theme`, selected `LocalDate`, mode, and day-selection callback; creates full-width selector and, in week mode, separate compact grid headings using `CalendarGeometry.GUTTER`. Keep CalendarScreen's public signature unchanged.

- [ ] Add `CalendarHeaderLayoutTest`: measure 320/360/411dp; Monday left equals selector content left; seven equal cells within one pixel; Sunday fits; week heading centers match `gutter + (index+0.5)*(width-gutter)/7`; day view has no fake grid gutter in selector. Check week crossing Dec/Jan and day-selection callback.
- [ ] Reproduce the current failure caused by the unconditional 42dp empty cell. Implement the distinct rows specified in the spec, preserving selected/today semantics and grid geometry.
- [ ] Run `CalendarHeaderLayoutTest`, existing `CalendarViewInteractionTest`, common Android gate and light/dark visual inspection. Record measured/rendered evidence and independent review.
- [ ] Commit: `fix(android): align calendar date selector and week headings`.

### Task 2: Keep Settings aligned in bottom navigation

**Modify:** `MainActivity.kt`; `docs/DESIGN.md`. **Create:** `ui/BottomNavigation.kt` if needed to construct/test the nav independently; device test `BottomNavigationLayoutTest.kt`.

**Interfaces:** nav accepts current route and `(String) -> Unit`; preserve four route keys and full labels. Scope alignment changes to this control.

- [ ] Add measured-layout tests at 320/360/411dp and font scales 1.0/1.3/2.0: all four item top/bottom bounds match, labels fit, height is at least 48dp, Settings callback uses `settings`, selected semantics survive route changes.
- [ ] Reproduce baseline-aligned/wrapped-label behavior. Disable baseline alignment and use common adaptive height/vertical alignment; preserve insets, readable labels and callbacks.
- [ ] Run `BottomNavigationLayoutTest`, common gate; inspect portrait/landscape and gesture/three-button system navigation. Independent review must distinguish measured evidence from an unrun screenshot check.
- [ ] Commit: `fix(android): align bottom navigation buttons`.

### Task 3: Replace HEX spinner with visual color palette

**Modify:** `ui/Theme.kt`, client/service callers only if required, `docs/DESIGN.md`. **Create:** `ui/ColorPickerView.kt`, `ui/ColorPaletteDialog.kt`, device test `ColorPickerViewTest.kt`.

**Interfaces:** preserve `Theme.colors(parent: LinearLayout, initial: Int?, onChange: (Int?) -> Unit): () -> Int?`. The stateful picker exposes selected `Int?` and saves/restores it; dialog preview is separate from committed selection.

- [ ] Add tests for each of four existing presets, custom hue/saturation/value confirmation, cancel, clear-to-null, arbitrary existing color and view-state round trip. Confirm initial render/restore/cancel fires no change callback and confirmation fires once.
- [ ] Implement visual swatches, preset names/selection semantics, `Без кольору`, `Власний колір`, custom picker and preview. No HEX-only selected label. Keep the returned control usable by existing `identify`/hierarchy-state handling.
- [ ] Run picker device tests and client/service save-reopen checks, common gate and black/white/custom-color gradient readability checks in light/dark/large text. Independent review.
- [ ] Commit: `feat(android): add visual preset and custom color picker`.

### Task 4: Validate Ukrainian client phone numbers

**Create:** `domain/PhoneRules.kt`, domain test `PhoneRulesTest.kt`. **Modify:** `domain/Rules.kt`, `domain/FormRules.kt`, `ui/ClientsScreen.kt`, `ui/AppointmentEditor.kt`, validated-client fixtures, `docs/REQUIREMENTS.md`. Device test: `ClientFormValidationTest.kt`.

**Interfaces:** `PhoneRules.normalize(raw: String): String?` returns canonical comparison form or null. Preserve formatted storage and duplicate-client UUID behavior.

- [ ] RED examples: `0671234567`, `+380671234567`, `380671234567`, `00380671234567`, `+380 (44) 123-45-67` normalize successfully; reject blank, `123`, `067123456`, `06712345678`, `+48123456789`, `abc0671234567`, `++380671234567`, `0671234567 ext 1`, all-zero national part and unbalanced parentheses. Equivalent `0…`/`+380…` duplicates match; self ID is excluded.
- [ ] Implement strict character/prefix/length checks with shared error routing; inline phone uses phone input type. Use normalization in duplicate comparison. Contact input and repository client writes use the same validation. Never run validation destructively while reading old records.
- [ ] Replace short synthetic phones only where valid-client fixtures require it; retain intentional invalid cases. Run all domain tests and device validation/save tests, common gate; verify failed save preserves input/focus and does not write a client. Independent review.
- [ ] Commit: `feat(android): validate Ukrainian client phone formats`.

### Task 5: Limit each personal-name field to 50 characters

**Modify:** `domain/Models.kt`, `domain/Rules.kt`, `ui/ClientsScreen.kt`, `ui/AppointmentEditor.kt`, `docs/REQUIREMENTS.md`. **Create:** `domain/PersonNameRules.kt`, domain test `PersonNameRulesTest.kt`; extend `ClientFormValidationTest.kt`.

**Interfaces:** `Limits.MAX_PERSON_NAME = 50`; `PersonNameRules.length(raw: String): Int` counts code points after trim. Domain and UI use the same helper and field-specific messages.

- [ ] RED tests for each field: 50 passes, 51 rejects; blank optional fields pass, blank first name rejects; combined name length may exceed 50; Ukrainian apostrophe/hyphen, surrounding whitespace and supplementary-code-point boundaries count correctly.
- [ ] Implement save validation and visible limit/error guidance in full and inline forms. Do not silently truncate imported, pasted or stored values. Preserve existing names until a deliberate corrected save.
- [ ] Run all domain tests and device typing/paste/import/recreation cases, common gate; ensure first failing field receives focus. Independent review.
- [ ] Commit: `feat(android): enforce fifty-character personal name limits`.

### Task 6: Move the contact picker above client fields

**Modify:** `ui/ClientsScreen.kt`. **Create/extend:** device test `ClientContactFormTest.kt`; use existing `MainActivity` contact lifecycle and `device/ContactPicker.kt` without replacing the provider flow.

**Interfaces:** keep `pickContact()` and `bindContactFields(name,phone)`; binding occurs after fields exist even though the button is added earlier.

- [ ] RED layout assertion: contact button precedes name/phone and follows heading/back. Cover create and edit forms.
- [ ] Move the action and preserve existing binding/lifecycle. Add synthetic callback fixtures for selection, cancel, unavailable provider and recreation ownership; selected result only changes name/phone.
- [ ] Run contact form/device tests and phone/name regressions from tasks 4–5, common gate. Verify manual entry remains usable and no real address book is accessed. Independent review.
- [ ] Commit: `fix(android): place contact picker at top of client form`.

### Task 7: Remove recipient UI and make selected client authoritative

**Modify:** `ui/ClientsScreen.kt`, `ui/RemindersScreen.kt`, `ui/AppointmentEditor.kt`, `domain/Rules.kt`, `domain/DeviceRules.kt`, `domain/AppointmentSaveSession.kt`, `docs/REQUIREMENTS.md`, `docs/DESIGN.md`. **Tests:** `AppointmentSaveSessionTest.kt`, `DeviceRulesTest.kt`, `IndependentDeviceTest.kt`, `DeviceIntentTest.kt`; create device test `AppointmentClientSelectionTest.kt`.

**Interfaces:** preserve legacy recipient schema/JSON, `prepare(requestedStatus)` and `clientPersisted(client)`. Introduce no recipient-routing API. Existing selected ID must win over inactive inline text.

- [ ] RED domain tests: enabled reminder + blank recipient is valid/actionable when due; stale/future/cancelled/handed-off reminders remain blocked. Selected existing client plus invalid/nonblank inline text produces no new client and keeps selected ID. Clearing selection activates inline validation; post-client-save retry reuses ID.
- [ ] Remove field and recipient-presence gating for manual share. Preserve stored legacy values and explain recipient selection happens in messenger. Put inline labels/fields in one visibility-controlled container; initial state and recreation derive visibility from selected client; inactive raw values survive toggling.
- [ ] Update affected fixture `successiveInlineCreationsKeepDistinctIdentityAndFirstClientUnchanged` to clear `draft.clientId` deliberately before a second inline creation. Run full domain, due-reminder/device-intent, editor selection and save-retry tests plus common gate. Verify no actual message is sent. Independent review of both 7a and 7b.
- [ ] Commit: `fix(android): simplify recipient and client selection forms`.

### Task 8: Default fresh appointments to Planned

**Modify:** `domain/AppointmentDraft.kt`, `ui/AppointmentEditor.kt`, `docs/DESIGN.md`. **Tests:** `AppointmentEditorTest.kt`, `IndependentEditorTest.kt`; create device test `AppointmentStatusPickerTest.kt`.

**Interfaces:** set `AppointmentDraft.fresh()` explicitly to PLANNED; retain constructors/from/repeat/calendar-DRAFT behavior. Picker uses explicit non-draft enum list and a separate displayed selection for stored drafts.

- [ ] RED tests: fresh status PLANNED, end still blank/required, client required for primary save; explicit requested DRAFT still supports no client. Existing non-draft statuses round-trip. Rendering a draft does not mutate it. Every displayed choice maps to the correct enum after filtering.
- [ ] Implement normal status list without placeholder/DRAFT, preserve separate draft identity and explicit/back draft-save actions. Do not globally change `Appointment.status` defaults or repeat semantics.
- [ ] Make draft-specific old tests request DRAFT explicitly; retain calendar-draft and repeat assertions. Run domain/status device tests and common gate, including failed-save/recreation state retention. Independent review.
- [ ] Commit: `fix(android): default new appointments to planned status`.

### Task 9: Move calendar appointments with drag and accessible action

**Modify:** `domain/CalendarGeometry.kt`, `ui/CalendarView.kt`, `ui/CalendarScreen.kt`, `docs/REQUIREMENTS.md`, `docs/DESIGN.md`. **Tests:** `CalendarGeometryTest.kt`, `CalendarViewInteractionTest.kt`; create device tests `CalendarScrollGestureTest.kt`, `AppointmentMovePersistenceTest.kt`.

**Interfaces:** `CalendarGeometry.move(original: Appointment, date: LocalDate, deltaMinutes: Float): Appointment` returns an immutable date/time-only copy. Add a named/defaulted move callback to CalendarView without accidentally reinterpreting existing positional resize/preview callbacks. Keep persistence and dialogs in CalendarScreen.

- [ ] RED geometry tests: 10:00–11:00 +30min becomes 10:30–11:30; delta 7 snaps 0, 8 snaps 15; date-only move of 10:07–11:07 preserves minutes; Dec/Jan date change works; bounds preserve duration; original and all non-date/time fields remain unchanged. Reject non-finite delta/invalid original duration deliberately rather than corrupting coordinates.
- [ ] RED native tests: short tap/jitter opens once; long-press body drag previews then emits only move on changed release; resize handle still resizes; date-only movement works; no-op/cancel/off-grid/gutter/multitouch does not mutate; next tap works. Use a real ScrollView fixture for scrolling arbitration and edge scroll; the existing FrameLayout fixture alone is insufficient.
- [ ] Implement explicit gesture states, pointer/finger offset, immutable preview, parent interception ownership, cancellation cleanup and edge-scroll cleanup. Add accessible `Перенести запис` date/time controls using the same move function. No automatic next-week navigation.
- [ ] Add drop confirmation with date/time and use existing transactional `persist`/ConflictException flow. Persistence tests cover reopen with same ID, unchanged metadata, overlap rejection, explicit override exactly once, failure rollback and reminder rescheduling. No SQLite writes during preview.
- [ ] Run full domain and named device tests, existing resize suite and common gate. Inspect drag in day/week, scroll/cancel behavior, accessibility and early/late records. Independent review must cover interaction and data integrity together.
- [ ] Commit: `feat(android): move appointments within calendar`.

### Task 10: Start the calendar in Week mode

**Modify:** `MainActivity.kt`; `docs/DESIGN.md`. **Create:** device test `CalendarDefaultModeTest.kt`.

**Interfaces:** initialize `calendarMode` to `week`; retain existing `onSaveInstanceState` and restoration, with no unconditional reset in render/navigation.

- [ ] RED tests: fresh launch selects Week/seven columns; switching to Day works; recreation and return from editor/settings preserve selected mode/date.
- [ ] Change the default and any reset sites that incorrectly override restored state; do not add an unnecessary preference store.
- [ ] Run default-mode/header/gesture device tests and common gate, then independent review.
- [ ] Commit: `fix(android): open calendar in week mode by default`.

## Final verification and the single PR

- [ ] Verify ten task rows have actual PASS evidence, independent review and distinct commit SHAs. Documentation-only setup commit does not replace a product task commit.
- [ ] Run full Gradle unit/lint/APK/test-APK commands and full `connectedDebugAndroidTest`; perform the named visual/manual matrix on a synthetic-data device. Run one independent whole-branch review and fix genuine issues with tested follow-up commits.
- [ ] Update `docs/STATUS.md`, `docs/qa/android.md`, `docs/qa/verification.md` and `docs/PR_DESCRIPTION.md` with the actual new result, date, APK location and remaining iOS limitations. Do not reuse the old 84-test result as fresh proof.
- [ ] Check remote and PR base. A previously empty remote may still lack an appropriate base; do not silently initialize/overwrite `main`, force-push, or create a PR against the feature branch itself. Request a specific base-branch decision only if it is genuinely missing; local tested task commits can still exist while publication is blocked.
- [ ] Push the feature branch and create exactly one aggregate PR after all task gates pass. Suggested title: `Improve Android calendar and client forms`. Include all ten outcomes, test evidence and screenshots when available. Leave PR open and unmerged; no auto-merge or release.
