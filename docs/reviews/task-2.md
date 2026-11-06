# Task 2 independent Android review

Review date: 2026-11-02. Scope: Android domain, SQLite repository, build configuration and persistence tests in the supplied Task 2 review snapshot. No UI acceptance claim. Source and test review only; the reported 31 passing offline domain tests were not rerun.

## Verdict

Spec: changes required. Quality: changes required. No Critical findings; two Important correctness findings below. Android compilation, lint, APK assembly and on-device SQLite/instrumentation remain BLOCKED and are acceptance gates, not passes.

## Important findings

1. **Expired outbox actions can still be handed off.** `android/app/src/main/java/ua/zapys/data/SQLiteRepository.kt`, `reminders()` and `setReminderState()`: appointment time is checked only when `replaceReminder()` is invoked by a save. A reminder saved before its appointment remains in the returned outbox indefinitely; after advancing the clock past the start, its original ID can still transition to `HANDED_OFF`. No mutation of the appointment is necessary to reproduce this. This violates avoidance of reminders for past appointments and the no-stale-action requirement. Resolve against the current appointment and clock inside the state-change transaction, and expose expired entries as non-actionable or remove/invalidate them. Add a mutable-clock persistence case that creates a future reminder, advances past the appointment without saving, and attempts the old ID.

2. **DST changes shift reminders away from the appointment's wall-clock time.** `android/app/src/main/java/ua/zapys/domain/Rules.kt:74`: `date.atStartOfDay(zone).plusMinutes(startMinute)` adds elapsed minutes to a zoned instant. Appointment minutes represent local calendar wall time. For `Europe/Kyiv` on spring transition day 2030-03-31, appointment minute 600 represents 10:00, but adding 600 minutes from midnight produces 11:00 local time; the outbox is one hour late. The reverse error occurs on fall transition days. Construct the local date/time first and then resolve it in the zone, with an explicit policy for nonexistent/ambiguous local times. Add transition-day tests; existing UTC-only tests cannot detect this.

## Reviewed strengths and limits

The implementation statically covers five ordered service snapshots, nullable versus zero minor-unit prices, end 1439 versus 1440 validation, adjacency/cancelled/self conflict exclusions, fresh repeat IDs and reset payment/status, literal template replacement, and one outbox row per appointment. Snapshot rows have no catalogue FK, so catalogue changes/deletion do not mutate historical services. Appointment, snapshot and outbox replacement share a SQLite transaction; client deletion has a restricting FK. Old reminder IDs are inert after replacement. No delivery or verified-identity claim was introduced, and Ukrainian source characters are present (console rendering was checked by character codes).

The instrumentation tests meaningfully exercise named on-disk reopening, snapshot order/nullability, client FK restriction, trigger-induced rollback, reschedule/cancel invalidation, preserved handoff on payment edits, conflicts and profile persistence. They remain authored tests, not executed evidence. Their fixed UTC clock at epoch zero misses both findings above. The rollback trigger fails before reminder replacement; an additional failure specifically during outbox insertion would strengthen proof of rollback of that final stage.

Legacy `android.test.AndroidTestCase` and `android.test.InstrumentationTestRunner` are a build verification concern, not a proven missing-library defect from this static review. Official Android guidance documents optional test libraries and test-manifest declarations, but also explicitly says AGP auto-generates the required support for Gradle instrumentation tests. Inspect the actual SDK35/AGP-generated test classpath/manifest and run instrumentation when the toolchain is available; offline Kotlin results do not validate these Android APIs. Reference: https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/test-setup (Projects using deprecated classes).

No identical test runs were repeated, no production/test sources changed, and no commit was made by this reviewer.

## Fix round 1 re-review

Reviewed the exact `task-2-fix1-before/fix1.patch` and updated implementation report. This section supersedes the initial source verdict above; it does not supersede the blocked runtime gates.

- **Finding 1: ADDRESSED in source.** `reminders()` now filters rows against the current appointment, client and one clock value inside a transaction. `setReminderState()` independently rechecks eligibility inside its mutation transaction and removes an ineligible row before any state update. Internal replacement uses raw stored rows, preserving handed-off state for unchanged reminders. New mutable-clock instrumentation cases cover expiry with no appointment save, stale-ID mutation and reopening. Those cases remain unexecuted.
- **Finding 2: ADDRESSED in source.** Local date and wall-clock minutes are combined before zone resolution. New spring/fall 10:00 cases assert fixed UTC instants; gap-forward and earlier-offset overlap policy is documented and pinned separately.
- **Additional canonical name token: ADDRESSED.** Both U+2019 `[ім’я]` and ASCII `[ім'я]` resolve through the same single-pass literal replacement. The new regression checks both forms with a name containing another token and `$1`, preventing recursive expansion/replacement interpretation.

No new Critical or Important defect identified in this scoped fix diff. Source spec verdict: PASS. Source quality verdict: PASS. Acceptance/verification verdict: BLOCKED pending Android compilation, lint, APK build and actual SQLite/instrumentation execution, including the new mutable-clock tests. Reported domain evidence is 4 regression tests with 3 expected initial failures, then 35 passing offline domain tests; these commands were not rerun by this reviewer. No production/test sources changed and no commit made.
