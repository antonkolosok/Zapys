# Whole-source final review — 2026-11-03

## Scope and evidence

Reviewed the supplied `final-source.diff` in bounded passes, recovering truncated output windows, plus the review package and `docs/superpowers/plans/2026-11-02-local-mvp.md`. The source snapshot covers Android and iOS production code, tests, build/project files, tooling and current documentation against empty baseline `4716bb6`. This is an uncommitted source review, not a review of a fabricated commit range. No Git diff was regenerated, no production files were changed, and no tests/builds/installations or external messaging were run by this reviewer.

The reported 83 Android offline tests establish only their recorded pure-domain result. Android framework compilation, lint, APK/test APK, instrumentation, and every Swift/Xcode/XCTest/native runtime gate remain UNRUN/BLOCKED. Findings below distinguish directly visible source defects from runtime outcomes that have not been observed.

## Strengths

- Both platforms retain UUID identity, ordered detached service snapshots and optional integer money. Unknown prices and free prices remain distinct, and overflow-sensitive totals use wider exact arithmetic.
- Save/conflict handling retains immutable candidates; cancelled overlap/resize confirmation does not intentionally write a preview. SQLite rollback and disk-reopen fixtures exercise substantive contracts rather than merely repeating implementation expressions.
- Reminder handoff is labelled honestly. Explicit preparation revalidates current IDs/eligibility, expired actions are rejected, and Android activity/iOS scene epochs protect delayed launch callbacks. Notification bodies omit client details, and manual entry/queues remain available without notification permissions.
- Calendar geometry preserves real durations, adjacent intervals and overlap lanes. Gradient code retains unset/hidden service positions instead of compressing colors into the visible block.
- Current verification documents clearly distinguish authored fixtures, structural checks and actual execution. CI contains separate native compilation/device/simulator gates; the absence of their results is not hidden.

## Findings

### Critical

None established in this source review.

### Important — fix before source acceptance

**F1 — P1: Android lifecycle fixture calls a protected method from an unrelated class.**

- Locations: `android/app/src/androidTest/java/ua/zapys/ManualOpenLifecycleTest.kt:30`; `android/app/src/main/java/ua/zapys/MainActivity.kt:34`.
- `activity.onPause()` is called directly, while the Kotlin override inherits the protected visibility of `Activity.onPause`. A same-package test is not a subclass and Kotlin protected access is not package access. This is a concrete source-level instrumentation compilation concern; no native compiler failure has been observed on this host. Since CI builds `assembleDebugAndroidTest`, it affects the native gate before the fixture can run.
- Remedy: invoke the lifecycle transition through a legal instrumentation/testing mechanism (for example `Instrumentation.callActivityOnPause`) or a deliberately scoped test adapter. Do not widen the production lifecycle method merely to satisfy this fixture. Retain the delayed-callback/drop-and-retry assertions and the disclaimer that this manually driven fixture does not prove real Home/rotation ordering.
- Validation: compile the instrumentation APK when the SDK is available, then run this fixture. Offline domain success cannot resolve this gate.

**F2 — P2: iOS permits client deletion that leaves existing appointments uneditable.**

- Locations: `ios/Sources/ZapysCore/SQLiteRepository.swift:200` and `:113`; `ios/App/ClientsView.swift:70`.
- `deleteClient` removes a referenced client and retains each appointment's `clientID`. Every subsequent appointment save, including a payment-only edit or calendar resize, then fails the existing-client guard with `notFound`. The calendar still shows the historical name, but the client card/history route has disappeared and the record cannot be saved without replacing/removing its client. A deletion confirmation does not explain this consequence. Android already rejects referenced-client deletion.
- Remedy: inside the deletion transaction, reject deletion when any appointment references the client, using a clear Ukrainian validation message. Preserve the client, appointments and reminders unchanged. Unreferenced-client deletion should continue to work. This is a bounded restriction, not a request for an archive or soft-delete feature.
- Validation: add synthetic real-SQLite cases for referenced deletion rejection, unchanged records/outbox after reopen, successful subsequent appointment/payment edit, and successful unreferenced deletion. Swift execution remains required later.

