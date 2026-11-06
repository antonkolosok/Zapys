import XCTest
import CSQLite
@testable import ZapysCore

/// Authored only on Windows: real SQLite execution still requires Swift/XCTest.
final class FinalRepositoryRegressionTests: XCTestCase {
 func testReferencedClientDeletionIsAtomicAcrossReopenAndPaymentEdit() throws {
  let path = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).path
  defer { try? FileManager.default.removeItem(atPath: path) }
  var c = Client(name: "Client", phone: "123")
  c.reminder.enabled = true; c.reminder.recipient = "123"
  var a = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 660)
  a.clientID = c.id; a.isDraft = false
  let now = try XCTUnwrap(Rules.date(day: a.day, minute: 570))
  let saved: [Appointment], outbox: [Reminder]
  do {
   let r = try SQLiteRepository(path: path)
   try r.saveClient(c, now: now); try r.saveAppointment(a, now: now)
   saved = try r.appointments(); outbox = try r.reminders(now: now)
   XCTAssertThrowsError(try r.deleteClient(c.id)) { error in
    guard let typed = error as? ZapysError, case .validation(let message) = typed else { return XCTFail("Expected validation: \(error)") }
    XCTAssertTrue(message.contains("Неможливо видалити клієнта"))
   }
  }
  let reopened = try SQLiteRepository(path: path)
  XCTAssertEqual(try reopened.clients(), [c])
  XCTAssertEqual(try reopened.appointments(), saved)
  XCTAssertEqual(try reopened.reminders(now: now), outbox)
  a = try XCTUnwrap(saved.first); a.paid = true; a.receivedMinor = 100
  try reopened.saveAppointment(a, now: now)
  XCTAssertEqual(try reopened.appointments(), [a])
  XCTAssertEqual(try reopened.reminders(now: now), outbox)
  let unreferenced = Client(name: "Other", phone: "456")
  try reopened.saveClient(unreferenced, now: now); try reopened.deleteClient(unreferenced.id)
  XCTAssertEqual(try reopened.clients(), [c])
 }

 func testReconciliationDecodesEachRowOnceAtTechnicalCeilingAndSkipsUnchangedWrites() throws {
  for count in [100, Limits.appointments] {
   let path = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).path
   defer { try? FileManager.default.removeItem(atPath: path) }
   var zone = TimeZone(secondsFromGMT: 0)!
   let r = try SQLiteRepository(path: path, timeZone: { zone })
   var c = Client(name: "Client", phone: "123")
   c.reminder.enabled = true; c.reminder.recipient = "123"
   let now = try XCTUnwrap(Rules.date(day: "2099-11-02", minute: 480, timeZone: zone))
   try r.saveClient(c, now: now)
   var db: OpaquePointer?
   XCTAssertEqual(sqlite3_open(path, &db), SQLITE_OK)
   defer { sqlite3_close(db) }
   XCTAssertEqual(sqlite3_exec(db, "BEGIN IMMEDIATE", nil, nil, nil), SQLITE_OK)
   var firstID: UUID?
   for _ in 0..<count {
    var a = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 660)
    a.clientID = c.id; a.isDraft = false
    let row = try XCTUnwrap(Rules.makeReminder(appointment: a, client: c, now: now, timeZone: zone))
    if firstID == nil { firstID = row.id }
    try insert(a, id: a.id, table: "appointments", db: db)
    try insert(row, id: a.id, table: "reminders", db: db)
   }
   XCTAssertEqual(sqlite3_exec(db, "COMMIT", nil, nil, nil), SQLITE_OK)
   // A no-op refresh must never UPDATE even one eligible reminder.
   XCTAssertEqual(sqlite3_exec(db, "CREATE TRIGGER reject_unchanged BEFORE UPDATE ON reminders BEGIN SELECT RAISE(ABORT, 'unexpected write'); END", nil, nil, nil), SQLITE_OK)
   var decoded: [String: Int] = [:]
   r.didDecodeRecord = { decoded[$0, default: 0] += 1 }
   func assertSinglePass() {
    XCTAssertEqual(decoded["appointments"], count)
    XCTAssertEqual(decoded["reminders"], count)
    XCTAssertEqual(decoded["clients"], 1)
   }
   XCTAssertEqual(try r.reminders(now: now).count, count); assertSinglePass()
   decoded = [:]
   _ = try r.actionableReminder(try XCTUnwrap(firstID), now: now); assertSinglePass()
   decoded = [:]
   try r.saveClient(c, now: now); assertSinglePass()
   XCTAssertEqual(sqlite3_exec(db, "DROP TRIGGER reject_unchanged", nil, nil, nil), SQLITE_OK)
   decoded = [:]
   try r.setReminderState(try XCTUnwrap(firstID), state: .cancelled, now: now); assertSinglePass()
   decoded = [:]; zone = TimeZone(secondsFromGMT: 3600)!
   let rebased = try r.reminders(now: now); assertSinglePass()
   XCTAssertEqual(rebased.first?.id, firstID)
   XCTAssertEqual(rebased.first?.state, .cancelled)
   XCTAssertEqual(rebased.first?.cancellationReason, .user)
   XCTAssertEqual(sqlite3_exec(db, "CREATE TRIGGER reject_unchanged BEFORE UPDATE ON reminders BEGIN SELECT RAISE(ABORT, 'unexpected write'); END", nil, nil, nil), SQLITE_OK)
   decoded = [:]
   XCTAssertEqual(try r.reminders(now: now), rebased); assertSinglePass()
  }
 }
 private func insert<T: Encodable>(_ value: T, id: UUID, table: String, db: OpaquePointer?) throws {
  var s: OpaquePointer?
  XCTAssertEqual(sqlite3_prepare_v2(db, "INSERT INTO \(table)(id,payload) VALUES(?,?)", -1, &s, nil), SQLITE_OK)
  defer { sqlite3_finalize(s) }
  let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)
  XCTAssertEqual(sqlite3_bind_text(s, 1, id.uuidString, -1, transient), SQLITE_OK)
  let data = try JSONEncoder().encode(value)
  XCTAssertEqual(data.withUnsafeBytes { sqlite3_bind_blob(s, 2, $0.baseAddress, Int32($0.count), transient) }, SQLITE_OK)
  XCTAssertEqual(sqlite3_step(s), SQLITE_DONE)
 }
}
