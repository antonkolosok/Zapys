# Task 4 independent Android source review

Reviewed the Task 4 brief, report and scoped review diff against `docs/REQUIREMENTS.md`, `docs/DESIGN.md` and `android/API.md`, and inspected the client/service/profile/navigation implementation and supporting domain/repository signatures. This review does not apply the revoked AGENTS.md. No source changes, commits, next-task implementation or test reruns were performed.

## Verdicts

**Spec: changes requested** for the incorrect past/future client-history partition below. The remainder of the authorized Task 4 source scope is substantially represented. Contact picking, reminder preference editing, appointment navigation and the calendar remain explicitly deferred to their sequential tasks.

**Quality: changes requested** for the same concrete issue. No definite Android compilation error was established by source inspection; compilation has not been demonstrated. **Execution gate: BLOCKED.** The reported 38 offline pure-domain tests and three XML parses do not compile or exercise these native Views. Android app compilation, lint, SQLite instrumentation, lifecycle/runtime checks and light/dark/large-text UI inspection remain required when the toolchain is available. This review supplies no APK or runtime pass and does not close that gate.

## Finding

- **P2 — today's elapsed appointments remain in the future section.** `android/app/src/main/java/ua/zapys/ui/ClientsScreen.kt:48` partitions only by `LocalDate.now()`. At 18:00, a persisted appointment today from 09:00–10:00 appears under upcoming appointments and never appears in history until midnight. This contradicts the required past/future client card and misleads the professional about today's remaining work. Compare a record's date and appropriate wall-clock time against one captured current date/time, with an explicit policy for an in-progress appointment; preserve the existing ordered vertical service/notes rendering. A focused boundary check should cover earlier today, later today, yesterday and tomorrow when implementing the fix.

## Source observations and practical limits

- Repository work runs on a serial background executor. Generation/activity checks suppress callbacks for abandoned screens, and destruction queues close behind previous work. Save failures update the same form rather than rebuilding it; input values are retained. Save blocks body input and navigation until completion. These are source observations, not lifecycle execution evidence.
- Route, edit UUID, creation UUID, hierarchy and dirty state are explicitly saved; delayed form loads apply pending hierarchy. Repeated creation attempts retain the record UUID. Rotation during load/save, process recreation and spinner restoration still require actual Android verification.
- Client saves retain their own UUID. Phone duplicates exclude that UUID, show explicit confirmation, and never choose another person's identity for an upsert. Existing reminder preferences survive copying the client. Search handles names and formatted phone digits.
- Services preserve blank duration/price as null and zero price as free; malformed nonblank durations and money receive field errors. Catalogue deletion describes preserved historical snapshots. There is no five-item catalogue limit.
- Profile copy describes optional unverified local data, local storage and unconnected delivery/backup honestly. Limits reference central constants. Ukrainian Unicode code points were checked directly in the source; terminal question-mark rendering is not a source corruption finding.
- The launcher, four destinations and documented Task 6/10 integration entrypoints are present. Future integration placeholders do not simulate successful contact access, account authentication, messaging or calendar persistence.

Resolve the history partition before source approval. Keep Android execution explicitly blocked until evidence exists; do not substitute the existing pure-domain test result for native form verification.

## Fix round 1 scoped re-review

Reviewed `task-4-fix1.diff` and the appended implementer report for the original P2 and regressions introduced by the three-file fix. **Original P2 addressed at source level.** `FormRules.isHistory` compares the appointment's end wall-clock date/time against one captured `now`; both client-card sections use that same value. Earlier end times and exact equality enter history, while ongoing and later appointments remain upcoming. Yesterday/tomorrow and status-independent chronological classification are consistent with the stated policy. Ordered service snapshots and notes are untouched.

The focused regression checks earlier/later today, ongoing, equality, yesterday, tomorrow and a completed status. The implementer reports one intended failing test followed by 39 passing offline domain tests. No duplicate tests or reruns were performed for this review. No new important defect was identified in the scoped changes.

**Updated source verdict: spec and quality approved within Task 4 scope. Execution gate remains BLOCKED.** This supersedes the initial changes-requested verdict and the requirement to resolve P2. It does not establish Android compilation, lint, instrumentation, lifecycle behavior or UI correctness; those native gates still require actual execution evidence.
