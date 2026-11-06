import XCTest
@testable import ZapysCore
final class AppointmentDraftTests: XCTestCase {
 func testTimeValidationIdentifiesFirstVisibleFieldBeforeOtherErrors() throws {
  var d = AppointmentDraft(); d.inlineName = "Missing phone"; d.receivedText = "bad"
  d.dateProvided = false; d.startProvided = false
  XCTAssertEqual(d.timeValidationIssue?.field, .date)
  d.dateProvided = true; d.appointment.day = "invalid"
  XCTAssertEqual(d.timeValidationIssue?.field, .date)
  d.appointment.day = "2099-01-01"
  XCTAssertEqual(d.timeValidationIssue?.field, .start)
  d.startProvided = true; d.appointment.startMinute = 1439
  XCTAssertEqual(d.timeValidationIssue?.field, .start)
  d.appointment.startMinute = 600
  XCTAssertEqual(d.timeValidationIssue?.field, .end)
  d.endProvided = true; d.appointment.endMinute = 600
  XCTAssertEqual(d.timeValidationIssue?.field, .end)
  XCTAssertThrowsError(try d.prepared(isDraft: false)) { error in
   XCTAssertEqual(error as? ZapysError, .validation(d.timeValidationIssue!.message))
  }
  d.appointment.endMinute = 660
  XCTAssertNil(d.timeValidationIssue)
 }
 func testRequiredEndAndClientAndDraftDoesNotChangeStatus() throws {
  var d = AppointmentDraft(now: Date(timeIntervalSince1970: 0))
  XCTAssertThrowsError(try d.prepared(isDraft: true))
  d.endProvided = true; d.appointment.endMinute = 600
  d.appointment.status = .completed
  XCTAssertThrowsError(try d.prepared(isDraft: false))
  XCTAssertTrue(try d.prepared(isDraft: true).isDraft)
  XCTAssertEqual(d.appointment.status, .completed)
 }
 func testRawOverridesAndManualEndSurvive() throws {
  var d = AppointmentDraft(); try d.addService(Service(name: "A", durationMinutes: 30, priceMinor: 0))
  let id = d.appointment.services[0].id
  d.durationText[id] = "wrong"
  XCTAssertThrowsError(try d.prepared(isDraft: true))
  XCTAssertEqual(d.durationText[id], "wrong")
  d.durationText[id] = "45"; d.appointment.endManuallySet = true; d.appointment.endMinute = 700
  d.applySuggestion(); XCTAssertEqual(d.appointment.endMinute, 700)
  d.applySuggestion(force: true); XCTAssertEqual(d.appointment.endMinute, d.appointment.startMinute + 45)
 }
 func testInlineIdentityRotatesOnlyOnSuccess() throws {
  var d = AppointmentDraft(); d.inlineName = "One"; d.inlinePhone = "100"
  let first = try XCTUnwrap(d.prepareInlineClient())
  XCTAssertEqual(first.id, try d.prepareInlineClient()?.id)
  d.inlineClientSaved(first); d.inlineName = "Two"; d.inlinePhone = "100"
  XCTAssertNotEqual(first.id, try d.prepareInlineClient()?.id)
 }
 func testTotalExceedsInt64WithoutOverflow() throws {
  var d = AppointmentDraft(); try d.addService(Service(name: "A", priceMinor: Int64.max)); try d.addService(Service(name: "B", priceMinor: Int64.max))
  XCTAssertEqual(d.totalText, "184467440737095516.14 ₴")
 }
}


