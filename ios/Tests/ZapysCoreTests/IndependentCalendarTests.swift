import XCTest
@testable import ZapysCore

/// Independent boundary oracles adapted from the reviewed Android calendar contract.
/// Authored on Windows: these tests require actual execution with Swift/XCTest on macOS.
final class IndependentCalendarTests: XCTestCase {
    private let day = "2026-12-31"
    private func event(_ start: Int, _ end: Int, day: String = "2026-12-31") -> Appointment {
        Appointment(day: day, startMinute: start, endMinute: end)
    }

    func testMondayFirstWeekCrossesYearAndRetainsEveryHeaderDate() {
        let expected = ["2026-12-28", "2026-12-29", "2026-12-30", "2026-12-31", "2027-01-01", "2027-01-02", "2027-01-03"]
        XCTAssertEqual(CalendarGeometry.weekDays(day), expected)
        for date in expected { XCTAssertEqual(CalendarGeometry.weekDays(date), expected) }
        XCTAssertEqual(CalendarGeometry.navigate(day, weeks: 1), "2027-01-07")
        XCTAssertEqual(CalendarGeometry.navigate(CalendarGeometry.navigate(day, weeks: -1), weeks: 1), day)
        XCTAssertEqual(CalendarGeometry.navigate("2024-02-26", weeks: 1), "2024-03-04")
        XCTAssertEqual(CalendarGeometry.weekDays("2024-02-29"), ["2024-02-26", "2024-02-27", "2024-02-28", "2024-02-29", "2024-03-01", "2024-03-02", "2024-03-03"])
    }

    func testTappedDraftSnapsQuarterHourAndClampsLastHalfHourOnSameDate() throws {
        for (tap, start) in [(-10.0, 0), (601.0, 600), (609.0, 615), (1439.0, 1409), (2000.0, 1409)] {
            let id = UUID()
            let draft = CalendarGeometry.draft(day: day, minute: tap, id: id)
            XCTAssertEqual(draft.id, id); XCTAssertEqual(draft.day, day)
            XCTAssertEqual(draft.startMinute, start); XCTAssertEqual(draft.endMinute, start + 30)
            XCTAssertTrue(draft.isDraft); XCTAssertEqual(draft.status, .planned)
            XCTAssertNil(draft.clientID); XCTAssertTrue(draft.services.isEmpty)
            try Rules.validate(draft)
        }
    }

    func testChainedOverlapsUsePeakConcurrencyAndReuseFreedLane() throws {
        let a = event(600, 630), b = event(620, 650), c = event(640, 670)
        let rows = CalendarGeometry.lanes([c, a, b])
        XCTAssertEqual(Set(rows.map { $0.appointment.id }), Set([a.id, b.id, c.id]))
        XCTAssertTrue(rows.allSatisfy { $0.laneCount == 2 })
        let first = try XCTUnwrap(rows.first { $0.appointment.id == a.id })
        let middle = try XCTUnwrap(rows.first { $0.appointment.id == b.id })
        let last = try XCTUnwrap(rows.first { $0.appointment.id == c.id })
        XCTAssertEqual(first.lane, last.lane); XCTAssertNotEqual(first.lane, middle.lane)
    }

    func testAdjacentOtherDateAndCancelledRecordsDoNotConsumeActiveLanes() {
        let a = event(600, 630), adjacent = event(630, 660), tomorrow = event(600, 630, day: "2027-01-01")
        var cancelled = event(590, 680); cancelled.status = .cancelled
        let rows = CalendarGeometry.lanes([cancelled, tomorrow, adjacent, a])
        XCTAssertEqual(Set(rows.map { $0.appointment.id }), Set([a.id, adjacent.id, tomorrow.id]))
        XCTAssertTrue(rows.allSatisfy { $0.lane == 0 && $0.laneCount == 1 })
        XCTAssertFalse([a, adjacent, cancelled, tomorrow].contains { Rules.overlaps(a, $0) })
    }

