# Task 6 independent Android editor review

Spec verdict: changes requested. Most domain contracts are implemented, but native editor state transitions still have user-visible data-loss/status inconsistencies.

Scope: reviewed the supplied task-6-android.diff once against the shared-before snapshot (baseline 4716bb6), then inspected final changed source and relevant REQUIREMENTS/DESIGN sections. Concrete dependencies inspected: Theme.onChange/field for raw-input listeners and labels; FormRules/Rules/Models for validation, identity, status ordering and history boundaries. Production files were not modified.

## Strengths

- AppointmentDraft preserves stable identity/raw values; fresh end is blank. Validation enforces same-day end > start and <=23:59 and existing client outside drafts.
- Ordered detached snapshots are capped at five, with nullable visit-only duration/price, distinct free/unknown money, preserved notes/colours, and no catalogue writes. Manual end survives suggestions and serialization.
- Shared phone numbers do not replace UUID identity. Inline creation validates required name/phone before its separate transaction.
- ConflictException remains typed; only explicit positive confirmation retries the captured candidate with allowOverlap=true. Decline does not change the original persisted appointment.
- Repeat allocates a new UUID, blanks date/start/end and resets status/payment/manual-end. History uses one captured now and exact-end inclusion through FormRules.isHistory.
- Bundle/raw override storage retains input across recreation and ordinary save failures. Visible field labels, validation focus and honest local/manual-delivery copy are present.

## Ranked findings

### P2 — Draft save silently drops inline client input

`android/app/src/main/java/ua/zapys/ui/AppointmentEditor.kt:80` (also MainActivity.kt:71).

Enter a new client name/phone, then select Save draft or Back → Save draft. Both paths call candidate(), which only reads d.clientId; the inline fields are handled exclusively by the primary-save branch at lines 74–77. The appointment saves without that client, and finish() clears both inline strings (MainActivity.kt:106). Reopening cannot recover the entered client details. Drafts may omit a client, but saving a form must not silently discard the client input the user already supplied. Reuse inline-client validation/persistence when populated, or retain those raw fields durably with the draft.

### P2 — Failed draft save changes status behind the visible selector

`android/app/src/main/java/ua/zapys/ui/AppointmentEditor.kt:80`.

Open a completed/no-show appointment and choose Save draft. The handler immediately sets d.status=DRAFT, but never updates the status Spinner. Only synchronous ValidationException restores the old value. A database failure or declined overlap leaves the form showing its original completed/no-show selection while d.status remains DRAFT. Primary Save then changes DRAFT to PLANNED (line 73), silently replacing the original status. Retain/restore the original status until successful save, or consistently update the visible control and retained state for failure/decline.

### P2 — Inline retry state overrides a later explicit client selection

`android/app/src/main/java/ua/zapys/ui/AppointmentEditor.kt:74` (client selection at line 34).

Create an inline client, then decline an appointment overlap or encounter an appointment write failure. The new client transaction has succeeded, but inline name/phone remain populated. Select a different existing client in the Spinner and retry Save. The nonblank inline branch still runs, rewrites the inline client and sets d.clientId back to its UUID (line 77), so the appointment is assigned to a different client from the visible selection. Clear/transition inline creation state once its transaction succeeds and synchronize the selector, or make the two selection modes explicitly exclusive.

No P1 findings identified. These findings concern editor source paths and are not claims of observed device behavior.

## Quality verdict and verification limits

Changes requested before Task 6 acceptance. The domain/state separation and transactional conflict boundary are sound foundations, but primary save, draft save and inline creation duplicate state transitions and diverge on failure paths. Add focused coverage for the three interactions above when fixing them.

Reported evidence reviewed: final offline domain suite OK (49 tests); focused initial editor suite 3 tests and independent editor suite 7 tests. Tests were not rerun for unchanged code. These tests exercise domain objects rather than Android control/state interactions. Three real SQLite instrumentation tests are authored and remain unrun.

