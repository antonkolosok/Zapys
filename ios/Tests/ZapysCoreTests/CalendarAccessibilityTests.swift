import XCTest
@testable import ZapysCore

final class CalendarAccessibilityTests: XCTestCase {
 func testAppointmentSemanticsDistinguishDatesAndIncludeAllRequiredContext() {
  var a = Appointment(day: "2026-11-03", startMinute: 540, endMinute: 570)
  a.clientNameSnapshot = "Олена"
  a.services = [ServiceSnapshot(service: Service(name: "Перша")), ServiceSnapshot(service: Service(name: "Друга"))]
  for status in AppointmentStatus.allCases {
   for draft in [false, true] {
    a.status = status; a.isDraft = draft
    let label = CalendarPresentation.appointmentLabel(a)
    for text in ["Олена", "2026-11-03", "09:00-09:30", CalendarPresentation.status(a), "Послуг: 2", "Перша", "Друга"] { XCTAssertTrue(label.contains(text)) }
    var other = a; other.day = "2026-11-04"
    XCTAssertNotEqual(CalendarPresentation.appointmentLabel(other), label)
   }
  }
  a.clientNameSnapshot = nil; a.services = []
  XCTAssertTrue(CalendarPresentation.appointmentLabel(a).contains("Без клієнта"))
  XCTAssertTrue(CalendarPresentation.appointmentLabel(a).contains("Послуг: 0"))
 }
 func testHeaderSelectionAndTodayAreIndependent() {
  for selected in [false, true] { for today in [false, true] {
   let label = CalendarPresentation.dayHeaderLabel("2026-11-03", selected: selected, today: today)
   XCTAssertTrue(label.contains("2026-11-03"))
   XCTAssertEqual(label.contains("Вибрано"), selected)
   XCTAssertEqual(label.contains("Сьогодні"), today)
  } }
 }
}
