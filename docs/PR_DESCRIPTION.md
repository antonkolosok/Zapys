# Native local calendar MVP

Implements Ukrainian native Android (Kotlin) and iPhone (SwiftUI) source apps backed by device-local SQLite. A professional can manage clients/services, create durable calendar drafts and appointments, override ordered service snapshots per visit, navigate day/week views, confirm overlaps/resizing, record status/payment and repeat appointments with a new identity.

Adds selective OS contact pickers, an explicitly unverified local profile and a durable single-reminder outbox. Reminders prepare text for manual messenger handoff; no authentication, automatic external delivery or sent/delivered receipt is simulated. iOS schedules a conservative window of 60 future notifications while retaining the full outbox and requiring later reconciliation. Unknown share extensions remain retryable.

## Validation

- Fresh pre-publication run on 2026-11-06: `./scripts/test-domain.ps1` passed all 84 JUnit tests using Kotlin 2.0.20/JUnit 4.13.2, exit 0. This checks the pure domain only; native verification limits are in `docs/qa/verification.md`.
- Android application and instrumentation sources now compile with Kotlin2.1.20/AGP8.9.2/SDK35 after fixing the Theme receiver mismatch and declaring platform test libraries. Both debug APK assemble tasks pass (exit0). Full Gradle unit/lint remain blocked by uncached JUnit4.13.2/lint-gradle31.9.2 and restricted network access. Real SQLite instrumentation and native UI execution remain UNRUN. Evidence: `artifacts/android-theme-fix-2026-11-06/`.
- All Swift compilation, XCTest, Xcode app build, simulator/UI, native contacts/notifications/sharing and rendered accessibility: UNRUN/BLOCKED on Windows without Swift/Xcode.
- iOS source membership and project/XML checks are static evidence only. No remote CI run or artifact exists.
- CI source covers Android unit/lint/APK/instrumentation compilation and emulator execution, Swift package tests and iPhone simulator tests. Run instructions and concrete QA matrices are in README and `docs/qa/`.
- Following the user's Appetize choice, CI source also builds an unsigned ARM64 iOS Simulator archive after UI tests and publishes a `zapys-ios-appetize` artifact. That packaging and any Appetize upload/runtime remain UNRUN; the Windows Android build helper prepares the same native checks for execution outside this restricted environment.

## Publication and remaining gates

This draft proposes `feat/local-mvp` into `main`, based on the existing empty baseline `4716bb6`. It includes the native application sources, tests, CI, build helpers, documentation and reference prototypes. Generated build outputs, local toolchains and test artifacts are excluded. Native checks remain pending; this request does not establish runtime readiness.

Whole-source review and one scoped re-review accepted all six source corrections, including referenced-client deletion protection, linear reminder reconciliation and calendar semantics. Before accepting runtime readiness, run native compilation/tests and manual permission, lifecycle/boot/clock, gesture, persistence, rendering and installed-messenger adapter checks. No external contacts/messages were used. No merge, auto-merge or release is authorized.

