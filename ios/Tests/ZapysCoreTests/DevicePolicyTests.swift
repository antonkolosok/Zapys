import XCTest
@testable import ZapysCore

final class DevicePolicyTests: XCTestCase {
 func testPositiveEnabledLead() throws {
  var client = Client(name: "Test", phone: "1")
  client.reminder.leadMinutes = 0
  XCTAssertNoThrow(try Rules.validate(client))
  client.reminder.enabled = true; client.reminder.recipient = "1"
  XCTAssertThrowsError(try Rules.validate(client))
 }
 func testClockAndBoundedWindow() {
  let now = Date(timeIntervalSince1970: 1000)
  let rows = (0..<80).map { Reminder(appointmentID: UUID(), dueAt: now.addingTimeInterval(Double($0 + 1)), channel: .viber, recipient: "1", message: "private", state: .error) }
  XCTAssertEqual(DevicePolicy.notificationWindow(rows, now: now, enabled: true).count, 60)
  XCTAssertEqual(DevicePolicy.notificationWindow(rows, now: now, enabled: false).count, 0)
  XCTAssertFalse(DevicePolicy.actionable(rows[0], now: now))
  XCTAssertTrue(DevicePolicy.actionable(rows[0], now: now.addingTimeInterval(2)))
 }
 func testShareOutcome() {
  XCTAssertEqual(DevicePolicy.shareOutcome(completed: false, activity: "net.whatsapp.WhatsApp.ShareExtension", failed: false), .retry)
  XCTAssertEqual(DevicePolicy.shareOutcome(completed: true, activity: "com.apple.UIKit.activity.CopyToPasteboard", failed: false), .retry)
  XCTAssertEqual(DevicePolicy.shareOutcome(completed: true, activity: "net.whatsapp.WhatsApp.ShareExtension", failed: false), .handoff)
  XCTAssertEqual(DevicePolicy.shareOutcome(completed: true, activity: "unknown", failed: true), .error)
 }
 func testRepositoryPreviewRechecksDueAndExpiryWithoutEditing() throws {
  let zone = TimeZone(secondsFromGMT: 0)!
  let repository = try SQLiteRepository(path: ":memory:", timeZone: { zone })
  var client = Client(name: "Test", phone: "1")
  client.reminder.enabled = true; client.reminder.recipient = "1"; client.reminder.leadMinutes = 60
  let early = Rules.date(day: "2030-01-02", minute: 539, timeZone: zone)!
  let due = early.addingTimeInterval(60), start = due.addingTimeInterval(3600)
  try repository.saveClient(client, now: early)
  var appointment = Appointment(day: "2030-01-02", startMinute: 600, endMinute: 630)
  appointment.clientID = client.id; appointment.isDraft = false
  try repository.saveAppointment(appointment, now: early)
  let id = try XCTUnwrap(repository.reminders(now: early).first?.id)
  XCTAssertThrowsError(try repository.actionableReminder(id, now: early))
  XCTAssertEqual(try repository.actionableReminder(id, now: due).id, id)
  try repository.setReminderState(id, state: .error, error: "Retry", now: due)
  XCTAssertThrowsError(try repository.markHandedToMessenger(id, now: early))
  XCTAssertEqual(try repository.actionableReminder(id, now: due).error, "Retry")
  XCTAssertThrowsError(try repository.actionableReminder(id, now: start))
  XCTAssertEqual(try repository.reminders(now: start).first?.state, .cancelled)
 }
}
