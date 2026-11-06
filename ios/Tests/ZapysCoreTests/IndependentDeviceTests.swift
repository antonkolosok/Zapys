import XCTest
import CSQLite
@testable import ZapysCore

/// Independent Task 11 contracts. Synthetic files/pure policy only; never opens
/// contacts, notification center, clipboard, share UI or a messaging application.
/// Authored on Windows. Swift compilation and all XCTest execution are UNRUN.
final class IndependentDeviceTests: XCTestCase {
    private func instant(_ value: String) throws -> Date {
        try XCTUnwrap(ISO8601DateFormatter().date(from: value))
    }
    private func file() -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent("device-qa-\(UUID().uuidString).sqlite")
    }
    private func remove(_ url: URL) {
        for suffix in ["", "-wal", "-shm", "-journal"] { try? FileManager.default.removeItem(atPath: url.path + suffix) }
    }
    private func client() -> Client {
        var value = Client(name: "Synthetic", phone: "123")
        value.reminder.enabled = true; value.reminder.recipient = "synthetic-recipient"
        value.reminder.leadMinutes = 60; value.reminder.template = "Synthetic preview"
        return value
    }
    private func visit(_ client: Client) -> Appointment {
        var value = Appointment(day: "2030-01-02", startMinute: 600, endMinute: 630)
        value.clientID = client.id; value.isDraft = false
        return value
    }
    private func row(_ id: Int, due: TimeInterval, state: ReminderState = .scheduled) -> Reminder {
        Reminder(id: UUID(uuidString: String(format: "00000000-0000-0000-0000-%012d", id))!,
                 appointmentID: UUID(), dueAt: Date(timeIntervalSince1970: due), channel: .telegram,
                 recipient: "synthetic", message: "Synthetic preview", state: state)
    }
    // Catch lead=0 silently producing a reminder that expires at its due instant.
    func testEnabledLeadMustBePositiveWhileDisabledZeroRemainsValid() throws {
        var value = client()
        for lead in [1, 60, 525600] { value.reminder.leadMinutes = lead; XCTAssertNoThrow(try Rules.validate(value)) }
        for lead in [-1, 0, 525601] { value.reminder.leadMinutes = lead; XCTAssertThrowsError(try Rules.validate(value)) }
        value.reminder.enabled = false; value.reminder.leadMinutes = 0
        XCTAssertNoThrow(try Rules.validate(value))
    }

    // Catch state-only eligibility and clock rollback permitting a premature share.
    func testClockBackwardsThenForwardsGatesActionAndFutureNotificationsForEachRetryableState() {
        for state in [ReminderState.scheduled, .needsManualAction, .error] {
            var value = row(1, due: 100, state: state); value.error = state == .error ? "Synthetic failure" : nil
            XCTAssertTrue(DevicePolicy.actionable(value, now: Date(timeIntervalSince1970: 100)))
            XCTAssertFalse(DevicePolicy.actionable(value, now: Date(timeIntervalSince1970: 99)))
            XCTAssertEqual(DevicePolicy.notificationWindow([value], now: Date(timeIntervalSince1970: 99), enabled: true), [value])
            XCTAssertTrue(DevicePolicy.notificationWindow([value], now: Date(timeIntervalSince1970: 100), enabled: true).isEmpty)
            XCTAssertTrue(DevicePolicy.actionable(value, now: Date(timeIntervalSince1970: 101)))
        }
        for state in [ReminderState.handedToMessenger, .cancelled] {
            let value = row(2, due: 100, state: state)
            XCTAssertFalse(DevicePolicy.actionable(value, now: Date(timeIntervalSince1970: 101)))
            XCTAssertTrue(DevicePolicy.notificationWindow([value], now: Date(timeIntervalSince1970: 99), enabled: true).isEmpty)
        }
        var blank = row(3, due: 100); blank.recipient = " \t\n"
        XCTAssertFalse(DevicePolicy.actionable(blank, now: Date(timeIntervalSince1970: 100)))
    }

    // Catch permission denial accidentally disabling the manual outbox.
    func testDeniedNotificationsLeaveDueManualQueueUsable() {
        let due = row(1, due: 100, state: .error), future = row(2, due: 200)
        XCTAssertTrue(DevicePolicy.notificationWindow([future, due], now: Date(timeIntervalSince1970: 100), enabled: false).isEmpty)
        XCTAssertTrue(DevicePolicy.actionable(due, now: Date(timeIntervalSince1970: 100)))
    }

    // Catch retaining the last 60, input-order selection, and stale IDs after window refresh.
    func testEarliestBoundedWindowReplacesStaleIdsWithDeterministicTies() {
        let now = Date(timeIntervalSince1970: 0)
        let rows = (1...70).reversed().map { row($0, due: Double($0)) }
        let window = DevicePolicy.notificationWindow(rows, now: now, enabled: true)
        XCTAssertEqual(window.map(\.dueAt), (1...60).map { Date(timeIntervalSince1970: Double($0)) })
        let shifted = DevicePolicy.notificationWindow(rows, now: Date(timeIntervalSince1970: 10), enabled: true)
        XCTAssertEqual(shifted.map(\.dueAt), (11...70).map { Date(timeIntervalSince1970: Double($0)) })
        XCTAssertEqual(Set(window.map(\.id)).subtracting(shifted.map(\.id)), Set((1...10).map { row($0, due: Double($0)).id }))
        let ties = [row(3, due: 100), row(1, due: 100), row(2, due: 100)]
        XCTAssertEqual(DevicePolicy.notificationWindow(ties, now: now, enabled: true).map(\.id), [ties[1].id, ties[2].id, ties[0].id])
    }

    // Catch treating any sheet presentation/completion as a messenger handoff.
    func testShareCancellationCopyUnknownActivityAndErrorRemainDistinctFromSupportedHandoff() {
        for activity in [nil, "com.apple.UIKit.activity.CopyToPasteboard", "com.apple.UIKit.activity.Mail", "unknown.extension"] as [String?] {
            XCTAssertEqual(DevicePolicy.shareOutcome(completed: true, activity: activity, failed: false), .retry)
        }
        let messenger = "net.whatsapp.WhatsApp.ShareExtension"
        XCTAssertEqual(DevicePolicy.shareOutcome(completed: false, activity: messenger, failed: false), .retry)
        XCTAssertEqual(DevicePolicy.shareOutcome(completed: true, activity: messenger, failed: true), .error)
        XCTAssertEqual(DevicePolicy.shareOutcome(completed: true, activity: messenger, failed: false), .handoff)
    }

    // Catch pre-due acceptance, start-time acceptance and expired rejection rolling back refresh.
    func testSQLiteActionRevalidatesDueAndExpiryAndCommitsInvalidation() throws {
        let url = file(); defer { remove(url) }
        let before = try instant("2030-01-02T08:59:59Z"), due = try instant("2030-01-02T09:00:00Z"), start = try instant("2030-01-02T10:00:00Z")
        let repository = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 0)! })
        let c = client(); let a = visit(c)
        try repository.saveClient(c, now: before); try repository.saveAppointment(a, now: before)
        let id = try XCTUnwrap(repository.reminders(now: before).first?.id)
        XCTAssertThrowsError(try repository.actionableReminder(id, now: before))
        XCTAssertThrowsError(try repository.markHandedToMessenger(id, now: before))
        XCTAssertEqual(try repository.actionableReminder(id, now: due).id, id)
        XCTAssertThrowsError(try repository.actionableReminder(id, now: start))
        // Read persisted SQLite bytes, without a refreshing repository read masking a rollback.
        XCTAssertEqual(try persistedReminder(url).state, .cancelled)
        XCTAssertThrowsError(try repository.setReminderState(id, state: .error, error: "late", now: start))
    }

    // Catch reschedule leaving a stale preview identifier eligible at explicit open.
    func testSQLiteRescheduleRejectsOldPreviewIdAndLeavesOneDurableReplacement() throws {
        let url = file(); defer { remove(url) }
        let now = try instant("2030-01-02T09:00:00Z")
        let c = client(); var a = visit(c)
        var oldID: UUID!
        do {
            let r = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 0)! })
            try r.saveClient(c, now: now); try r.saveAppointment(a, now: now)
            oldID = try XCTUnwrap(r.actionableReminder(try XCTUnwrap(r.reminders(now: now).first?.id), now: now).id)
            a.startMinute = 660; a.endMinute = 690; try r.saveAppointment(a, now: now)
            XCTAssertThrowsError(try r.actionableReminder(oldID, now: now))
            XCTAssertThrowsError(try r.markHandedToMessenger(oldID, now: now))
        }
        let rows = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 0)! }).reminders(now: now)
        XCTAssertEqual(rows.count, 1); XCTAssertNotEqual(rows.first?.id, oldID)
        XCTAssertEqual(rows.first?.dueAt, try instant("2030-01-02T10:00:00Z"))
        XCTAssertEqual(rows.first?.state, .scheduled)
    }

    // Catch disabling/deleting leaving the queued preview actionable, or a failed
    // preference validation mutating the durable client/outbox before rejection.
    func testSQLitePreferenceFailureIsAtomicAndDisableThenDeleteInvalidateActions() throws {
        let url = file(); defer { remove(url) }
        let now = try instant("2030-01-02T09:00:00Z")
        let r = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 0)! })
        var c = client(); let a = visit(c)
        try r.saveClient(c, now: now); try r.saveAppointment(a, now: now)
        let original = try XCTUnwrap(r.reminders(now: now).first)
        c.reminder.leadMinutes = 0
        XCTAssertThrowsError(try r.saveClient(c, now: now))
        XCTAssertEqual(try r.clients().first?.reminder.leadMinutes, 60)
        XCTAssertEqual(try r.reminders(now: now).first, original)
        c.reminder.enabled = false
        try r.saveClient(c, now: now)
        XCTAssertThrowsError(try r.actionableReminder(original.id, now: now))
        XCTAssertEqual(try persistedReminder(url).state, .cancelled)
        try r.deleteAppointment(a.id)
        XCTAssertThrowsError(try r.markHandedToMessenger(original.id, now: now))
        XCTAssertTrue(try r.appointments().isEmpty)
        XCTAssertEqual(try persistedReminder(url).state, .cancelled)
    }

    // Catch read refresh clearing persisted retry errors or state-only actions after rollback.
    func testSQLiteErrorSurvivesReopenBackwardsClockAndExplicitRetryHandoff() throws {
        let url = file(); defer { remove(url) }
        let due = try instant("2030-01-02T09:00:00Z"), before = due.addingTimeInterval(-1)
        let c = client(); let a = visit(c)
        var id: UUID!
        do {
            let r = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 0)! })
            try r.saveClient(c, now: before); try r.saveAppointment(a, now: before)
            id = try XCTUnwrap(r.reminders(now: due).first?.id)
            try r.setReminderState(id, state: .error, error: "Synthetic preparation failure", now: due)
        }
        let r = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 0)! })
        let back = try XCTUnwrap(r.reminders(now: before).first)
        XCTAssertEqual(back.id, id); XCTAssertEqual(back.state, .error); XCTAssertEqual(back.error, "Synthetic preparation failure")
        XCTAssertThrowsError(try r.actionableReminder(id, now: before))
        XCTAssertEqual(DevicePolicy.notificationWindow([back], now: before, enabled: true).map(\.id), [id!])
        XCTAssertEqual(try r.actionableReminder(id, now: due).state, .error)
        // Merely previewing/copying/cancelling does not call any state mutation.
        XCTAssertEqual(try r.reminders(now: due).first?.state, .error)
        try r.markHandedToMessenger(id, now: due)
        XCTAssertEqual(try persistedReminder(url).state, .handedToMessenger)
        XCTAssertNil(try persistedReminder(url).error)
        XCTAssertThrowsError(try r.actionableReminder(id, now: due))
    }

    // Catch timezone rebasing/unchanged writes silently reviving terminal outcomes.
    func testSQLiteTimezoneChangeBeforeFirstReadAndUnchangedSavesPreserveTerminalIdentity() throws {
        for terminal in [ReminderState.handedToMessenger, .cancelled] {
            let url = file(); defer { remove(url) }
            let now = try instant("2030-01-02T09:00:00Z"), c = client()
            let a = visit(c)
            var original: Reminder!
            do {
                let r = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 0)! })
                try r.saveClient(c, now: now); try r.saveAppointment(a, now: now)
                original = try XCTUnwrap(r.reminders(now: now).first)
                try r.setReminderState(original.id, state: terminal, now: now)
            }
            do {
                let r = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 7200)! })
                // UTC+2 starts at 08:00Z. Move clock back to 07:00Z to isolate
                // zone rebasing from appointment-expiry invalidation.
                let rebasedNow = try instant("2030-01-02T07:00:00Z")
                try r.saveAppointment(a, now: rebasedNow); try r.saveClient(c, now: rebasedNow)
                let actual = try XCTUnwrap(r.reminders(now: rebasedNow).first)
                XCTAssertEqual(actual.id, original.id); XCTAssertEqual(actual.state, terminal)
                XCTAssertEqual(actual.dueAt, try instant("2030-01-02T07:00:00Z"))
                XCTAssertThrowsError(try r.actionableReminder(actual.id, now: rebasedNow))
            }
            let reopened = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 7200)! })
            let reopenedNow = try instant("2030-01-02T07:00:00Z")
            XCTAssertEqual(try reopened.reminders(now: reopenedNow).first?.id, original.id)
            XCTAssertEqual(try reopened.reminders(now: reopenedNow).first?.state, terminal)
        }
    }

    // Catch truncating the persisted outbox to match the platform notification limit.
    func testSQLiteKeepsCompleteOutboxWhenOnlyEarliestWindowIsScheduled() throws {
        let url = file(); defer { remove(url) }
        let now = try instant("2030-01-01T00:00:00Z"), c = client()
        do {
            let r = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 0)! })
            try r.saveClient(c, now: now)
            for index in (1...70).reversed() {
                var a = visit(c); a.startMinute = 60 + index * 15; a.endMinute = a.startMinute + 10
                try r.saveAppointment(a, now: now)
            }
            let rows = try r.reminders(now: now)
            let window = DevicePolicy.notificationWindow(rows, now: now, enabled: true)
            XCTAssertEqual(window.count, 60)
            XCTAssertEqual(window.first?.dueAt, try instant("2030-01-02T00:15:00Z"))
            XCTAssertEqual(window.last?.dueAt, try instant("2030-01-02T15:00:00Z"))
            XCTAssertTrue(DevicePolicy.notificationWindow(rows, now: now, enabled: false).isEmpty)
        }
        let reopened = try SQLiteRepository(path: url.path, timeZone: { TimeZone(secondsFromGMT: 0)! })
        XCTAssertEqual(try reopened.reminders(now: now).count, 70)
        XCTAssertEqual(try reopened.appointments().count, 70)
    }

    private func persistedReminder(_ url: URL) throws -> Reminder {
        var db: OpaquePointer?
        guard sqlite3_open(url.path, &db) == SQLITE_OK else { throw ZapysError.database("QA open") }
        defer { sqlite3_close(db) }
        var statement: OpaquePointer?
        guard sqlite3_prepare_v2(db, "SELECT payload FROM reminders", -1, &statement, nil) == SQLITE_OK else { throw ZapysError.database("QA query") }
        defer { sqlite3_finalize(statement) }
        guard sqlite3_step(statement) == SQLITE_ROW, let bytes = sqlite3_column_blob(statement, 0) else { throw ZapysError.notFound }
        return try JSONDecoder().decode(Reminder.self, from: Data(bytes: bytes, count: Int(sqlite3_column_bytes(statement, 0))))
    }
}
