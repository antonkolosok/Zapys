# Zapys Local MVP Implementation Plan (superseded)

The user requested sequential tasks. The authoritative plan is now `docs/superpowers/plans/2026-11-02-local-mvp.md`. This initial broad allocation is retained only as history. Do not execute it.

> For agentic workers: use the applicable subagent-driven-development workflow; the user explicitly requested autonomous parallel Android/iOS work. Root owns integration, commits and final review.

**Goal:** Native, offline SQLite appointment apps for Android/iPhone covering `docs/REQUIREMENTS.md`, ready for review in an unmerged PR.

**Architecture:** Independent native presentations with pure domain rules and repository interfaces. SQLite transactions persist clients, services, appointments, ordered service snapshots, reminder preferences/outbox, and local profile. Device services use explicit contact/notification permissions. External identity and messenger delivery are truthfully unavailable until services are connected.

**Tech stack:** Kotlin/Android native UI, SQLiteOpenHelper; Swift/SwiftUI, SQLite3; JUnit, XCTest; GitHub Actions Android and macOS jobs.

**Spec:** `docs/REQUIREMENTS.md`

## Global constraints

- Ukrainian UI, max FIVE services per appointment, one reminder per appointment in Free.
- Name/phone required for clients; service duration/price/note optional; unknown differs from zero.
- Overlap requires warning and explicit continue; no automatic external messages.
- SQLite must survive process restart; atomic validation/save; UUID identity; versioned schema.
- Never merge; no invented verification, delivery, authentication or cloud claims.

## Review focus

1. Midnight/end boundaries and adjacent intervals; cancelling resize restores old time.
2. Editing catalog/client data does not corrupt historical visit data; unknown prices remain null.
3. Failed saves/constraint errors do not leave orphaned appointment/reminder rows.
4. Reschedule/cancel invalidates pending reminders; sharing never sets sent.
5. Denied permissions, empty contacts, phone formats and restart preserve usable local mode.

## Tasks

### 1. Shared specification and design
- [x] Write requirements, ownership and implementation plan.
- [ ] Designer reads approved HTML and writes `docs/DESIGN.md` with screen flow, tokens, accessibility and gradient treatment.
- [ ] Root sets up feature branch, project-local tools and reproducible CI.

### 2. Android application
**Owner/files:** Android developer, `android/`; QA owns `android/app/src/test/` and `android/app/src/androidTest/` after coordination.
**Interfaces:** Kotlin domain models (Client, Service, Appointment, Reminder, LocalProfile), repository CRUD and domain validation accessible to JUnit. SQLite repository and native UI consume those models. Expose in `android/README.md` exact test/build commands and API locations.
- [ ] Write meaningful failing domain tests for interval validation, conflicts, template substitution, max services, reminder limit, money/null and repeat reset.
- [ ] Implement domain rules and transactional SQLite CRUD; add restart/persistence tests.
- [ ] Implement day/week calendars, appointment editor, clients/history, services, settings/profile and reminder outbox.
- [ ] Implement contact picker/manual sharing and permission-denied behavior.
- [ ] Run unit tests, lint, debug APK build and instrumentation when possible; fix errors.

### 3. iPhone application
**Owner/files:** iOS developer, `ios/`; QA owns dedicated test files after coordination.
**Interfaces:** Swift domain models and SQLite repository testable as a Swift package; SwiftUI app and Xcode project consume local package. Expose exact commands/API in `ios/README.md`.
- [ ] Write failing XCTest domain cases matching Android acceptance rules.
- [ ] Implement SQLite repository, domain rules and versioned migrations; test durable reopen.
- [ ] Implement day/week calendar, resizing, editors, contacts/history/services/settings/profile/outbox.
- [ ] Integrate Contacts picker, local notifications/manual share; correct permission handling.
- [ ] Build/test pure domain where possible and iOS simulator in macOS CI, not a fabricated Windows pass.

### 4. Independent QA and review
- [ ] Android QA adds edge/integration tests and writes `docs/qa/android.md`.
- [ ] iOS QA adds edge/integration tests and writes `docs/qa/ios.md`.
- [ ] Independent reviewer checks both apps against spec and security/privacy assumptions; `docs/reviews/final.md`.
- [ ] Fix material findings; rerun affected checks and whole app suites.

### 5. Delivery
- [ ] Root writes README, current status, test evidence and PR description.
- [ ] Commit code on `feat/local-mvp`, push if authenticated repository access exists, open PR; NEVER merge.
- [ ] If GitHub/macOS access is unavailable, preserve all work and prepared PR metadata, state precise outstanding actions and test blockers.