**F3 — P2: iOS ordinary refresh performs quadratic full-table decoding at the supported local data size.**

- Locations: `ios/Sources/ZapysCore/SQLiteRepository.swift:129`, `:143`, `:152`, `:159`, `:168`, `:189`; caller `ios/App/AppModel.swift:43`.
- `reminders()` loops through all appointments, and each `refresh` decodes the entire reminders table and clients table again. It also writes unchanged eligible reminders. The same whole-store refresh runs during startup, after every model write, and before manual preparation/state changes; timezone reconciliation repeats the reminder scan too. At the declared 10,000 appointment/reminder ceiling this entails about 100 million reminder decodes for one ordinary reconciliation, excluding client decodes and unnecessary writes. This is directly visible algorithmic work on the sole DB queue, not a measured native latency claim. It can delay loading, save completion and reminder actions as the user's history grows.
- Remedy: use transaction-local snapshots indexed by appointment/client ID, or keyed reminder lookup plus a cached client map, across reconciliation; write only changed rows. Apply the same bounded approach to timezone rebasing and action validation. Preserve current transaction boundaries, one-reminder identity, cancellation reasons, stale-ID rejection and terminal outcomes; no schema redesign or background-service expansion is needed.
- Validation: retain existing restoration/timezone/rollback fixtures and add a meaningful synthetic scaling or query/decode-count regression showing that a batch reconciliation no longer rescans all reminders per appointment. Actual startup/action latency still needs native measurement; do not invent a timing pass on Windows.

**F4 — P2: compact Android week appointments omit their time.**

- Locations: `android/app/src/main/java/ua/zapys/domain/CalendarPresentation.kt:13`; `android/app/src/main/java/ua/zapys/ui/CalendarView.kt:48` and `:50`.
- When metadata does not fit, presentation returns only status/client and service labels. There is no week-aware time fallback. A normal 30-minute week record therefore omits the time required by `docs/DESIGN.md`; its long status prefix can consume the narrow title as well. The iOS compact-week implementation already addresses the analogous case.
- Remedy: provide a compact week start-time line plus abbreviated textual status/client within the existing first logical row height. Keep true event height and service-row positions unchanged. Anchor the client color to the actual client text center when the first row is split, rather than retaining the old whole-row midpoint. Preserve day behavior unless deliberately updated with equivalent requirements.
- Validation: cover compact/tall day and week presentations, all statuses, unchanged service anchors, mixed unset colors, and the changed client anchor. Update the native drawing fixture to assert time drawing as well as status. Native narrow-screen/font-scale rendering is still UNRUN.

**F5 — P2: iOS calendar accessibility omits required context and selected-day semantics.**

- Locations: `ios/App/AppointmentBlock.swift:52`; `ios/App/CalendarView.swift:38` and `:68`.
- A block's explicit accessibility label ignores children but omits the appointment date and explicit service count. In week view two same-time records on different days therefore lack distinguishing date context when individually focused. The agenda does display dates/services but also omits an explicit count. Day-header selection is communicated only through color; its label/traits do not identify the selected date. These are explicit design requirements, not optional visual polish.
- Remedy: include client, date, start/end, draft/status and service count in the block and agenda appointment semantics; preserve complete service names where useful. Add selected semantics to the current day header (and an appropriate today indication), keeping agenda edit and end-time actions separately accessible.
- Validation: author focused semantics assertions for date/count/selection and retain action-isolation tests. Verify actual VoiceOver traversal and large text on iOS later; source labels alone are not rendered accessibility evidence.

### Minor

**F6 — P3: current UI/API text still describes completed integrations as future work.**