Separate blocked gates: Android SDK compilation, lint/APK, instrumentation execution, and device visual/rotation/focus testing are BLOCKED by the unavailable native toolchain. Source review does not establish compilation or runtime acceptance. Ukrainian source text is present (verified Unicode characters despite terminal rendering); visual legibility, control accessibility and recreation behavior still require native execution. Calendar grid, device integration, cloud/authentication and delivery are outside this review.
## Fix round 1 scoped re-review

Reviewed task-6-fix1.diff and the appended implementation report. Scope was the original three P2 findings and regressions introduced by their fixes. No tests rerun; reported focused regression/compatibility evidence is JUnit OK (13 tests).

| Original finding | Result | Source basis |
| --- | --- | --- |
| Draft save drops inline client | Addressed | Primary, draft and back-save now share submit/session.prepare, which validates and includes populated inline client input before either write. |
| Failed draft save changes hidden status | Addressed | requestedStatus is passed into build; preparation does not mutate d.status or the visible selector. |
| Inline retry overrides explicit selection | Addressed | Successful client persistence clears the session and Activity inline strings and updates the live selector; retry with blank inline input respects draft.clientId. |

### New P2 — A second inline creation overwrites the first saved client

`android/app/src/main/java/ua/zapys/domain/AppointmentSaveSession.kt:12` (immutable inlineId at line 5; editor transition at AppointmentEditor.kt:84).

Create inline client A, save, then decline the appointment conflict (or encounter an appointment write failure). The fix now clears the inline fields and presents A in the selector. Enter a different new client B into the freshly cleared inline fields and save again. prepare() still constructs B using the same immutable inlineId as A; MainActivity.inlineClientId is also unchanged. saveClient therefore updates A's existing UUID instead of creating a separate client. The live session client list additionally retains A's old display data because clientPersisted only adds absent UUIDs. Concrete dependency inspection confirms SQLiteRepository.saveClient at lines 56–59 writes by supplied UUID. This overwrites a durable client outside the appointment transaction even if the second appointment attempt is declined.

Allocate a fresh inline UUID after a successful client transaction, keeping session and recreation state synchronized; retain a UUID only while its creation is still unsaved/in flight. Add a focused two-successive-inline-creation regression that proves distinct UUIDs and preservation of the first client.

Updated spec verdict: original three findings addressed; changes requested for the new client-identity regression. Updated quality verdict: unified immutable request preparation improves failure consistency, but the inline creation lifecycle needs the additional identity transition above.

Verification limits unchanged: Android compilation, lint/APK, instrumentation and device UI/rotation remain BLOCKED. The three SQLite instrumentation cases remain authored but unrun. This re-review establishes source-level findings only.
## Fix round 2 scoped re-review

Reviewed task-6-fix2.diff and appended report solely for the successive inline-client UUID P2 and regressions in this small fix.

UUID lifecycle finding: addressed. AppointmentSaveSession advances its private-set inlineId only after clientPersisted; failed/repeated prepare retains the unsaved identity. AppointmentEditor immediately copies the new ID to MainActivity.inlineClientId before clearing fields. Existing Activity state save/restore therefore retains the next creation identity across recreation. The already captured appointment request continues to reference the successfully saved client. A later populated inline entry receives a distinct UUID, preserving the first client and its live-list entry.

The added focused regression checks stable retries, successive distinct clients, unchanged first-client data and reconstruction with the next UUID/raw state. Reported RED reproduces the defect and reported GREEN is OK (4 AppointmentSaveSessionTest tests). No unchanged tests were rerun for this review. No additional finding identified in this scoped change.

Final spec verdict: all review findings addressed; source-level Task 6 acceptance recommended within the reviewed scope. Final quality verdict: acceptable with explicit native verification gates outstanding. This does not establish Android compilation or device correctness. SDK compile, lint/APK, the three authored SQLite instrumentation cases, and visual/rotation/focus runtime verification remain BLOCKED/unrun as previously recorded.