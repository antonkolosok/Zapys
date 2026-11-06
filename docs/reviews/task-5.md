# Task 5 independent review — iPhone forms and project

Reviewed 2026-11-03. Scope: docs/REQUIREMENTS.md, docs/DESIGN.md, ios/API.md, Task 5 brief/report and both supplied diffs; current SwiftUI forms/AppModel, FormValues and tests, Xcode project/shared scheme, assets metadata/Info.plist, simulator script and CI. Revoked AGENTS guidance was not used. Calendar/editor/contact/device implementation is outside this task's explicit integration scope.

Specification verdict: CHANGES REQUESTED. Core client/service CRUD, duplicate-phone identity, optional exact values, honest profile and deliberate next-task entrypoints are present, but form labeling and failure-focus requirements are incomplete and profile editing can lose new input during initialization.

Source-quality verdict: CHANGES REQUESTED; platform verification BLOCKED. No definite Swift compilation error was established by source inspection. The project has coherent app/UI-test target wiring, local package linkage, shared testable scheme and iOS 17 deployment configuration. This is not compiler, installation or runtime evidence.

## Fixable findings

- **P2 — ios/App/SettingsView.swift:12-16,24-25 — Profile input can be overwritten by asynchronous initialization.** Profile fields are editable while `loaded == false`; only Save is disabled. A user can open Settings while the serial repository read is pending and start typing, then the ready callback unconditionally replaces `value` with `model.profile`. Disable editing until loaded, or track dirty input and reconcile without replacement. Also disable fields during a write or track the saved snapshot: currently a user may type after Save captures `saved`, then receive a saved message while the visible newer text has never been persisted.
- **P2 — ios/App/ClientsView.swift:29-40; ios/App/ServicesView.swift:40-42; ios/App/SettingsView.swift:12-14; ios/App/Theme.swift:29 — Required persistent visible labels are missing.** These are `TextField` titles/prompts, which disappear when values are entered on iOS; Section headings do not identify each populated field. DESIGN explicitly requires visible labels rather than placeholder-only fields. Add persistent labels/LabeledContent or equivalent for identity, reminder recipient/template, service duration/price and profile inputs; retain accessibility names and required markers.
- **P2 — ios/App/ClientsView.swift:67; ios/App/ServicesView.swift:62-64 — Validation focuses an unrelated field for nontrivial failures.** Client validation sends every failure except an empty name to phone, including enabled reminders without recipients and overlong note/template. Service note validation runs after focus has been set to price, so an overlong note focuses price. The spec requires focus on the first failing field. Validate/map field failures explicitly and bind focus to recipient/note/template as needed; preserve the existing entered text.
- **P2 — ios/UITests/ZapysUITests.swift:7,13-16 — Launch readiness assertion races database initialization.** The initial wordmark is present before opening SQLite completes; `waitForExistence` on Add does not wait for it to become enabled. Add is rendered but disabled until `model.ready`. A slow first launch can therefore tap a disabled button and subsequently fail to locate Save. Wait for an enabled/hittable Add using a predicate before navigation. The isolated launch argument is otherwise correctly wired.

## Verification blockers (separate from source fixes)

- **BLOCKED — Task 5 acceptance / macOS CI:** no Swift compilation, core test execution, xcodebuild build/test, simulator launch or real UI result was available on this Windows review host. These remain required before the task can claim platform acceptance. A syntactically valid PBX object graph and XML cannot prove module import resolution, SDK type checking, asset compilation, UI-test runner installation or actual accessibility queries.
- **BLOCKED — UI and persistence acceptance:** light/dark/Dynamic Type layout, actual save/error/discard behavior and restart persistence have not been exercised here. The small UI test only covers navigation and empty-client validation; it is not CRUD, failure-injection or persistence evidence.

## Positive source findings and evidence

