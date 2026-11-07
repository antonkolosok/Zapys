# Zapys: Android UX improvements — specification

Date: 2026-11-06, UTC. Source: the user's ten-item improvement request in the Zapys chat.

## Scope and authority

Implement the ten changes below as ten separate tasks. The user requested a new local Codex chat to execute with Superpowers and appropriate agents, tests and a commit after each task, and one aggregate PR only after the entire set. No merge.

This batch targets the existing Android Kotlin/Views app that the user is testing. This is an implementation assumption based on the conversation, not a request to change the SwiftUI app simultaneously. Keep iOS builds and behavior separate; do not claim iOS parity or execution. Preserve SQLite data and the native architecture.

This specification supersedes conflicting older Android guidance about the recipient field, color picker, initial calendar mode and fresh-form status. Update the corresponding sections of `docs/REQUIREMENTS.md` and `docs/DESIGN.md` in the task that implements the change, marking Android-specific decisions explicitly.

## Global constraints

- All coordinator-created agents must load and apply the relevant Superpowers skills.
- Execute tasks 1–10 sequentially: relevant failing check, implementation, tests, independent review, separate commit, recorded SHA. One aggregate PR only after all ten pass. No merge or auto-merge.
- Keep Android minSdk 26, target/compileSdk 35, Java17, Kotlin2.1.20 and AGP8.9.2 unless a demonstrated blocker requires a separate explicit decision. Do not migrate UI frameworks or test frameworks for this batch.
- Use synthetic data. No real contact imports, external message sending, credentials in files, or destructive database migration.
- Preserve UUIDs, client/service snapshots, appointment history, payment, notes and reminder correctness. No silent data deletion, truncation or bulk normalization.
- Preserve light/dark themes, Ukrainian UI, accessible names/selected states, system insets and readable large text. No fixed heights that clip enlarged text.
- Date/time bounds remain 00:00–23:59 within one day. Moving a record preserves its duration. Conflicts require explicit confirmation and cancellation leaves original data untouched.
- Device/UI checks are distinct from compilation and offline domain tests. Record PASS/FAIL/BLOCKED/UNRUN honestly.

## 1. Monday button at the left edge

The independent Monday-first seven-day selector must span the available content width, with no empty 42dp cell before Monday. Its left edge matches the calendar content edge, retaining the normal outer padding. Seven equal cells, including Sunday, must fit without horizontal clipping.

The current spacer also aligns the week grid's headings with its time-label gutter. Do not remove or shrink the grid's `CalendarGeometry.GUTTER=42f`, and do not mislabel week columns. Separate the full-width date selector from the week grid's compact day headings: only the latter retain the shared gutter and align directly above the seven grid columns. Day mode needs only the full-width selector. Preserve today, selected date, Monday-first order and tap-to-day behavior.

Check 320/360/411dp widths, month/year boundaries, and that each week heading matches its actual column. Use no screenshot-only inference as proof of pixel alignment; test measured coordinates and inspect rendering.

## 2. Settings bottom-navigation alignment

All four destinations share the same top/bottom bounds and vertical alignment. The full label `Налаштування` remains readable, selected state remains visible, and every item remains clickable above system navigation insets.

Fix the navigation container, not every button in the app. Disable inappropriate text-baseline alignment and allow a common measured row height that accommodates the longest label. At 320/360/411dp and font scales 1.0/1.3/2.0, text must not overlap, disappear, or push one button below the others. Portrait and landscape must remain usable. Touch targets are at least 48dp high.

## 3. Visual preset and custom-color palette

Replace the shared HEX-code spinner in client and service forms with a color swatch control. Opening it shows the existing four preset colors and a full custom picker (hue control plus saturation/value area), with a live swatch preview. Also provide `Без кольору`, confirm and cancel.

Do not use a hexadecimal code as the visible selected value. Provide accessible preset names, a selected indicator, and a named custom-color control. New custom choices are opaque RGB; preserve any existing stored color unchanged until a new choice is confirmed. Cancel changes nothing. Clearing stores `null`. Restore unsaved selection on activity recreation; committing a choice fires the change callback once, while initial rendering/restoration must not mark the form dirty.

Keep `Theme.colors(parent, initial, onChange): () -> Int?` compatible with its callers. Preserve client/service save-reopen behavior and calendar gradient/text contrast for arbitrary colors. No new external UI dependency is needed.

## 4. Ukrainian client phone validation

Validate client phone input in the full form, inline new-client form and imported contact save path. Accept full Ukrainian mobile and geographic fixed-line formats: `0XXXXXXXXX`, `+380XXXXXXXXX`, `380XXXXXXXXX`, or `00380XXXXXXXXX` (nine national significant digits after country code). Permit presentation spaces, NBSP, hyphens and balanced parentheses; an optional `+` is allowed only at the beginning. Reject letters, extensions, misplaced/repeated plus signs, other country codes, short local numbers, wrong lengths and a national part beginning with `0`.

Provide one pure `PhoneRules.normalize(raw: String): String?` returning canonical `+380…` or `null`; use it consistently for validation and duplicate comparison. Preserve user formatting in stored/displayed phone text. Existing invalid records remain readable and unchanged; editing/saving that client requires correcting its phone. Existing appointments referencing such clients must not fail merely because an old phone is invalid.

