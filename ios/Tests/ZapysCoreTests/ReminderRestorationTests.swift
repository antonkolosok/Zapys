import XCTest
@testable import ZapysCore

/// Regression-first Task 11 review fixes. All execution UNRUN on Windows.
final class ReminderRestorationTests: XCTestCase {
 private let zone = TimeZone(secondsFromGMT: 0)!
 private var now: Date { Rules.date(day: "2030-01-02", minute: 570, timeZone: zone)! }
 private func fixture(path: String = ":memory:") throws -> (SQLiteRepository, Client, Appointment, UUID) {
  let repository = try SQLiteRepository(path: path, timeZone: { self.zone })
  var client = Client(name: "Synthetic", phone: "123")
  client.reminder.enabled = true; client.reminder.recipient = "synthetic"; client.reminder.leadMinutes = 60
  var appointment = Appointment(day: "2030-01-02", startMinute: 600, endMinute: 630)
  appointment.clientID = client.id; appointment.isDraft = false
  try repository.saveClient(client, now: now); try repository.saveAppointment(appointment, now: now)
  return (repository, client, appointment, try XCTUnwrap(repository.reminders(now: now).first?.id))
 }
 func testDisableThenReenableCreatesFreshCurrentReminderAndRejectsOldID() throws {
  let (repository, original, _, oldID) = try fixture()
  var client = original; client.reminder.enabled = false
  try repository.saveClient(client, now: now)
  XCTAssertEqual(try repository.reminders(now: now).first?.state, .cancelled)
  client.reminder.enabled = true; try repository.saveClient(client, now: now)
  let restored = try XCTUnwrap(repository.reminders(now: now).first)
  XCTAssertNotEqual(restored.id, oldID)
  XCTAssertEqual(try repository.actionableReminder(restored.id, now: now).state, .needsManualAction)
  XCTAssertThrowsError(try repository.actionableReminder(oldID, now: now))
  XCTAssertThrowsError(try repository.markHandedToMessenger(oldID, now: now))
 }
 func testCancelledAppointmentRestoreCreatesFreshReminder() throws {
  let (repository, _, original, oldID) = try fixture()
  var appointment = original; appointment.status = .cancelled
  try repository.saveAppointment(appointment, now: now)
  XCTAssertEqual(try repository.reminders(now: now).first?.state, .cancelled)
  appointment.status = .planned; try repository.saveAppointment(appointment, now: now)
  let restored = try XCTUnwrap(repository.reminders(now: now).first)
  XCTAssertNotEqual(restored.id, oldID)
  XCTAssertNoThrow(try repository.actionableReminder(restored.id, now: now))
  XCTAssertThrowsError(try repository.actionableReminder(oldID, now: now))
 }
 func testExplicitCancelSurvivesUnchangedWritesAndPreferenceCycle() throws {
  let (repository, original, appointment, oldID) = try fixture()
  try repository.setReminderState(oldID, state: .cancelled, now: now)
  try repository.saveAppointment(appointment, now: now); try repository.saveClient(original, now: now)
  var client = original; client.reminder.enabled = false
  try repository.saveClient(client, now: now)
  client.reminder.enabled = true; try repository.saveClient(client, now: now)
  let retained = try XCTUnwrap(repository.reminders(now: now).first)
  XCTAssertEqual(retained.id, oldID); XCTAssertEqual(retained.state, .cancelled)
  XCTAssertThrowsError(try repository.actionableReminder(oldID, now: now))
 }
 func testExpiryAndBackwardClockAloneDoNotRestoreCancelledRow() throws {
  let (repository, client, appointment, oldID) = try fixture()
  XCTAssertEqual(try repository.reminders(now: now.addingTimeInterval(3600)).first?.state, .cancelled)
  try repository.saveClient(client, now: now); try repository.saveAppointment(appointment, now: now)
  let retained = try XCTUnwrap(repository.reminders(now: now).first)
  XCTAssertEqual(retained.id, oldID); XCTAssertEqual(retained.state, .cancelled)
 }
 func testCancellationReasonSurvivesSQLiteReopenAndOldPayloadDecodes() throws {
  let path = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString).path
  defer { try? FileManager.default.removeItem(atPath: path) }
  var original: Client!; var oldID: UUID!
  do {
   let (repository, client, _, id) = try fixture(path: path)
   original = client; oldID = id
   var disabled = client; disabled.reminder.enabled = false
   try repository.saveClient(disabled, now: now)
  }
  let reopened = try SQLiteRepository(path: path, timeZone: { self.zone })
  let cancelled = try XCTUnwrap(reopened.reminders(now: now).first)
  XCTAssertEqual(cancelled.cancellationReason, .eligibilityLost)
  var json = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(cancelled)) as? [String: Any])
  json.removeValue(forKey: "cancellationReason")
  let legacy = try JSONDecoder().decode(Reminder.self, from: JSONSerialization.data(withJSONObject: json))
  XCTAssertNil(legacy.cancellationReason); XCTAssertEqual(legacy.state, .cancelled)
  try reopened.saveClient(original, now: now)
  XCTAssertNotEqual(try reopened.reminders(now: now).first?.id, oldID)
 }
}
