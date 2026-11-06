# Task 10 Android contacts and reminders review

Date: 2026-11-03. Reviewed frozen `task-10-android.diff` (54,952 UTF-8 bytes), brief, implementation report, independent QA report, REQUIREMENTS and DESIGN. Scope: final Android contacts, reminder preferences/outbox reconciliation, device adapters, profile flag and related UI lifecycle. Previous AGENTS.md is revoked. Production files were read only; no installs, commits, external messages or unchanged test reruns.

## Verdicts

**Specification: changes required.** The principal requested integration is present, but timezone reconciliation hides terminal outcomes and backwards clock changes leave reminders actionable before their configured due time. Boot recovery also retains an invalid assumption about notifications still being displayed.

**Quality: changes required; native acceptance remains blocked.** The separation of database/provider work from UI, retained scheduling IDs and explicit preview/open flow are good foundations. Source inspection does not establish Android compile or runtime correctness. The offline results below cannot close the lifecycle, scheduling or framework gates.

## Ranked findings

### 1. P2 — Terminal outcomes disappear from the queue after a timezone change

`android/app/src/main/java/ua/zapys/data/SQLiteRepository.kt:99` deliberately excludes HANDED_OFF/CANCELLED rows from rebasing, but `SQLiteRepository.kt:49` and `:54` still filter every row by equality with a newly computed timezone-dependent scheduledAt. Reproduction: hand off or cancel a reminder for a still-future appointment, change to a zone with a different offset, and reopen the queue. The stored terminal row keeps its old timestamp and is therefore filtered out, so the distinct handoff/cancellation outcome disappears. An unrelated later save can also replace that mismatched terminal row through `:90`, making it pending again. Preserve terminal identity/state while updating the relevant timezone metadata, or define terminal visibility separately from pending scheduling eligibility. Verify terminal outcomes remain visible and cannot become pending after timezone change followed by an unchanged appointment/client save.

### 2. P2 — Boot reconciliation does not restore already-due notifications

`android/app/src/main/java/ua/zapys/device/ReminderScheduler.kt:34` and `:41`, with `device/ReminderReceiver.kt:11`. After a notification has been posted, its ID remains in persistent `shown`. A reboot clears OS notifications, but the receiver ignores the broadcast action and performs ordinary reconciliation. A still-due row remains in both due and shown, so it is never posted again; only its expiry alarm is restored. The notification can remain absent for the entire remaining actionable period. Reset/reconcile display state specifically on boot, without blindly reposting notifications the user dismissed during ordinary foreground reconciliation. Device verification must cover reboot between due time and appointment start, as well as future alarms and expired rows.

### 3. P2 — Backwards clock changes retain premature manual action and notification state

`android/app/src/main/java/ua/zapys/domain/DeviceRules.kt:4` and `:5`; `ui/RemindersScreen.kt:33`; `device/ReminderScheduler.kt:33`. Once a row is promoted to NEEDS_ACTION, moving the clock back before scheduledAt never removes its actionable status. The scheduler treats all NEEDS_ACTION rows as due; the explicit-open check only verifies that appointment start is future. Thus a reminder due at 09:00 remains available for launch and notification at 08:00 after a clock correction. ERROR retries have the same missing due-time guard. Preserve error/terminal outcomes, but derive current eligibility from scheduledAt <= current clock and restore the future alarm when necessary. Add a forward-to-due then backwards-before-due fixture, followed by crossing due again; check queue, notifications and explicit-open behavior.

## Strengths verified by source inspection

- Contact picker uses ACTION_PICK plus Phone.CONTENT_TYPE and queries only the selected content URI for display name/number. There is no READ_CONTACTS permission or address-book enumeration. Provider reads run on the serial IO executor; cancellation/failure leaves manual fields available.
- Contact ownership, returned URI and pending data are saved. Applying a result waits for restored hierarchy and matching form identity. Foreground reconciliation does not rebuild client/appointment/settings forms, avoiding the obvious dirty-form loss on picker/composer return.
- Profile notification preference is persisted with a backward-compatible false JSON default, and settings saves use copy. Client preferences include channel, recipient, template and enabled lead; the positive enabled-lead ruling is implemented. Existing unique appointment_id outbox storage enforces one row per appointment.
- Due reads transactionally persist scheduled-to-needs-action promotion. Manual queue does not depend on notification permission. OS permission requests occur from an explicit settings action, and profile/system/channel checks gate notification scheduling.
- Preview precedes an explicit open action. Open re-reads current ID and appointment and checks appointment start against the clock. Messenger intents target the selected package; launch exceptions become ERROR; retry/copy are exposed. Recipient selection is explicitly manual, and handoff never claims sent/delivered. Preview cancellation does not change state.
- Retained alarm/notification IDs support cancellation even after rows are filtered/deleted. Notifications use private generic text without client/phone/note. Appointment-start cleanup and boot/time/timezone hooks exist; inexact timing and OS delays are documented honestly.
- Database and contact-provider operations in the inspected integration paths run off the UI thread. Receiver completion uses finally, and notification failures do not mark an ID successfully shown.

## Evidence and concrete verification gaps

Reported evidence, not rerun here: full offline domain suite **79 passing**, independent policy suite **6 passing**. These validate pure Kotlin policy, not native integration. The frozen diff size was independently checked as **54,952 bytes**. No production modification was made during review.

Native Gradle compile, SDK35 build/lint, instrumented SQLite/intent fixtures, emulator/device execution and rendered/accessibility inspection are **BLOCKED/UNRUN**. In particular, authored DeviceIntentTest and DevicePersistenceTest are not compiled/passed evidence. Do not describe Task 10 as runtime verified.