Do not use a stale carrier-prefix allowlist or claim to verify that a number exists. This batch validates the format of ordinary full client numbers, not emergency/short/service-number dialing. The country code and ordinary nine-digit national format follow the [ITU Ukraine numbering plan](https://www.itu.int/dms_pub/itu-t/oth/02/02/T02020000DB0009PDFE.pdf), consulted 2026-11-06. Formatting tolerance is a product decision.

Example error: `Вкажіть український номер: 0671234567 або +380671234567`. Focus the phone field and preserve invalid input. Normalize only for comparisons; duplicate confirmation must still create a separate UUID rather than upserting another client.

## 5. Each personal-name field has a 50-character limit

First name, surname and patronymic each permit at most 50 Unicode code points after trimming outer whitespace. Define this consistently in domain validation and form errors. First name remains required; surname and patronymic remain optional. The combined display name may exceed 50.

Keep Ukrainian/other Unicode letters, apostrophes, hyphens and internal spaces. Do not silently truncate typing, pasted text, imported names or legacy data; show the limit/error and block saving until corrected. Apply the same rule to the inline first-name field and contact-import save path. Saving must focus the first invalid field. Preserve input after errors and recreation.

## 6. Contact picker action at the top

Place `Обрати телефон з контактів` directly below the client-form heading/back area and before name/phone fields, on both create and edit screens. Keep existing selective contact access, cancellation, manual entry, async result ownership and recreation handling. A selected contact updates only name/phone. Existing limits/phone validation still run before saving; no silent truncation or import of other contacts.

## 7. Remove recipient field; hide inactive inline client form

Keep both parts in task 7, as in the user's numbered request.

### 7a. Recipient

Remove `Одержувач у месенджері` from client forms and recipient-only clutter in reminders. The actual adapter opens a manual `ACTION_SEND` composer; the user chooses the recipient in the external messenger. Enabled reminders with a blank recipient must validate and become actionable under the existing time/state rules.

Retain legacy recipient properties/JSON for compatibility without exposing the field, erasing stored values, or inventing a recipient from the phone. Remove recipient-presence gates from manual sharing only; retain stale-ID, schedule, cancelled/terminal-state and lifecycle checks. Never imply automatic routing or successful delivery. No schema migration or real message send.

### 7b. Existing client vs inline client

When an existing client is selected, hide both inline-new-client labels and fields immediately, on initial render and after recreation. Selected existing identity is authoritative at the save-session boundary: hidden nonblank or invalid inline strings must never create a client, trigger inline validation, or replace that identity.

Retain inactive draft text in memory/state so switching back to `Без клієнта` restores it. Only the active inline path may validate and create a client. Keep retry semantics: if client creation succeeded but appointment save failed, retry uses that same client ID.

## 8. Planned is the default normal status

The normal status picker contains `Заплановано`, `Завершено`, `Скасовано`, `Не з’явився / не з’явилась`, with no `Виберіть` placeholder and no `Чернетка` entry. Fresh unsaved forms opened by `+ Новий запис` default to `PLANNED`. Map picker positions to an explicit enum list, not raw enum ordinals.

Do not erase draft behavior. Calendar empty-slot taps still persist a 30-minute DRAFT; explicit/back `Зберегти чернетку` remains available, and repeat still creates a separate draft with cleared payment/date/time as currently specified. Editing an existing draft may display `Заплановано` for primary save, but rendering the picker must not mutate the persisted draft or its in-memory DRAFT identity. Existing non-draft statuses must round-trip unchanged until the user selects another.

## 9. Move existing appointments in the calendar

Implementation decision: a short tap opens the editor; a long press on the appointment body activates move, followed by drag and release. This preserves ordinary vertical scrolling. The existing bottom resize handle retains its distinct resize behavior.

Move vertically in day/week views; in week view horizontal motion selects another visible day. Preserve finger offset and duration. Snap the movement delta to 15 minutes, preserving non-quarter-hour starts on a purely horizontal move. Clamp start to `0..(1439-duration)` without shortening duration. Preserve all fields except date/start/end, including UUID, status, snapshots, payment and `manualEnd`.

Show immutable preview and target date/time; never write to SQLite during pointer movement. A changed valid drop opens a confirmation with date/start/end, then uses existing transactional save/conflict handling. No-op, cancel/back/recreation, extra-pointer cancellation or release outside the grid produces no save. Returning from a cancelled gesture must restore normal scrolling/tapping. Edge scrolling during an active drag must allow reaching earlier/later visible times without jumping the record; no automatic week switching.

Save success persists once, survives reopen and reconciles reminders. Conflict refusal or storage failure leaves the original appointment/reminder state intact. Expose an accessible `Перенести запис` date/time action using the same domain move/persist path, because a drag cannot be the only available action.

## 10. Week selected by default

Fresh calendar launch defaults to `week`, with seven date columns and the week switch selected. Choosing `day` still works; retain that intentional choice and date across recreation and return from editors/settings. Do not reset to week on every render/navigation. No new persistent preference is required for this batch.

## Completion

Every task has actual test evidence, clean independent review and its own commit. Final full Android regression and device interaction checks pass; documentation reflects changed Android behavior. Then open one aggregate PR with the ten-task checklist, commit list, test evidence and any independently scoped iOS limitations. Leave it unmerged. A task with required checks blocked is not complete and does not satisfy this PR gate.
