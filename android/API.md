# Android repository API

## Task 10 contacts and local reminders

Fix round 1: production UI/scheduler consume time-aware `actionableAt`, `futureAlarmIds`, and `dueIds`; legacy state-only helpers remain for existing callers/tests. Backwards clock changes retain ERROR/NEEDS_ACTION outcome but suppress action/display until scheduledAt and restore future alarms. A subsequent due crossing becomes actionable again. Boot alone resets the remembered display ledger, restoring still-due notifications; ordinary foreground keeps the dismissed-notification ledger. Repository timezone metadata is persisted transactionally; reads and client/appointment saves rebase terminal timestamps without changing IDs or outcomes before eligibility/replacement comparisons. `loadForExternal` requires resumed activity and matching request epoch; onPause invalidates delayed callbacks, and subsequent explicit user action can retry. Native clock persistence/direct lifecycle-method fixtures are authored UNRUN.

`DeviceRules.reconcile(reminder, now)` promotes only due SCHEDULED rows; `actionable(id, rows)` accepts only a current NEEDS_ACTION/ERROR row with recipient. `alarmIds(rows, notificationsEnabled)` and `staleIds(retained, desired)` drive cancellation. `SQLiteRepository.reminders()` now persists due promotion without appointment edits. Optional `LocalProfile.notificationsEnabled=false` is backward compatible JSON; UI saves preserve it. Enabled client reminders require positive lead minutes (1–525600); zero would become due at the exact instant the appointment expires, so it is rejected on save.

`ContactPicker` uses ACTION_PICK + Phone.CONTENT_TYPE, querying only returned content URI on the activity serial executor. No READ_CONTACTS permission. Activity saves requesting form identity, returned URI and pending result; applies only after hierarchy restoration. Cancellation/unavailable provider leaves manual input usable. Foreground reconciliation never rebuilds client/appointment/settings forms.

`ReminderScheduler.reconcile(repository)` runs off UI after successful mutations, foreground, explicit queue refresh, boot and clock/timezone broadcasts. SharedPreferences retains OS IDs independently of filtered/deleted database rows. Inexact RTC alarms (no exact alarm permission) prepare privacy-safe local notifications only, with additional appointment-start expiry cleanup alarms. OS power management can delay both. Notification denial or disabled channel/profile leaves manual queue operational. Failures remain retryable at later reconciliation; receiver exceptions are contained. `SQLiteRepository.reconcileTimeZone()` rebases timestamps of pending and terminal rows on timezone change, retaining terminal IDs/outcomes; default zone is resolved dynamically, injected zones remain deterministic.

Queue preview reads current ID, and explicit open reads it again plus checks appointment start before launching. `MessengerShare.intent` uses ACTION_SEND text/plain targeted to selected messenger package; app unavailable/unsupported sharing => ERROR with retry/copy. Recipient must be selected manually. Only successful target-composer launch records HANDED_OFF, never sent/delivered. Preview dismissal keeps action pending; explicit reminder cancellation stores CANCELLED. Target-app cancellation/delivery outcome is unknowable and is labelled as such. Standard package IDs are supported; alternative client packages use copy fallback. No contact/message/notification is sent in tests.

New author fixtures: `DeviceRulesTest` (offline runnable), `DeviceIntentTest` (intent/profile fixtures, native UNRUN). Independent QA owns `IndependentDeviceTest` and `DevicePersistenceTest`. Device permissions, picker/provider recreation, actual composer support, alarms/Doze, boot and rendered UI require SDK/device execution and remain blocked on this host.

`SQLiteRepository(context: Context, databaseName: String = "zapys.db", now: () -> Long = { System.currentTimeMillis() }, zone: ZoneId? = null) : Repository, Closeable`

All methods are synchronous; UI must call them off the main thread. Each mutation is an atomic SQLite transaction. `close()` releases the helper; constructing another instance with the same name reopens durable storage. Schema version is 1; foreign keys enabled. Constructor clock/zone support deterministic reminder scheduling.

Implements the signatures in `domain/Models.kt`: clients/services/appointments/reminders return ordered detached values; saveClient/deleteClient, saveService/deleteService, saveAppointment(appointment, allowOverlap=false): Appointment, deleteAppointment, setReminderState, profile/saveProfile.

Validation throws ValidationException, unconfirmed conflicts throw ConflictException. A referenced client cannot be deleted (SQLiteConstraintException); deleting a catalogue service keeps historical snapshots. Appointment snapshots retain selection order and nullable minor-unit prices. Saving appointments snapshots the current client's name/phone/colour and atomically invalidates/replaces its sole outbox row. Client edits rebuild affected pending reminders; already cancelled/handed-off rows cannot be manually revived. LocalProfile is unverified device-local data.

Instrumentation: `PersistenceTest` uses named on-disk databases, closes/reopens the helper and deletes fixtures afterwards. No external messaging occurs. Android SDK/device execution remains a distinct required gate.

Reminder time uses appointment local date plus wall-clock minutes, then `LocalDateTime.atZone(zone)`: nonexistent DST times move forward by the gap and ambiguous times choose the earlier offset. Lead time is subtracted from the resolved instant. Templates accept both `[ім’я]` (U+2019) and `[ім'я]` (ASCII); substitution remains literal and single-pass.

`reminders()` filters every stored row against current appointment/client data and one clock reading inside a read transaction, hiding expired or mismatched rows. `setReminderState` rechecks eligibility inside its write transaction and deletes stale rows before accepting any transition, so a previously obtained ID cannot hand off an expired appointment.

## Task 4 native UI entrypoints

`ua.zapys.MainActivity` is the launcher. Four native destinations use calendar, clients, services, settings route keys. Calendar provides native day/seven-column week timelines and an accessible agenda with appointment detail/edit routes. Date/mode are retained in saved state.

