# Execute the ten Android improvements in local Codex

The user explicitly requested implementation with Superpowers and appropriate agents, tests and a commit after EVERY task, one aggregate PR only after all tasks, and NO merge. This is an execution handoff, not a request for another planning discussion.

## Current execution checkpoint — 2026-11-07

- Branch `feat/android-ux-improvements`. Preparation commit `e10f748`; Task 1 complete `283782e`; Task 2 complete `17ba516`; Task 3 complete `14c2bdd`; Task 4 complete `d4ac5c5`. Tasks 5–8 are also complete: aee0b0c,94bd905,31fd586,9b7874a respectively. All eight tasks passed required checks and independent specification/code-quality review. Do not restart completed tasks; read the actual progress ledger and Git log.
- Task 5 complete in aee0b0c, tested and independently approved. Task 6 complete in 94bd905, tested and independently approved. Task 7 complete in 31fd586, tested and independently approved. Task 8 complete in 9b7874a, tested and independently approved. Task 9 verified and independently approved; separate commit pending. Evidence111unit,37normaldevice,6largefont,2explicitlandscape PASS; emulator restoredfont1/portrait. Read task-9-report/review/ledger. Task10 remains pending. On resume check the ledger/report/working tree before assigning any task again.
- Actual Superpowers skills are installed under `C:/Users/anton/.codex/plugins/cache/openai-curated-remote/superpowers/7.0.1/skills`. Every agent must read relevant skill files. Coordinator owns reviews and commits after checks; workers never create agents or commit.
- Android common command now passes: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-android.ps1`, 107 unit tests at Task8, lint 0 errors/16 original warnings, app/test APKs. Use the supported escalation flow when required; the sandbox cannot download dependencies/write profile paths. Do not repeat ordinary user confirmations. Coordinator owns ALL build and emulator commands because combined worker commands caused approval holds. Workers send readiness/filter details, then read the coordinator evidence. Reuse the exact approved helper command without combining a search or pipeline.
- Dedicated synthetic-data AVD `Zapys_UX_API35`, serial `emulator-5580`, API35, density420, is installed and running. Ignored runner `artifacts/android-ux-2026-11-06/device-checks.ps1` supports test class filters, rendering pulls, navigation mode/orientation, and exact Task2 baseline captures. Use only this device; no real data. No concurrent Gradle builds.
- SDD workspace `.superpowers/sdd/2026-11-06-android-ux-improvements/` contains task briefs/reports/review packages. Durable authoritative ledger remains `docs/superpowers/progress/2026-11-06-android-ux-improvements.md`.
- Task 9 has two accepted follow-ups: preserve historical snapshots with a narrow move persistence operation (existing editor save refreshes them); fix the demonstrated existing landscape calendar viewport clipping so New Appointment and a usable timeline remain reachable. Exact baseline/current comparison and the ruling are recorded in the ledger and Task9 brief. Neither is claimed fixed yet.
- GitHub connector/CLI work. `main` exists; old MVP PR #1 was already merged upstream before this batch. This session has not merged anything and has not created the Android UX aggregate PR. Create exactly one only after all ten gates and final regression.

## Read first

1. Root `AGENTS.md`: persistent workflow for all future Zapys tasks and every created agent.
2. `docs/superpowers/specs/2026-11-06-android-ux-improvements.md`: exact behavior/acceptance criteria for all ten items and documented implementation assumptions.
3. `docs/superpowers/plans/2026-11-06-android-ux-improvements.md`: files, regression cases, test commands, task order and commit messages.
4. `docs/superpowers/progress/2026-11-06-android-ux-improvements.md`: authoritative actual execution checkpoint; later entries supersede preparation observations below.
5. Latest `docs/STATUS.md` and `docs/qa/verification.md`: existing build evidence and environment-specific limitations.

## Original preparation instructions (apply only to unfinished gates)

- Work in `C:\Users\anton\Desktop\Zapys` or its approved isolated feature worktree. Do not re-clone an empty remote and lose the local source/context.
- Load real Superpowers skills, especially `subagent-driven-development`; dispatch task-scoped implementer and independent reviewer agents. Every dispatch must explicitly require the appropriate Superpowers skills. Verify the skills are available in this local CLI; cloud-chat skill availability is not evidence that the CLI has them.
- Read the preflight in the plan. Observed HEAD is `384f290 feat: add native local calendar MVP`, on `feat/local-mvp`. Untracked `android/.idea/` belongs to the user's IDE; preserve it and exclude it from commits.
- The normal PowerShell GitHub CLI returned `antonkolosok`. Previous chat's HTTP401 and read-only `.git` are specific to that isolated environment. Check this session's real permissions; use its supported approval flow for necessary Git/network commands. Never request tokens in chat or copy them into the repository.
- Commit the rules/planning package separately, then implement task 1. After its tests and review pass, commit it before starting task 2. Continue through task 10; do not wait for routine permission that is already covered by the user's request.
- For a real blocked test/commit/skill, record the blocker and stop at that gate with the exact next action. Do not mark it done or accumulate the remaining tasks as uncommitted changes.
- Resolve routine UI implementation choices using the written spec. Android is the scope of this batch. Preserve the separate iOS project; do not claim its tests have run.
- Only after all ten tested/reviewed commits and final regression, create ONE aggregate PR. No merge, auto-merge or release. If a remote PR base is missing, explain that specific publication blocker rather than inventing or overwriting `main`.

## Why a handoff is needed

The preparation chat can edit project files but its active policy makes `.git` read-only and does not permit Git escalation requests. It cannot remotely control the separate Codex window in PowerShell. Therefore it prepared this package and performed read-only source audits; it did not implement or commit the ten product changes. Execute them here, with the user's local Git approval flow.
