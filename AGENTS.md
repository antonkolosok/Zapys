# Zapys project context

The user revoked the old working agreement on 2026-11-03. The rules below were explicitly established on 2026-11-06 and apply to future Zapys work, including every newly created agent.

Approved product scope is documented in `docs/REQUIREMENTS.md` and `docs/DESIGN.md`. Current verification evidence and remaining blockers are in `docs/STATUS.md` and `docs/qa/verification.md`.

The current user authorization covers development, tests and an unmerged feature-branch pull request. It does not authorize merging or publishing a mobile release.

## Required development workflow

1. Use the applicable **Superpowers skills**. The coordinator and EVERY agent it creates must load the actual relevant skills, not merely claim to follow them. Include this requirement explicitly in every agent dispatch. Check skill availability at startup; if unavailable, report the missing capability and use the supported installation/connection process before implementation.
2. Break a multi-part request into separately numbered, independently verifiable tasks. Preserve the user's requested task boundaries. Record acceptance criteria, affected files and checks before implementing.
3. Use appropriate implementer and independent reviewer agents. Run implementations sequentially when they touch shared files; independent read-only investigations may run in parallel. Each task needs both specification and code-quality review. Workers must not create duplicate reviewer agents; the coordinator owns review dispatch.
4. For each task: reproduce the problem or add the relevant failing test, implement the scoped change, run the appropriate tests, resolve review findings, then create a **separate Git commit**. Record commands, results and commit SHA. Do not move to the next implementation task before this gate is complete.
5. Test after EVERY task. UI tasks need relevant layout/interaction checks; domain tasks need behavioral unit tests. Compilation alone is not a runtime/UI test. A blocked or unrun check must never be reported as passing. Preserve existing regression coverage; do not weaken checks to get a green build.
6. If a required test, skill, Git permission or commit is blocked, record the exact blocker and stop that task at the blocked gate. Prepare a reproducible handoff; do not silently combine uncommitted tasks or bypass environment restrictions.
7. Work on a feature branch. Preserve unrelated user changes. Stage explicit task paths; do not commit tools, build outputs, IDE-local files, credentials or real client/contact data. Never force-push or rewrite unrelated history.
8. Create **one aggregate PR/MR only after all requested tasks are complete**, tested, reviewed and committed, and the final regression review passes. Do not open per-task PRs or an early draft PR. If this work already has the one PR, update it instead of creating another.
9. **Never merge, enable auto-merge, or publish a mobile release** unless the user separately and explicitly authorizes that action. An authorization to commit or open a PR is not merge authorization.
10. Maintain `docs/STATUS.md` and the active task ledger so another Codex chat can resume from actual evidence. Later explicit user instructions take precedence over older requirements and process documents.

Current improvement handoff: `docs/CODEX_HANDOFF.md`.
Current specification: `docs/superpowers/specs/2026-11-06-android-ux-improvements.md`.
Current implementation plan: `docs/superpowers/plans/2026-11-06-android-ux-improvements.md`.
