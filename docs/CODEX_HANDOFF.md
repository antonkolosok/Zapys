# Execute the ten Android improvements in local Codex

The user explicitly requested implementation with Superpowers and appropriate agents, tests and a commit after EVERY task, one aggregate PR only after all tasks, and NO merge. This is an execution handoff, not a request for another planning discussion.

## Read first

1. Root `AGENTS.md`: persistent workflow for all future Zapys tasks and every created agent.
2. `docs/superpowers/specs/2026-11-06-android-ux-improvements.md`: exact behavior/acceptance criteria for all ten items and documented implementation assumptions.
3. `docs/superpowers/plans/2026-11-06-android-ux-improvements.md`: files, regression cases, test commands, task order and commit messages.
4. `docs/superpowers/progress/2026-11-06-android-ux-improvements.md`: actual execution checkpoint; currently all product tasks PENDING.
5. Latest `docs/STATUS.md` and `docs/qa/verification.md`: existing build evidence and environment-specific limitations.

## Start execution

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
