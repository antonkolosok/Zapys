import XCTest
@testable import ZapysCore

final class DomainTests: XCTestCase {
    func testAdjacentAndCancelledIntervalsDoNotConflict() {
        let a = Appointment(day: "2026-11-02", startMinute: 600, endMinute: 630)
        var b = Appointment(day: a.day, startMinute: 630, endMinute: 660)
        XCTAssertFalse(Rules.overlaps(a, b))
        b.startMinute = 629
        XCTAssertTrue(Rules.overlaps(a, b))
        b.status = .cancelled
        XCTAssertFalse(Rules.overlaps(a, b))
    }
    func testInvalidBoundaryAndServiceCountAreRejected() {
        var a = Appointment(day: "2026-11-02", startMinute: 1430, endMinute: 1440)
        XCTAssertThrowsError(try Rules.validate(a))
        a.endMinute = 1439
        XCTAssertNoThrow(try Rules.validate(a))
        a.services = (0..<6).map { ServiceSnapshot(service: Service(name: "Послуга \($0)")) }
        XCTAssertThrowsError(try Rules.validate(a))
    }
    func testTemplateReplacementIsLiteralAndDoesNotRescanClientInput() {
        let a = Appointment(day: "2026-11-02", startMinute: 600, endMinute: 630)
        let text = Rules.render(template: "[ім’я] [дата] [час] [послуги]", appointment: a, clientName: "[час]", serviceNames: ["Стрижка"])
        XCTAssertEqual(text, "[час] 2026-11-02 10:00 Стрижка")
    }
    func testRepeatResetsIdentityPaymentStatusAndEndOverride() {
        var a = Appointment(day: "2026-11-02", startMinute: 600, endMinute: 645)
        a.isDraft = false; a.status = .completed; a.paid = true; a.receivedMinor = 0; a.endManuallySet = true
        let b = Rules.repeated(a, day: "2026-11-09", startMinute: 660)
        XCTAssertNotEqual(a.id, b.id)
        XCTAssertTrue(b.isDraft); XCTAssertFalse(b.paid); XCTAssertNil(b.receivedMinor)
        XCTAssertEqual(b.status, .planned); XCTAssertFalse(b.endManuallySet); XCTAssertEqual(b.endMinute, 705)
    }
    func testUnknownAndFreeAreDistinctAndSuggestionRequiresEveryDuration() {
        let unknown = ServiceSnapshot(service: Service(name: "A"))
        let free = ServiceSnapshot(service: Service(name: "B", durationMinutes: 20, priceMinor: 0))
        XCTAssertNil(unknown.priceMinor); XCTAssertEqual(free.priceMinor, 0)
        XCTAssertNil(Rules.suggestedEnd(startMinute: 600, services: [unknown, free]))
        XCTAssertEqual(Rules.suggestedEnd(startMinute: 600, services: [free]), 620)
    }
}
