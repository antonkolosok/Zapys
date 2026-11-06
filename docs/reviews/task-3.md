# Task 3 independent source review

Reviewed the Task 3 brief, report and review diff against REQUIREMENTS.md and AGENTS.md; inspected the Swift models, rules, CSQLite bridge, repository, three XCTest files, package manifest and root CI. No production or test sources changed. Future UI is outside this review.

## Verdict

Spec: substantially implemented at source level, with one stale-action atomicity issue below. Quality: changes requested for that issue. Execution gate: BLOCKED. There are zero Swift compilation, type-check or runtime passes; Swift/Xcode are absent on this host and remote write 403 prevents starting macOS CI. The reported YAML parse and static checks do not establish executable correctness. This review does not turn either RED or GREEN into PASS.

## Finding

- **P2 — stale manual action can overwrite a newer cancellation/reschedule across repository instances.** `ios/Sources/ZapysCore/SQLiteRepository.swift:146` (`markHandedToMessenger`) commits the eligibility refresh through `reminders(now:)`, retains a Reminder value outside a transaction, and starts a separate transaction to write that value. A second repository instance can cancel/delete/reschedule the appointment after the first transaction commits and before the final write. The original action then restores its old message/action ID as handedToMessenger, including after deletion (subsequent reads refresh existing appointments only). The documented confinement is per repository instance and does not prevent this interleaving. Keep refresh, identifier/state validation and handoff update inside one BEGIN IMMEDIATE transaction; return a rejection outcome from the transaction and throw after committing when expiry changes must survive rejection. Alternatively enforce and document one shared database executor across every instance. Add a coordinated two-connection regression only when implementing the fix; no duplicate test was added by this reviewer.

## Reviewed evidence and limits

- Five ordered service snapshots, optional Int64 money versus zero, 23:59 boundary, existing-client validation, explicit overlap override, self/adjacent/cancelled exclusions and repeat reset are represented in the source. Repository catalogue edits do not rewrite service snapshots.
- SQLite is real persistence. Prepared statements finalize after body completion, bound text/blob uses SQLITE_TRANSIENT, data is copied before stepping again, and normal mutation/migration paths use rollback-capable transactions. Schema user_version 1 is transactional and future versions reject. The injected trigger test exercises a failure after the appointment write at source level; it has not executed.
- Ordinary single-executor stale-ID, cancellation/deletion and clock-expiry paths are implemented. The finding concerns the gap between two transactions rather than those sequential cases.
- Template replacement is one-pass and literal, recognizes both apostrophes in lowercase Ukrainian variables, and leaves inserted tokens unexpanded. Terminal rendering of Cyrillic is not evidence of corrupt file contents.
- Calendar construction matches wall-clock components with explicit gap-forward/first-overlap policy. Kyiv gap/overlap assertions are present, but Foundation behavior remains unverified until macOS execution.
- LocalProfile has no verified-identity field; reminder states describe manual preparation/handoff without claiming delivery. No network delivery exists in this scope.
- CSQLite module/link declarations and Swift 5.9 package declarations are structurally plausible. No definite Swift type error was established by inspection; that is not a type-check pass.
- CI separates Android and macOS core tests, uses read-only contents permission, and preserves the Swift pipeline exit status through pipefail. It is a core package test job, not an iOS simulator/app-build result. Workflow execution, runner tool versions and SDK availability are unverified here.

Required before Task 3 can close: resolve the atomic manual-action finding and obtain actual macOS Swift test evidence when remote/toolchain access is restored. Keep blocked execution visible in STATUS and the PR.

## Fix round 1 scoped re-review

Reviewed `task-3-fix1.diff`, its before snapshots as represented by the diff, and the appended implementer report. **Original P2 addressed at source level.** `markHandedToMessenger` now obtains BEGIN IMMEDIATE before refresh/read, checks the action and writes handoff under that same writer reservation. Another connection cannot insert a cancellation/reschedule between eligibility and the handoff write. No nested transaction was introduced. A rejected action returns false inside the transaction, allowing refreshed expiry invalidations to commit before notFound is thrown outside it; SQL failures still follow rollback.

The raw second-connection payload assertion specifically checks committed expiry without a later refresh masking rollback. The lock-contention/cross-instance cancellation test provides relevant additional coverage, with an accurate comment that it does not reproduce the former between-transactions race. The new Swift closure/result types and CSQLite statement lifetimes show no definite defect by inspection. API documentation matches the change. No new issue identified within the fix scope.

**Updated source verdict: spec and quality approved within Task 3 scope. Execution gate remains BLOCKED.** Neither the original tests nor the new tests have compiled or executed; macOS Swift/CI evidence is still required. No missing-tool command was rerun and no runtime/type-check success is claimed. This section supersedes the initial changes-requested source verdict and the requirement to resolve P2, but does not supersede the blocked verification gate.
