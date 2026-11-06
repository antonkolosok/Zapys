import XCTest
@testable import ZapysCore

final class CalendarGeometryTests: XCTestCase {
 func testCompactWeekClientAnchorMatchesLowerHalfWithoutMovingHiddenServices() {
  var a = Appointment(day: "2026-11-03", startMinute: 540, endMinute: 570)
  a.clientColorSnapshot = "#112233"
  var second = Service(name: "Second"); second.colorHex = "#445566"
  a.services = [ServiceSnapshot(service: Service(name: "Unset")), ServiceSnapshot(service: second)]
  let compact = CalendarPresentation.rows(a, height: 36, rowHeight: 22, week: true)
  let regular = CalendarPresentation.rows(a, height: 36, rowHeight: 22)
  XCTAssertEqual(compact.labelCenters(rowHeight: 22), [18.5, 35, 57])
  XCTAssertEqual(regular.labelCenters(rowHeight: 22), [13, 35, 57])
  XCTAssertEqual(compact.labelCenters(rowHeight: 44), [35, 68, 112])
  let stops = CalendarGeometry.gradient(colors: [a.clientColorSnapshot] + a.services.map(\.colorHex), rowCenters: compact.labelCenters(rowHeight: 22))
  XCTAssertEqual(stops.map(\.center), [18.5, 57])
  XCTAssertGreaterThan(stops[1].center, 36, "Clipped colored service must stay below the visible event")
  let ampleWeek = CalendarPresentation.rows(a, height: 200, rowHeight: 22, week: true)
  XCTAssertNil(ampleWeek.compactTime)
  XCTAssertEqual(ampleWeek.labelCenters(rowHeight: 22), [13, 35, 57])
 }
 func testCompactWeekCarriesTimeWithinUnchangedLogicalRows() {
  var a = Appointment(day: "2026-11-03", startMinute: 540, endMinute: 570)
  a.clientNameSnapshot = "Ірина"
  a.services = [ServiceSnapshot(service: Service(name: "Перша")), ServiceSnapshot(service: Service(name: "Друга"))]
  let day = CalendarPresentation.rows(a, height: 36, rowHeight: 22)
  let week = CalendarPresentation.rows(a, height: 36, rowHeight: 22, week: true)
  XCTAssertEqual(week.compactTime, "09:00")
  XCTAssertTrue(week.labels[0].contains("Черн.")); XCTAssertTrue(week.labels[0].contains("Ірина"))
  XCTAssertEqual(week.labels.count, day.labels.count)
  XCTAssertEqual(Array(week.labels.dropFirst()), Array(day.labels.dropFirst()))
  XCTAssertNil(day.compactTime); XCTAssertNil(week.metadata)
  XCTAssertEqual(CalendarGeometry.height(start: a.startMinute, end: a.endMinute), 36)
  for (status, short) in [(AppointmentStatus.planned, "План"), (.completed, "Вик."), (.cancelled, "Скас."), (.noShow, "Неяв.")] {
   a.isDraft = false; a.status = status
   let rows = CalendarPresentation.rows(a, height: 36, rowHeight: 22, week: true)
   XCTAssertEqual(rows.compactTime, "09:00"); XCTAssertTrue(rows.labels[0].hasSuffix("Ірина"))
   XCTAssertEqual(rows.labels[0], short + " · Ірина")
  }
 }
 func testMonthBoundaryAndProportionalGeometry() {
  XCTAssertEqual(CalendarGeometry.weekDays("2026-11-01"), ["2026-10-26", "2026-10-27", "2026-10-28", "2026-10-29", "2026-10-30", "2026-10-31", "2026-11-01"])
  XCTAssertEqual(CalendarGeometry.navigate("2026-12-28", weeks: 1), "2027-01-04")
  XCTAssertEqual(CalendarGeometry.top(540), 648, accuracy: 0.001)
  XCTAssertEqual(CalendarGeometry.height(start: 540, end: 570), 36, accuracy: 0.001)
 }
 func testDraftAndResizeClampWithoutMutatingOriginal() {
  let draft = CalendarGeometry.draft(day: "2026-11-03", minute: 1439)
  XCTAssertEqual(draft.startMinute, 1409); XCTAssertEqual(draft.endMinute, 1439)
  XCTAssertTrue(draft.isDraft); XCTAssertNil(draft.clientID)
  let resized = CalendarGeometry.resize(draft, minute: 0)
  XCTAssertEqual(resized.endMinute, 1410); XCTAssertTrue(resized.endManuallySet)
  XCTAssertEqual(draft.endMinute, 1439)
 }
 func testConnectedOverlapGroupAndAdjacency() {
  let a = Appointment(day: "2026-11-03", startMinute: 540, endMinute: 600)
  let b = Appointment(day: a.day, startMinute: 570, endMinute: 630)
  let c = Appointment(day: a.day, startMinute: 600, endMinute: 660)
  let d = Appointment(day: a.day, startMinute: 660, endMinute: 690)
  let lanes = CalendarGeometry.lanes([d,c,b,a])
  XCTAssertEqual(lanes.map(\.lane), [0,1,0,0])
  XCTAssertEqual(lanes.map(\.laneCount), [2,2,2,1])
 }
 func testUnsetAndClippedColorsKeepLogicalAnchors() {
  let stops = CalendarGeometry.gradient(colors: ["#112233", nil, "#445566"], rowCenters: [14,38,62])
  XCTAssertEqual(stops.map(\.center), [14,62])
  XCTAssertEqual(stops.map(\.colorHex), ["#112233", "#445566"])
  XCTAssertTrue(CalendarGeometry.gradient(colors: [nil], rowCenters: [14]).isEmpty)
  XCTAssertEqual(CalendarGeometry.gradient(colors: ["#112233"], rowCenters: [14]).count, 1)
 }
 func testCompactStatusDoesNotAddOrMoveServiceRows() {
  var a = Appointment(day: "2026-11-03", startMinute: 540, endMinute: 541)
  a.services = [ServiceSnapshot(service: Service(name: "Перша")), ServiceSnapshot(service: Service(name: "Друга"))]
  let compact = CalendarPresentation.rows(a, height: 1.2, rowHeight: 24)
  XCTAssertEqual(compact.labels.count, 3); XCTAssertTrue(compact.labels[0].contains("Чернетка"))
  XCTAssertEqual(Array(compact.labels.dropFirst()), ["Перша", "Друга"])
  XCTAssertNil(compact.metadata)
 }
}