`ClientsScreen.list/form`, `ServicesScreen.list/form`, `SettingsScreen.form` build native scrollable Views with `Theme` light/dark tokens. `MainActivity.load` performs reads on its serial background executor; `save` performs mutations there and reports errors inline without reconstructing forms. Both post main-thread callbacks only while their originating generation/activity is active. Destruction queues repository close after pending work. Navigation/back warns before discarding changed forms. Explicit stable field IDs, route, editing UUID, draft UUID and hierarchy are saved for activity recreation. Inputs/navigation are blocked while a save commits; repeated creation retries retain the same UUID.

A phone number is not a client identity key. Separate UUIDs may share a phone. `FormRules.duplicates` compares digits excluding the current UUID and the client form requests confirmation to save a separate person. It never merges or overwrites a different UUID; SQLite repository semantics are unchanged.

ClientsScreen provides selective platform contact picking with manual entry fallback, reminder preferences and history navigation. Settings exposes notification permission controls and the manual reminder queue. History lists persisted future/past records with vertically ordered service snapshots and notes. No automated messenger delivery is claimed.

No database reads or writes are performed by UI construction. Profile is optional, device-local and unverified; technical limits reference centralized Limits constants. Catalogue null price/duration are unknown, while zero price is free. No seed/demo records are inserted.


## Task 6 appointment editor

`AppointmentDraft` retains raw date/start/end/money strings and ordered detached service snapshots until validation. `fresh` has blank required end; `from` preserves stored interval/manualEnd; `repeat` creates a new UUID with blank date/start/end, draft status and cleared payment. `add`, `suggest`, and `build(clients)` centralize editor contracts.

`MainActivity.openAppointment(id?)` opens the full native editor. `openCalendarDraft(saved)` accepts an already persisted calendar draft and preserves its supplied interval; the calendar owns grid draft creation. `repeatAppointment` opens an unsaved new form. `AppointmentEditor.pack/unpack` plus separate raw override and inline client state preserve input across activity recreation. Calendar date/mode state keys are retained for the current grid.

`save(error, action, failure?, success)` retains the original Throwable on its serial executor and delivers optional typed failures to the active screen. Editor ConflictException presents conflicting records; only explicit confirmation retries the captured candidate with allowOverlap=true. Declining does not mutate the repository. Client history and the accessible chronological agenda open the same editor; end equal to captured now is history.

Inline new-client entry validates the appointment and client first, saves a separate UUID client, then saves the appointment. These are two transactions: if appointment save fails or overlap is declined, the newly created client remains available. Retry retains the same client UUID and form input. This does not merge shared-phone clients.

Fix round 1: all appointment save actions use AppointmentSaveSession.prepare(requestedStatus), returning an immutable appointment/client request without mutating the selected status. After successful inline client creation, clientPersisted clears inline input, updates the live client collection and selection; explicit later client choices are respected on retry. Invalid inline input blocks draft and back-save and remains editable. AppointmentDraft.build accepts optional requestedStatus/selectedClientId for nonmutating request preparation; existing default call contracts remain unchanged.

Inline client UUID lifecycle: successful clientPersisted advances session.inlineId, which the editor copies to MainActivity recreation state before clearing inline fields. Failed/in-flight/repeated prepare keeps its current UUID. The next populated inline creation therefore cannot overwrite the previously persisted client.

## Task 8 calendar

`CalendarScreen.create(activity, appointments, date, mode, change)` constructs the day/seven-column week screen. MainActivity retains date/mode across editor/back/recreation. Previous/next controls move seven days in both modes; Monday-first headings drill down to a day, and Today selects the current local date. The initial viewport is near 09:00; the complete 00:00–23:59 timeline and an accessible native agenda remain reachable.

`CalendarGeometry` is Android-independent: `weekDates`, `navigate`, `draft`, `resize`, `lanes`, `gradient`, `top`, `height`. Units are dp at 1.2/minute, with a 42dp gutter. Connected overlap groups use peak concurrency, adjacency reuses a lane, and cancelled records appear only in the agenda. Draft creation rounds to 15 minutes then clamps start to 23:29, retaining a 30-minute duration through 23:59. Resize returns an immutable copy, rounds to 15 minutes and clamps to start+1 through 23:59.

`CalendarView` draws exact proportional intervals, clips labels, and anchors gradient colors at logical font-metric row centers; omitted colors leave row positions intact, including rows beyond a short block. Zero colors use theme standard/draft fills, one is solid, and multiple stops use a shader whose span can extend beyond the block. Opaque theme-surface text rows protect readability. The agenda retains complete ordered service names and edit/end-time actions.

Slot creation and resize use MainActivity's serial save and typed ConflictException callback. The blank draft is persisted before opening the editor and announcing success. Resize dragging changes only preview data; touch cancellation clears it. Release opens a confirmation, then overlap requires a second explicit confirmation. Accessible end-time selection uses the same candidate and confirmation path. Declining either dialog performs no write. Native rendering, accessibility traversal, gestures, rotation and Android compilation/lint/device checks remain unverified pending SDK availability.

## Final source corrections

`CalendarPresentation.rows(appointment,height,rowHeight,week=false)` returns `CalendarRows(labels,metadata,compactTime)`. Compact week time and abbreviated status/client share the first logical row; `labelCenters(rowHeight,topPadding=4f)` anchors the client at that row's lower-half center, preserving every service center. Tall rows and day behavior retain their labels/metadata. The direct lifecycle fixture uses scoped reflection on the protected override for its detached activity; it does not establish framework Home/rotation ordering. Native compilation/rendering remain UNRUN.
