# Zapys — local MVP requirements

Decision date: 2026-11-02. Source: the user's approvals in the coordinating conversation. Implementation is now authorized; the earlier pause on development has been superseded.

## Product

Android and iPhone apps for one self-employed professional managing their own calendar. They create appointments themselves. No team calendars, client self-booking, breaks, personal-time blocks, attachments or medical records in this MVP. UI language Ukrainian. Existing `zapys-calendar-preview.html` and `zapys-demo.html` are the approved visual reference, not production apps.

## Local-first architecture

The newest user decision supersedes cloud-first storage: SQLite on the device for MVP, cloud later. Stable UUIDs, explicit schema versions, transactional writes and repository boundaries allow migration. A restart must preserve data. No network or account required to use the local calendar. Use native Kotlin Android and SwiftUI iPhone apps, in `android/` and `ios/`. SQLite storage on both. Default currency UAH, money in integer minor units, null means unknown and zero means free.

Local mode cannot establish ownership of a phone number/email or autonomously deliver messages through third-party messengers without an external identity/provider service. Therefore capture an optional local profile by phone/email, explicitly labelled unverified/local; do NOT pretend it is authenticated. Preserve the phone/email login requirement for cloud integration. Do not introduce fake OTPs/passwords or hard-coded credentials.

## Clients

Required name and phone. Optional surname, patronymic, note, colour. Selectively import name/phone from the OS contacts picker; no wholesale upload. Manual entry always works. Search by name/phone. In a client card show past/future appointments with services and notes. A client has reminder preferences: enabled, messenger channel, recipient identifier, lead time, editable template.

Android client saves (full form, inline new client and imported contact) require an ordinary full Ukrainian number: `0XXXXXXXXX`, `+380XXXXXXXXX`, `380XXXXXXXXX` or `00380XXXXXXXXX`, with nine national digits whose first digit is nonzero. Spaces, NBSP, hyphens and balanced parentheses are allowed; `+` is allowed only at the beginning. Reject letters, extensions, other country codes and wrong lengths. Canonical `+380…` is used only for validation/comparison; retain entered formatting in stored/displayed text. Equivalent numbers trigger the existing duplicate confirmation and retain separate client UUIDs. Invalid input stays in the focused phone field. Legacy invalid phones remain readable and unchanged; editing/saving the client requires correction, while appointments referencing that client remain usable. This checks format, not number existence; no carrier-prefix allowlist or short/service-number support.

## Services

Name required; duration, price, note and colour optional. Up to FIVE services PER APPOINTMENT, not five services in the catalogue. Services appear vertically, preserving selection order. Notes max 2000 characters is a conservative technical limit for this MVP, not a finalized commercial tariff. Durations, when specified, positive whole minutes; price nonnegative. Store appointment service snapshots so editing a catalogue item does not silently change historical appointments.

As approved in the service discussion, selecting a service copies its duration and price into the appointment. Both may be changed or cleared for that particular visit without changing the catalogue item. Persist those overrides in the appointment snapshot; unknown and zero/free remain distinct.

## Appointments

Two creation routes: New appointment opens a form with end time required; tapping a calendar day/time immediately persists a blank 30-minute draft. Draft may have no client/services. A completed appointment requires a valid client, date, start/end with end greater than start, at most five services. Services can be empty. End can be set manually; selected service durations may suggest end only if all are known and end was not manually set. Manual override persists. Day boundary: no implicit overnight appointment; end <= 23:59; handle final slots explicitly without invalid 24:00.

Day timeline: time-positioned blocks; resize lower edge to alter end with 15-minute snapping. Week: seven day columns, time-positioned blocks, tapping day heading opens that day. Preserve week navigation, today and day/week switch. Overlapping appointments are allowed ONLY after an explicit warning/confirmation for save/resizing. Cancel leaves original untouched. Adjacent appointments (end == next start) do not overlap. Exclude cancelled appointments and the appointment itself from conflicts. Render overlaps visibly rather than covering one another.

Statuses: planned, completed, cancelled, no-show; drafts separate. Cancelling removes pending reminders. Repeat appointment copies client/services into new draft/form with a NEW id; user supplies new date/time; reset payment/status/reminder state. Paid/unpaid flag and optional actual received amount, nonnegative minor units. Notes and null/free price distinctions survive reopening.

## Colours

Client and each service have optional colour, unset by default. Appointment: client name at top followed by a vertical service list. Gradient anchors align with their labels: client colour then each coloured service in display order. Unset colours do not contribute stops. One colour => solid fill; no colours => standard appearance. Ensure readable text regardless of chosen colours.

## Reminders

Channels in preferences: Viber, WhatsApp, Telegram, Instagram. Lead time configurable, examples 24h/1h. Enabled reminders require a positive whole-minute lead of 1..525600; disabled saved preferences may retain 0. A zero lead has no actionable window before appointment-start expiry. Editable text and variables `[ім’я]`, `[дата]`, `[час]`, `[послуги]`; substitute appointment data literally, never execute template text. MVP free entitlement: at most ONE reminder per appointment. Do not invent unlimited paid quotas or payment integration.

Store a reminder/outbox record, render states scheduled/needs manual action/handed to messenger/error/cancelled. Do NOT call a share sheet opening "sent" or "delivered". Local mode can prepare a message and open an OS share composer manually, with explicit notice that automated delivery is not connected. Local notifications can alert the professional to send, with user permission and privacy-aware contents. A future provider adapter is responsible for real sent/error receipts. Rescheduling/cancelling must invalidate stale reminder times and text. Avoid sending reminders for past or cancelled appointments. Account for denied notification/contact permission gracefully. No automatic external messages during development/tests.

## Limits and deferred scope

Client/appointment storage needs maximum limits, but exact commercial quotas were not finally approved. Conservative technical ceilings for local MVP: 1000 clients, 10000 appointments, 2000 characters per note/template; centralize constants and label these implementation defaults. Max 5 services per appointment and 1 free reminder are approved. No payments/subscriptions/cloud sync in this offline version. Never let a local profile claim cloud backup.

## Acceptance

Client/service CRUD; validation; contact picker; durable drafts and appointments; day/week navigation; resizing with conflict confirmation; multiple ordered services and gradient; status/payment; repeat; history; reminder preferences/template/outbox; honest local profile; persisted settings; meaningful domain and persistence tests; Android APK build and lint when toolchain available; iOS simulator build/tests on macOS CI. Record actual test evidence separately from checks that cannot run on Windows. No PR merge.