- Locations: `ios/App/SettingsView.swift:22`; `android/API.md:11`, `:33`, `:39`; `ios/API.md:34`, `:37`, `:47`, `:48`, `:56`.
- Settings says permission and reminder queue will arrive at the next stage, directly beside the working queue route. API sections say the calendar is unimplemented/only an agenda and contacts/reminders are deferred; the Android timezone paragraph says only nonterminal rows are rebased, contradicting the newer terminal-preservation contract. iOS also retains a ten-app-file membership statement beside the current fourteen-file audit. These are current reference documents, not archived review findings.
- Remedy: replace stale product copy with the present manual/local behavior. Rewrite or clearly mark superseded API sections as historical and make current signatures, calendar/device behavior, timezone semantics and membership counts consistent. Preserve honest UNRUN evidence and do not rewrite historical review reports as if native checks passed.
- Validation: a targeted text/readback check is sufficient; update final status/count wording only after the controller's actual final verification.

## Recommendations and bounded follow-up

Apply F1–F6 as one consolidated fix wave, then one scoped re-review of the changed paths and regressions. The source review is not permission to merge or release. Native builds/tests and the manual matrices remain separate acceptance work; resolving findings must not relabel those gates as passed.

The legacy `android.test` runner/library wiring was considered but is not a separate finding: the controller supplied the earlier AGP investigation that legacy dependencies/manifest entries are supplied automatically. Missing hand-written declarations alone do not establish a failure. F1 remains independent of that wiring.

## Declined to judge

- Cloud sync, verified phone/email ownership, authentication and provider delivery receipts: explicitly deferred product scope; local metadata/manual handoff is the approved MVP.
- Payments/subscriptions, commercial quotas, teams, client booking, clinical records and attachments: expressly outside the requirements; no expansion requested.
- App Store/Play release signing, release publication and merge readiness as an authorized action: no merge or release is authorized, and native execution is absent.
- Actual contact-provider behavior, installed messenger/extension identifiers, recipient selection and delivery: source adapters/fallbacks were inspected, but no real external contacts/messages or installed-app testing is authorized as evidence here; synthetic native adapter checks remain required.
- Actual OS alarm/notification delivery under Doze, reboot, denied permission, background suspension and clock changes: source reconciliation was reviewed; delivery/lifecycle results require device execution. The documented iOS 60-request window and absence of guaranteed background refill are accepted MVP limitations, not grounds to add a background delivery service.
- Native compilation, test discovery, gesture arbitration, screenshots, touch targets, contrast, keyboard/layout and TalkBack/VoiceOver behavior: not inferred from authored tests or source structure; platform gates remain UNRUN. Explicit source omissions are nevertheless reported in F1/F4/F5.
- Exact performance timings and memory consumption at the technical ceiling: no native benchmark exists; F3 addresses the demonstrated repeated decoding, not an invented elapsed-time result.
- Full binary provenance, credential absence and pixel equivalence of the unchanged HTML references: the package records inventory/hash/limited-scan evidence; this review does not promote it to a complete security audit or visual approval.
- Recovery/export of data after uninstall or device loss: no cloud backup is claimed; a new backup/export feature is outside this implementation plan.

## Assessment

**Source verdict: changes required (F1–F5); resolve F6 in the same bounded wave.** The implementation broadly follows the local native plan and has useful domain/persistence fixtures, but the identified instrumentation access, referenced-client deletion, reconciliation complexity and calendar requirements should be corrected before source acceptance.

**Runtime verdict: NOT ACCEPTED / UNRUN.** Neither native application has a successful build/runtime result in the supplied evidence. A clean scoped re-review and the controller's final Android offline run would establish only those limited results; Android native and all Swift/Xcode/device gates still need execution. No merge/release recommendation or authorization is given.

---

## One scoped final re-review — 2026-11-03

The assessment above records the initial review and is preserved as historical evidence. This follow-up reviews only the consolidated F1–F6 fixes and new breakage within their delta. Read `final-fix-report.md` and all 15 paths in the raw 62,638-byte `final-fix.diff`; the independently read SHA256 is `A879816CD2E4FA5F0DE35283F3960E4086902D0572151D687CF3081BCFE4DF08`, matching the frozen handoff. No whole-source re-review, regenerated Git diff, test rerun, production edit or additional reviewer was used.

### Individual dispositions