    func testTripleConcurrencyUsesThreeLanesAndAdjacentFreshGroupResets() throws {
        let a = event(600, 660), b = event(615, 645), c = event(630, 675), d = event(675, 690)
        let rows = CalendarGeometry.lanes([d, c, b, a])
        let overlapping = rows.filter { $0.appointment.id != d.id }
        XCTAssertEqual(Set(overlapping.map(\.lane)).count, 3)
        XCTAssertTrue(overlapping.allSatisfy { $0.laneCount == 3 })
        let fresh = try XCTUnwrap(rows.first { $0.appointment.id == d.id })
        XCTAssertEqual(fresh.lane, 0); XCTAssertEqual(fresh.laneCount, 1)
    }

    func testExactHeightAndFullDayScaleNeverInflateShortEvents() {
        XCTAssertEqual(CalendarGeometry.scale, 1.2, accuracy: 0.0001)
        XCTAssertEqual(CalendarGeometry.gutter, 42, accuracy: 0.0001)
        XCTAssertEqual(CalendarGeometry.height(start: 600, end: 660), 72, accuracy: 0.0001)
        XCTAssertEqual(CalendarGeometry.height(start: 600, end: 630), 36, accuracy: 0.0001)
        XCTAssertEqual(CalendarGeometry.height(start: 1438, end: 1439), 1.2, accuracy: 0.0001)
        XCTAssertEqual(CalendarGeometry.top(0), 0, accuracy: 0.0001)
        XCTAssertEqual(CalendarGeometry.top(540), 648, accuracy: 0.0001)
        XCTAssertEqual(CalendarGeometry.top(1439), 1726.8, accuracy: 0.0001)
    }

    func testResizeReturnsIndependentPreviewWithSnapBoundsAndPreservedFields() {
        var original = event(607, 667); original.note = "retain"; original.paid = true
        original.services = [ServiceSnapshot(service: Service(name: "Retain service"))]
        let unchanged = original
        let preview = CalendarGeometry.resize(original, minute: 683)
        var expected = original; expected.endMinute = 690; expected.endManuallySet = true
        XCTAssertEqual(preview, expected)
        XCTAssertEqual(CalendarGeometry.resize(original, minute: 0).endMinute, 608)
        XCTAssertEqual(CalendarGeometry.resize(original, minute: 2000).endMinute, 1439)
        XCTAssertEqual(CalendarGeometry.resize(event(1438, 1439), minute: 1430).endMinute, 1439)
        // Discarding any preview is cancellation: the original remains a separate value.
        XCTAssertEqual(original, unchanged)
    }

    func testUnsetMiddleColorKeepsClippedServiceAnchorInDayAndWeekRows() {
        let fixtures: [[Double]] = [[16, 36, 56], [12, 30, 48]]
        for centers in fixtures {
            let stops = CalendarGeometry.gradient(colors: ["#112233", nil, "#445566"], rowCenters: centers)
            XCTAssertEqual(stops.map(\.colorHex), ["#112233", "#445566"])
            XCTAssertEqual(stops.map(\.center), [centers[0], centers[2]])
            XCTAssertGreaterThan(stops.last!.center, CalendarGeometry.height(start: 600, end: 630))
        }
    }

    func testZeroOneAndSixGradientContributionsKeepSourceOrder() {
        XCTAssertTrue(CalendarGeometry.gradient(colors: [nil, nil], rowCenters: [10, 30]).isEmpty)
        let single = CalendarGeometry.gradient(colors: [nil, "#123456", nil], rowCenters: [10, 30, 50])
        XCTAssertEqual(single.map(\.colorHex), ["#123456"]); XCTAssertEqual(single.map(\.center), [30])
        let colors = ["#555555", "#444444", "#333333", "#222222", "#111111", "#000000"]
        let centers: [Double] = [10, 30, 50, 70, 90, 110]
        let stops = CalendarGeometry.gradient(colors: colors.map { Optional($0) }, rowCenters: centers)
        XCTAssertEqual(stops.map(\.colorHex), colors); XCTAssertEqual(stops.map(\.center), centers)
    }