Required follow-up verification:

1. Compile/lint with pinned Android/Kotlin toolchain and execute the synthetic persistence/intent fixtures. Check old profile JSON without the new flag as well as enabled preference round trips.
2. Exercise picker cancel, empty/null cursor, unavailable provider, SecurityException, delayed provider result, rotation/process recreation while picker/query is outstanding, and navigation to another form. Confirm dirty values and exact originating form ownership, including switching away and back to the same saved client.
3. Add lifecycle coverage for manual-open callbacks. `MainActivity.kt:140` checks generation/isDestroyed but not resumed/stopped state; a delayed explicit-open lookup can call `startActivity` after Home/backgrounding. Verify background-launch behavior, cancellation of obsolete operations, duplicate taps and rotation while preview/open/save is pending. A successful launch followed by a failed state write also needs a clear recovery outcome without falsely implying delivery.
4. Verify all four installed/uninstalled/unsupported target composer cases without sending any messages. Check ERROR retry/copy, cancelled preview, external composer cancellation, current-ID rejection after reschedule, and appointment expiry while preview is open.
5. Exercise API33 permission denial, permanent denial, app/channel disablement and profile toggles. Queue must still promote due rows without edits; settings copy must remain understandable after returning from permission settings.
6. Exercise retained alarm/notification cancellation after reschedule, disable, cancellation and deletion; boot recovery; forward/backwards clock and timezone changes; expiry without edits; Doze/inexact delays; notification/alarm failures and subsequent reconciliation. Add the three finding-specific scenarios above; current pure tests do not cover them.
7. Inspect Ukrainian copy, long/large text, focus, scroll and light/dark rendering on device. Source-level use of native controls is not accessibility or rendering evidence.

## Fix round 1 re-review — 2026-11-03

Scope: the original three P2 findings, the explicitly requested stopped-activity callback guard, and directly introduced breakage only. Read the frozen `task-10-fix1.diff` and appended implementation report; independently confirmed the diff is **25,293 bytes**. No unchanged tests were rerun, no broad audit was performed, and production remained read only.

### Finding disposition

| Finding | Disposition | Source evidence |
| --- | --- | --- |
| P2 terminal outcome loss/revival on timezone change | **Addressed in source** | `SQLiteRepository.kt:53`, `:60`, `:72`, `:118` reconcile before queue reads and relevant mutations. `:102` persists zone metadata and rebases terminal scheduledAt in the transaction, retaining ID/state before unchanged-save comparisons. The terminal outcome is no longer excluded from timestamp rebasing. |
| P2 due notification missing after reboot | **Addressed in source** | `ReminderReceiver.kt:11` passes the boot action explicitly. `ReminderScheduler.kt:35` resets the display ledger only for boot; due rows are consequently repost candidates, while ordinary foreground reconciliation retains dismissal history. |
| P2 premature action/display after backwards clock change | **Addressed in source** | `DeviceRules.kt:4`–`:6` separate current-clock action, future alarm and due display eligibility. `ReminderScheduler.kt:28`–`:29` consume those predicates. `RemindersScreen.kt` uses time-aware checks at rendering, preview lookup, explicit-open lookup and again immediately before launch. NEEDS_ACTION/ERROR outcomes survive correction without allowing premature action; future alarms are restored. |
| Stopped-activity callback verification gap | **Guard addressed in source; framework verification still open** | `MainActivity.kt:34` invalidates the request epoch on pause; `:35`–`:40` guard both success and failure callbacks by generation, resumed state and current epoch. Preview and explicit-open lookups use this path. Dropped work cannot write HANDED_OFF; resuming does not reactivate an obsolete request. |

No additional actionable source defect was identified in this narrow fix diff. The rebasing occurs before the mutation being compared, so a timezone-only correction is distinguished from a real appointment/preference change. Metadata and row changes share the repository transaction. Boot handling does not reset dismissal state on every foreground pass. The external callback guard covers the failure path as well as successful composer launch, avoiding a stale error callback replacing current UI.

### Revised verdicts

**Specification: PASS for the reviewed source changes and original findings.** All three original P2 findings are addressed at source level. This supersedes the earlier changes-required verdict for those findings; it does not declare device acceptance complete.

**Quality: source re-review PASS; native acceptance remains BLOCKED/UNRUN.** No outstanding source finding from this review remains. Android compilation/lint, instrumentation, real SQLite behavior, boot/notification/alarm behavior and actual lifecycle/background-launch ordering remain unverified gates.

Reported new evidence: focused DeviceClockTest **RED with 4 assertion failures, then GREEN with 4 passing**, and full offline domain suite **83 passing** using bundled Kotlin 2.0.20/JUnit. These were not rerun and do not establish pinned native compile success. The final failure-callback lifecycle gate is native source changed after the offline run and remains uncompiled.

`DeviceClockPersistenceTest` adds meaningful real-SQLite fixtures for both terminal outcomes across timezone change, an unchanged write before the first queue read, reopening, and backwards-clock/error eligibility. `ManualOpenLifecycleTest` exercises the production callback gate with a delayed request, direct onPause invocation and a fresh resumed request; it does not launch a composer. Both are **authored, UNRUN**. The direct lifecycle fixture seeds resumed state and therefore cannot prove actual Home/rotation ordering even if it later passes.

The earlier native follow-up checklist remains applicable. In particular, execute timezone-before-first-write/reopen fixtures, boot during the due window versus ordinary notification dismissal, clock backwards then due again, and delayed success/failure callbacks through real Home/resume/rotation. Verify no composer appears and no handoff is persisted for an obsolete request. No claim of runtime success is made.