- `AppModel` constructs and uses its single repository on one background serial queue. Successful snapshots and completion/error callbacks are dispatched onto main. Client/service forms retain their State, disable edits during a write and dismiss only after successful write plus refresh. If refresh fails after a committed write, retry uses the same UUID, avoiding duplicate creation, though the reported error cannot distinguish commit from refresh failure.
- Client duplicate checks exclude the current UUID, compare normalized phone keys and ask confirmation. Saving retains `value.id`; separate clients sharing a phone are not merged or overwritten. Existing repository validation remains the authority.
- FormValues uses optional Int duration and overflow-checked Int64 minor units. Blank, zero/free, comma decimal and two-digit precision are distinct without floating-point money. Duration range matches Rules. The added tests cover meaningful invalid inputs, but have not run in this review.
- Profile text explicitly says unverified and device-local, with no account/cloud authentication claim. Reminder wording discloses manual operation. Source diffs contain Ukrainian copy; terminal rendering of direct source reads displays question marks, so that terminal appearance alone is not evidence of damaged files.
- UI tests launch with `--ui-testing-temporary-database`; AppModel selects a fresh UUID directory under temporaryDirectory and never resets or removes the production Application Support database. All UI-test actions are synthetic; no contact or message integration is invoked.
- Source inspection of `scripts/test-ios.sh` finds simulator selection, boot-status wait, shared-scheme xcodebuild test, signing disabled and pipefail-preserved exit status. CI separates core/package tests and app/UI tests and uploads results. Script and CI were reviewed, not executed; YAML parsing reported in the implementation report was not repeated.
- Review checks actually executed: parsed shared scheme and Info.plist XML and both asset Contents.json documents; checked 43 PBX object definitions with zero duplicate IDs and zero unresolved 24-character IDs. Inspected CSQLite shim/module map and local package product/reference alignment. These checks are source/metadata checks only.

No implementation edits, commits, external actions, calendar/editor additions or build/runtime claims were made in this review.

## Fix round 1 scoped re-review — 2026-11-03

Reviewed task-5-fix1.diff, task-5-fix1-report.md and the actual six changed files (ClientsView, ServicesView, SettingsView, Theme, ZapysUITests and API.md). Scope was the original four P2 findings and consequential new breakage; unchanged project/metadata checks were not repeated.

**Scoped specification verdict: PASS (source review). Scoped source-quality verdict: PASS (source review). All four original P2 findings are resolved in source.** No important new issue or definite compile break was identified in this fix. Original platform verification blockers remain unchanged; this verdict does not claim simulator or compiler acceptance.

| Original finding | Resolution inspected |
| --- | --- |
| Profile input overwritten during load / edited while save snapshot is in flight | SettingsView.swift:23 disables the entire Form until loaded and during busy writes. Initialization assigns the profile before enabling the Form. The same State remains on write failure. |
| Placeholder-only labels | ClientsView.swift:31-44, ServicesView.swift:40-44, SettingsView.swift:12-14 and Theme.swift:25 now render separate persistent Text labels while retaining field prompts and identifiers. |
| Wrong failing-field focus | ClientsView.swift:62-72 validates required identity, note, recipient and template in visible order with their own focus values; ServicesView.swift:63 validates note with note focus. Theme.swift:23-25 receives the parent FocusState binding and applies it to the actual note editor. Input is retained on failure. |
| UI-test startup readiness race | ZapysUITests.swift:14-19 waits for Add to exist, be enabled and be hittable; timeout records a failure and returns before tapping. Additional assertions cover persistent client labels and preservation of an entered name after missing-phone validation. |

The API.md integration additions match the inspected app architecture and explicitly preserve deferred features and blocked platform evidence. No unchanged whole-tree checks, build attempts, tests or production edits were performed in this round. Runtime focus/keyboard behavior, label layout and platform execution still need the previously required macOS evidence.

Review artifact note: task-5-fix1.diff contains interleaved trailing-whitespace diagnostic lines, so it is not a clean patch artifact. This is a low-impact evidence-format issue, not source corruption or a product blocker. Actual changed source files were inspected directly; terminal Unicode rendering was not treated as file damage.

