# Task 8 Android calendar review

Date: 2026-11-03. Independent, task-scoped source review of the final eight-file `task-8-android.diff`, read once, against `docs/REQUIREMENTS.md`, `docs/DESIGN.md`, the task brief, implementation report and independent QA report. Production files were read only. Legacy AGENTS instructions were not applied.

## Verdicts

- **Specification: changes requested.** Geometry, persistence routing, conflict gates and gradient anchor handling follow the task contract in source, but block tapping and required grid status presentation have concrete gaps below.
- **Code quality: changes requested.** The pure geometry boundary and immutable resize candidates are sound choices. Gesture recognition currently conflates touching a resize region with performing a resize. No native compile, rendering or runtime approval is implied.
- **Acceptance: pending.** Fix the source findings, then obtain the still-blocked Android build/lint and device evidence before native acceptance.

## Ranked findings

### 1. P2 — Tapping the lower edge cannot open the appointment

`android/app/src/main/java/ua/zapys/ui/CalendarView.kt:81`, `:85`, `:87`–`:89`.

ACTION_DOWN sets `dragging` immediately for the bottom `min(10dp, height/3)` of every block. For a stationary DOWN/UP there, `preview` is null but `original` is non-null, so neither the resize branch nor the click branch runs. This makes the lower third of a short block a dead zone for the required block-tap editor action. A small MOVE can also produce a snapped resize candidate before the movement threshold is crossed, because candidate creation and the resize release branch do not check `moved`.

Distinguish a possible resize handle press from an actual drag: require movement beyond touch slop before preview/commit, and route a stationary release on a hit block through the normal click action. Preserve ACTION_CANCEL discard and explicit confirmation. Verify stationary grip taps, sub-slop jitter, deliberate drags, and cancellation using the actual View event path; geometry-copy tests cannot exercise this branch.

### 2. P2 — Colored grid blocks omit the required textual status

`android/app/src/main/java/ua/zapys/ui/CalendarView.kt:43`, `:48`–`:49`, `:68`.

The rendered rows contain only client and service names; the optional metadata row contains only start/end. No status is drawn even when an event has ample space. A custom client/service color also replaces the draft fill, so a colored draft and another status with the same labels and interval have identical grid content. DESIGN requires textual draft/status even when a color exists, and time/status when space permits. The agenda correctly includes `value.status.title`, but its separate representation does not supply the missing status inside the time grid.

Include a readable textual status in the event's metadata/compact representation, preserving true duration and client/service gradient anchors. Verify a tall colored draft and planned/completed/no-show events in both day and week modes, plus the clipped compact fallback. Do not enlarge event heights to make metadata fit.

## Strengths and checked integration

- CalendarScreen is actually wired into MainActivity's calendar branch, rather than leaving the tested helper unused. Day has one time-positioned column; week has seven equal Monday-first columns. Headers drill down, Today uses the local current date, and previous/next advances seven days. Header sits outside the timeline ScrollView.
- Drawing uses the 42dp gutter and 1.2dp/minute scale, with full-day extent and initial 09:00 scroll. Overlap layout excludes cancelled records, groups by date and connected intervals, reuses adjacent lanes and sizes groups by peak lane use.
- Empty taps create a blank 30-minute draft with quarter-hour snapping and a latest interval of 23:29–23:59. CalendarScreen passes it through the repository conflict gate before success announcement/editor entry. The separately inspected `SQLiteRepository.saveAppointment` checks conflicts before persistence. Declining the conflict prompt performs no insert.
- The New appointment button calls the existing unsaved editor route; inspected `AppointmentDraft.fresh` leaves its required end field blank. Existing-record taps route to the task 6 editor.
- Resize candidates are immutable, preview is separate from the supplied records, and ACTION_CANCEL clears it. Release and accessible end-time picker share explicit change confirmation followed by the repository's explicit overlap gate. A declined dialog makes no save call. Clamp and snap behavior retain `start < end <= 1439`.
- Gradient stops use the same actual font-metric logical centers as client/service drawing. Unset rows are omitted without compressing following anchors; hidden rows keep their centers below short clips. Shader endpoints preserve those centers instead of normalizing hidden rows onto visible labels. Zero/one/many colors and opaque theme-surface text backings are handled explicitly.
- The native agenda includes every visible-date record, full ordered service names, date/range/status, editor action and an accessible end-time picker. A persistent shortcut reaches it without manually scrolling the full-day canvas, including for dense and cancelled records.
- Named dependency inspection confirmed MainActivity's single-thread repository executor, generation/destroyed callback guards, busy-save input gating, close queued after outstanding operations, and Bundle storage for selected date/day-week mode. Back navigation keeps those calendar fields. This is source evidence, not an exercised rotation/lifecycle test.