| Finding | Scoped source verdict | Evidence and remaining boundary |
| --- | --- | --- |
| F1 | Addressed | `ManualOpenLifecycleTest.kt:31` now invokes the declared override by scoped reflection. It no longer calls a protected Kotlin method directly or widens production visibility. Drop/retry assertions remain. The detached direct-method fixture still requires native compilation/execution and does not prove framework lifecycle ordering. |
| F2 | Addressed | `ios/Sources/ZapysCore/SQLiteRepository.swift:230` checks appointment references inside the transaction before removing the client, returning Ukrainian guidance. The new real-SQLite regression covers rejected deletion, reopen equality for client/appointments/outbox, subsequent payment edit and unreferenced deletion. Existing referenced clients remain usable; the deliberate restriction is that references must be removed or changed before deleting the client. Swift tests are authored, UNRUN. |
| F3 | Addressed | `SQLiteRepository.swift:206` loads one transaction-local appointment/client/reminder snapshot, indexed by identity and retaining reminder order. Refresh, timezone rebasing and action paths consume it. `:195` skips equal reminder writes and updates the in-memory index after changed writes; unchanged zone metadata is also skipped. The previous transaction boundaries, stale-ID invalidation commit behavior, cancellation provenance and terminal-state decisions are retained. The new 100/10,000-row batch-seeded regression counts real decodes and uses rejecting UPDATE triggers for no-op writes; read, action, state, client-save and zone paths are covered in source. Actual Swift execution and elapsed-time/memory measurement remain UNRUN. |
| F4 | Addressed | Android presentation supplies compact week time and abbreviated status/client; the canvas consumes both inside the existing first row and uses `labelCenters` for the lower-half client anchor. Service positions and true event heights are preserved; day/tall behavior is retained. Pure-domain regression covers statuses, row scales, day/week and mixed unset colors, while the native fixture distinguishes event time from the hour-rule draw. Targeted domain tests passed; native rendering/instrumentation remain UNRUN. |
| F5 | Addressed | Shared iOS `CalendarPresentation.appointmentLabel` is wired into both the actual block and the agenda edit button, with client/date/range/status/count/service names. Header wording includes selection/today and the selected trait is set. Independent edit/end actions remain separate. New helper tests and actual UI assertions cover the changed semantics. Swift compilation, UI tests and VoiceOver traversal remain UNRUN. |
| F6 | Addressed | Settings now describes current queue/permission/manual behavior; the changed API sections describe the implemented calendars, contact/reminder integrations, terminal rebasing, current signatures and fourteen app-source memberships. Native UNRUN statements are retained. No historical review evidence was rewritten. |

### Verification considered

The fixer records a regression-first contract compilation failure for the missing Android `week`/`compactTime`/`labelCenters` API, followed by **8 targeted Android domain tests PASS**. That RED was not a native failure or an executed assertion failure.

Read the controller's exact `final-domain.log`: JUnit 4.13.2, `OK (84 tests)`, `Time: 0,367`, offline Kotlin 2.0.20. The controller reports exit 0. This reviewer did not rerun that suite. The new Swift tests are not included in this result, nor are Android framework/UI/instrumentation sources. The controller also reports refreshed structural inventory checks; those are not promoted to runtime evidence here.

### Residuals and final scoped verdict

**No unresolved F1–F6 source finding and no new blocking source regression identified in the reviewed delta. Source corrections accepted.** This disposition supersedes the initial source-change verdict above; it does not guarantee absence of defects in unexecuted platform code.

**Native runtime acceptance remains NOT ACCEPTED / UNRUN.** Required remaining gates are Android pinned-toolchain compilation/lint/APK/test APK/instrumentation and all Swift package/Xcode/simulator/UI checks, followed by the documented native lifecycle, permissions, gestures, rendering, accessibility and installed-adapter scenarios. F3 removes the demonstrated repeated full-table decoding; it does not establish a native latency benchmark. F1 removes the protected-call source concern; it does not establish a lifecycle test pass.

The earlier declined-to-judge scope remains unchanged. This was the single authorized scoped final re-review; no further review wave, merge or release is recommended or authorized by this report.