    private func coloredEvent() -> Appointment {
        var appointment = event(600, 900)
        appointment.clientNameSnapshot = "Client"; appointment.clientColorSnapshot = "#008800"
        var service = Service(name: "Service"); service.colorHex = "#AA0000"
        appointment.services = [ServiceSnapshot(service: service)]
        return appointment
    }

    func testTallColoredRecordsKeepOrderedLabelsAndTextualDraftOrStatus() {
        for status in AppointmentStatus.allCases {
            for draft in [false, true] {
                var appointment = coloredEvent(); appointment.status = status; appointment.isDraft = draft
                let rows = CalendarPresentation.rows(appointment, height: 360, rowHeight: 24)
                XCTAssertEqual(rows.labels, ["Client", "Service"])
                XCTAssertEqual(rows.metadata, "\(CalendarPresentation.status(appointment)) · 10:00–15:00")
                XCTAssertFalse(CalendarPresentation.status(appointment).isEmpty)
                let expectedStatus: String
                switch status {
                case .planned: expectedStatus = "Заплановано"
                case .completed: expectedStatus = "Виконано"
                case .cancelled: expectedStatus = "Скасовано"
                case .noShow: expectedStatus = "Не з’явився"
                }
                XCTAssertEqual(CalendarPresentation.status(appointment), draft ? "Чернетка · " + expectedStatus : expectedStatus)
            }
        }
    }

    func testCompactStatusDoesNotInsertRowsOrMoveServiceGradientCenters() {
        let appointment = coloredEvent()
        let rows = CalendarPresentation.rows(appointment, height: 12, rowHeight: 24)
        XCTAssertEqual(rows.labels, ["\(CalendarPresentation.status(appointment)) · Client", "Service"])
        XCTAssertNil(rows.metadata)
        let stops = CalendarGeometry.gradient(colors: [appointment.clientColorSnapshot, appointment.services[0].colorHex], rowCenters: rows.labels.indices.map { 4 + 24 * (Double($0) + 0.5) })
        XCTAssertEqual(stops.map(\.center), [16, 40])
        XCTAssertEqual(stops.map(\.colorHex), ["#008800", "#AA0000"])
        XCTAssertNil(CalendarPresentation.rows(appointment, height: 75, rowHeight: 24).metadata)
        XCTAssertNotNil(CalendarPresentation.rows(appointment, height: 76, rowHeight: 24).metadata)
    }

    func testDeclinedCalendarDraftConflictDoesNotInsertOrphanAfterReopen() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        let blocker = event(600, 660)
        let draft = CalendarGeometry.draft(day: day, minute: 609)
        do {
            let repository = try SQLiteRepository(path: url.path)
            try repository.saveAppointment(blocker)
            XCTAssertThrowsError(try repository.saveAppointment(draft)) { error in
                XCTAssertEqual(error as? ZapysError, .overlap([blocker.id]))
            }
            XCTAssertEqual(try repository.appointments(), [blocker])
        }
        XCTAssertEqual(try SQLiteRepository(path: url.path).appointments(), [blocker])
    }

    func testDeclinedResizeConflictKeepsOriginalIntervalAndMetadataAfterReopen() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        var original = event(600, 660); original.note = "original"; original.paid = true
        let blocker = event(675, 720)
        let preview = CalendarGeometry.resize(original, minute: 691)
        XCTAssertEqual(preview.endMinute, 690); XCTAssertTrue(preview.endManuallySet)
        do {
            let repository = try SQLiteRepository(path: url.path)
            try repository.saveAppointment(original); try repository.saveAppointment(blocker)
            XCTAssertThrowsError(try repository.saveAppointment(preview)) { error in
                XCTAssertEqual(error as? ZapysError, .overlap([blocker.id]))
            }
        }
        let records = try SQLiteRepository(path: url.path).appointments()
        XCTAssertEqual(records.first { $0.id == original.id }, original)
        XCTAssertEqual(Set(records.map(\.id)), Set([original.id, blocker.id]))
    }
}