## Verification limits

The supplied evidence records 66 passing offline domain tests, including four author calendar tests and nine independent calendar tests. These results were **not rerun** during this review, per task scope. The test harness does not compile Android Views. In particular, its immutable-copy/cancellation assertions do not simulate gesture dispatch or dialogs.

Android SDK/Views compilation, Gradle build, lint, APK, emulator/device execution, screenshots, actual gestures, accessibility traversal, dark/light and large-font rendering, persistence through UI operations, and rotation interaction remain **BLOCKED / NOT RUN**. No native pass or visual acceptance is claimed. Both findings above are source-path conclusions; native validation is still necessary after correction.

Dependency reads were limited to the concrete integration risks named above: MainActivity callbacks/state, Theme view/container construction, repository conflict/write ordering, and the editor draft's blank-end factory. No whole-branch review, production edits, commit, merge, SDK retry or unchanged test rerun was performed.

## Fix round 1 re-review

Date: 2026-11-03. Reviewed the four-file `task-8-fix1.diff` once and the appended implementation report. Scope was the two original P2 findings and new breakage introduced by this fix only; this was not another whole-task or branch review.

### Finding 1 — addressed in source

`android/app/src/main/java/ua/zapys/ui/CalendarView.kt:84`–`:98` now separates `possibleHandle` from `dragging`. A handle press reserves the event stream but does not create a preview. Vertical movement must exceed platform touch slop before an actual resize begins. A stationary or sub-slop release retains `original == null` and follows the ordinary block-open callback; candidate delivery remains confined to a real drag. Cancellation clears the possible handle, drag, preview and hit and releases parent interception. This resolves both the dead tap zone and sub-slop resize proposal identified in the initial review by source inspection.

The authored `CalendarViewInteractionTest` exercises stationary presses, jitter, deliberate drag/release, cancellation and a subsequent tap via `dispatchTouchEvent`. It is useful pending coverage, not executed evidence: no Android compilation or native event-path run occurred.

### Finding 2 — addressed in source

`android/app/src/main/java/ua/zapys/domain/CalendarPresentation.kt:7`–`:13` supplies status-first time metadata when a complete row fits and a status-prefixed client label otherwise. `android/app/src/main/java/ua/zapys/ui/CalendarView.kt:48`–`:51`, `:72` consumes that presentation in the actual drawing path. Colored records therefore no longer omit status from their text content. Client/service row count, indices, logical centers and gradient contribution order stay fixed; no event-height inflation was introduced. Compact text remains subject to the grid's existing clipping/truncation, with full text in the agenda.

The report records a focused RED/GREEN run of three `CalendarPresentationTest` cases for tall status metadata, compact status with unchanged anchors, and the fit boundary. These tests were not rerun here. Authored native draw-call fixtures include day/week and tall/compact records, but remain uncompiled and unrun; their wide week fixture and 1x1 recording Canvas do not establish normal-phone layout, clipping visibility or screenshot quality.

### Updated verdicts and limits

- **Specification: source review approved for this correction round.** Both original P2 findings are addressed in the reviewed implementation; no new actionable regression was found in the four-file fix.
- **Code quality: source review approved for this correction round.** Explicit potential/active drag state and the small presentation helper make the corrected behavior reviewable while retaining immutable candidates and logical label anchors.
- **Native acceptance: still pending / BLOCKED.** Source approval is not Android compilation, runtime or visual approval. SDK/Views compile, build/lint, device gestures, accessibility and rendered status readability remain unverified. The native fixtures launch no Activity and exercise no repository or database, so they also cannot establish persistence or dialog integration.

Only this review document was updated. No production edits, unchanged test reruns, broad dependency exploration, SDK retries or native-pass claims were made.
