import XCTest
import CSQLite
@testable import ZapysCore

final class RepositoryTests: XCTestCase {
    func testRejectedExpiredHandoffCommitsInvalidationVisibleToSecondConnection() throws {
        let path = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).path
        defer { try? FileManager.default.removeItem(atPath: path) }
        let repository = try SQLiteRepository(path: path)
        var client = Client(name: "Client", phone: "123")
        client.reminder.enabled = true; client.reminder.recipient = "123"
        try repository.saveClient(client)
        var appointment = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 630)
        appointment.clientID = client.id; appointment.isDraft = false
        let before = try XCTUnwrap(Rules.date(day: appointment.day, minute: 570))
        let start = try XCTUnwrap(Rules.date(day: appointment.day, minute: 600))
        try repository.saveAppointment(appointment, now: before)
        let id = try XCTUnwrap(repository.reminders(now: before).first?.id)
        XCTAssertThrowsError(try repository.markHandedToMessenger(id, now: start))

        // Read raw persisted bytes from another connection, without a refreshing
        // repository read that could hide an incorrectly rolled-back expiry.
        var db: OpaquePointer?
        XCTAssertEqual(sqlite3_open(path, &db), SQLITE_OK)
        defer { sqlite3_close(db) }
        var statement: OpaquePointer?
        XCTAssertEqual(sqlite3_prepare_v2(db, "SELECT payload FROM reminders", -1, &statement, nil), SQLITE_OK)
        defer { sqlite3_finalize(statement) }
        XCTAssertEqual(sqlite3_step(statement), SQLITE_ROW)
        let bytes = try XCTUnwrap(sqlite3_column_blob(statement, 0))
        let data = Data(bytes: bytes, count: Int(sqlite3_column_bytes(statement, 0)))
        XCTAssertEqual(try JSONDecoder().decode(Reminder.self, from: data).state, .cancelled)
    }

    func testSecondConnectionWriterLockPreventsPartialHandoffThenCancellationWins() throws {
        let path = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).path
        defer { try? FileManager.default.removeItem(atPath: path) }
        let first = try SQLiteRepository(path: path)
        let second = try SQLiteRepository(path: path)
        var client = Client(name: "Client", phone: "123")
        client.reminder.enabled = true; client.reminder.recipient = "123"
        try first.saveClient(client)
        var appointment = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 630)
        appointment.clientID = client.id; appointment.isDraft = false
        let before = try XCTUnwrap(Rules.date(day: appointment.day, minute: 570))
        try first.saveAppointment(appointment, now: before)
        let id = try XCTUnwrap(first.reminders(now: before).first?.id)
        var writer: OpaquePointer?
        XCTAssertEqual(sqlite3_open(path, &writer), SQLITE_OK)
        defer { sqlite3_close(writer) }
        XCTAssertEqual(sqlite3_exec(writer, "BEGIN IMMEDIATE", nil, nil, nil), SQLITE_OK)
        XCTAssertThrowsError(try first.markHandedToMessenger(id, now: before))
        XCTAssertEqual(sqlite3_exec(writer, "COMMIT", nil, nil, nil), SQLITE_OK)
        XCTAssertEqual(try second.reminders(now: before).first?.state, .needsManualAction)
        appointment.status = .cancelled
        try second.saveAppointment(appointment, now: before)
        XCTAssertThrowsError(try first.markHandedToMessenger(id, now: before))
        XCTAssertEqual(try second.reminders(now: before).first?.state, .cancelled)
        // No sleeps or production hooks. This verifies real lock contention and
        // cross-instance visibility, not the former sub-transaction race schedule.
    }
    func testChangingAppointmentClientRefreshesClientSnapshot() throws {
        let r = try SQLiteRepository(path: ":memory:")
        let first = Client(name: "First", phone: "1")
        let second = Client(name: "Second", phone: "2")
        try r.saveClient(first); try r.saveClient(second)
        var a = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 630)
        a.clientID = first.id; a.isDraft = false
        try r.saveAppointment(a)
        a = try XCTUnwrap(r.appointments().first)
        a.clientID = second.id
        try r.saveAppointment(a)
        XCTAssertEqual(try r.appointments().first?.clientNameSnapshot, "Second")
    }
    func testMigrationSetsVersionAndRejectsFutureSchema() throws {
        let path = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).path
        defer { try? FileManager.default.removeItem(atPath: path) }
        do { _ = try SQLiteRepository(path: path) }
        var db: OpaquePointer?
        XCTAssertEqual(sqlite3_open(path, &db), SQLITE_OK)
        defer { sqlite3_close(db) }
        var statement: OpaquePointer?
        XCTAssertEqual(sqlite3_prepare_v2(db, "PRAGMA user_version", -1, &statement, nil), SQLITE_OK)
        XCTAssertEqual(sqlite3_step(statement), SQLITE_ROW)
        XCTAssertEqual(sqlite3_column_int(statement, 0), 1)
        sqlite3_finalize(statement)
        XCTAssertEqual(sqlite3_exec(db, "PRAGMA user_version = 2", nil, nil, nil), SQLITE_OK)
        XCTAssertThrowsError(try SQLiteRepository(path: path))
    }
    func testSQLiteFailureAfterAppointmentWriteRollsBackEntireTransaction() throws {
        let path = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).path
        defer { try? FileManager.default.removeItem(atPath: path) }
        let r = try SQLiteRepository(path: path)
        var c = Client(name: "Client", phone: "123")
        c.reminder.enabled = true; c.reminder.recipient = "123"
        try r.saveClient(c)
        var a = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 630)
        a.clientID = c.id; a.isDraft = false
        try r.saveAppointment(a)
        let original = try r.appointments()
        let outbox = try r.reminders()
        var db: OpaquePointer?
        XCTAssertEqual(sqlite3_open(path, &db), SQLITE_OK)
        defer { sqlite3_close(db) }
        XCTAssertEqual(sqlite3_exec(db, "CREATE TRIGGER reject_outbox BEFORE UPDATE ON reminders BEGIN SELECT RAISE(ABORT, 'test failure'); END", nil, nil, nil), SQLITE_OK)
        a.startMinute = 700; a.endMinute = 730
        XCTAssertThrowsError(try r.saveAppointment(a))
        XCTAssertEqual(try r.appointments(), original)
        XCTAssertEqual(sqlite3_exec(db, "DROP TRIGGER reject_outbox", nil, nil, nil), SQLITE_OK)
        XCTAssertEqual(try r.reminders(), outbox)
    }

    func testRescheduleInvalidatesOldManualActionIdentifier() throws {
        let r = try SQLiteRepository(path: ":memory:")
        var c = Client(name: "Client", phone: "123")
        c.reminder.enabled = true; c.reminder.recipient = "123"
        try r.saveClient(c)
        var a = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 630)
        a.clientID = c.id; a.isDraft = false
        try r.saveAppointment(a)
        let oldID = try XCTUnwrap(r.reminders().first?.id)
        a.startMinute = 700; a.endMinute = 730
        try r.saveAppointment(a)
        XCTAssertThrowsError(try r.markHandedToMessenger(oldID))
        XCTAssertNotEqual(try r.reminders().first?.id, oldID)
    }
    func testPaymentEditAndExpiryPreserveHandoffButRejectFurtherAction() throws {
        let r = try SQLiteRepository(path: ":memory:")
        var c = Client(name: "Client", phone: "123")
        c.reminder.enabled = true; c.reminder.recipient = "123"
        try r.saveClient(c)
        var a = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 660)
        a.clientID = c.id; a.isDraft = false
        let before = try XCTUnwrap(Rules.date(day: a.day, minute: 570))
        try r.saveAppointment(a, now: before)
        let id = try XCTUnwrap(r.reminders(now: before).first?.id)
        try r.markHandedToMessenger(id, now: before)
        a.paid = true; try r.saveAppointment(a, now: before)
        XCTAssertEqual(try r.reminders(now: before).first?.state, .handedToMessenger)
        let expired = try XCTUnwrap(Rules.date(day: a.day, minute: 601))
        XCTAssertEqual(try r.reminders(now: expired).first?.state, .handedToMessenger)
        XCTAssertThrowsError(try r.markHandedToMessenger(id, now: expired))
    }

    func testMissingClientRejectsUpdateWithoutReplacingPersistedDraft() throws {
        let r = try SQLiteRepository(path: ":memory:")
        let a = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 630)
        try r.saveAppointment(a)
        var invalid = a; invalid.clientID = UUID(); invalid.isDraft = false
        XCTAssertThrowsError(try r.saveAppointment(invalid))
        XCTAssertEqual(try r.appointments(), [a])
    }
    func testReopenPreservesOrderedSnapshotsAndNullMoney() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        let c = Client(name: "Олена", phone: "+380671234567")
        var a = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 660)
        do {
            let r = try SQLiteRepository(path: url.path)
            try r.saveClient(c)
            let s = Service(name: "Догляд", durationMinutes: 30, priceMinor: nil)
            try r.saveService(s)
            a.clientID = c.id; a.isDraft = false
            a.services = [ServiceSnapshot(service: s), ServiceSnapshot(service: Service(name: "Безкоштовно", priceMinor: 0))]
            try r.saveAppointment(a)
            var edited = s; edited.name = "Нова назва"; try r.saveService(edited)
        }
        let reopened = try SQLiteRepository(path: url.path)
        XCTAssertEqual(try reopened.appointments().first?.services.map(\.name), ["Догляд", "Безкоштовно"])
        XCTAssertNil(try reopened.appointments().first?.services.first?.priceMinor)
        XCTAssertEqual(try reopened.appointments().first?.services.last?.priceMinor, 0)
    }
    func testConflictRejectsAtomicallyAndRescheduleReplacesReminder() throws {
        let r = try SQLiteRepository(path: ":memory:")
        var c = Client(name: "Ірина", phone: "123")
        c.reminder.enabled = true; c.reminder.recipient = "123"
        try r.saveClient(c)
        var a = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 660)
        a.clientID = c.id; a.isDraft = false
        try r.saveAppointment(a)
        let before = try XCTUnwrap(r.reminders().first)
        var b = a; b.id = UUID()
        XCTAssertThrowsError(try r.saveAppointment(b))
        XCTAssertEqual(try r.appointments().count, 1)
        XCTAssertEqual(try r.reminders().count, 1)
        a.startMinute = 700; a.endMinute = 760; try r.saveAppointment(a)
        let after = try XCTUnwrap(r.reminders().first)
        XCTAssertNotEqual(before.dueAt, after.dueAt)
        XCTAssertTrue(after.message.contains("11:40"))
        a.status = .cancelled; try r.saveAppointment(a)
        XCTAssertEqual(try r.reminders().first?.state, .cancelled)
    }
}
