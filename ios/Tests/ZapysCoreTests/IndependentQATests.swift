import Foundation
import XCTest
@testable import ZapysCore

/// Acceptance cases owned independently from the production implementation.
final class IndependentQATests: XCTestCase {
    private let day = "2099-11-02"

    private func completedAppointment(client: Client, start: Int = 600, end: Int = 660) -> Appointment {
        var appointment = Appointment(day: day, startMinute: start, endMinute: end)
        appointment.clientID = client.id
        appointment.isDraft = false
        return appointment
    }

    func testOverlapMatrixIncludesSelfCancelledAdjacentAndDifferentDays() {
        let original = Appointment(day: day, startMinute: 600, endMinute: 660)
        XCTAssertFalse(Rules.overlaps(original, original))
        for (start, end, expected) in [(540, 600, false), (660, 720, false), (599, 601, true), (659, 661, true), (610, 620, true), (590, 670, true)] {
            var candidate = Appointment(day: day, startMinute: start, endMinute: end)
            XCTAssertEqual(Rules.overlaps(original, candidate), expected, "Interval \(start)..<\(end)")
            XCTAssertEqual(Rules.overlaps(candidate, original), expected)
            candidate.day = "2099-11-03"
            XCTAssertFalse(Rules.overlaps(original, candidate))
            candidate.day = day
            candidate.status = .cancelled
            XCTAssertFalse(Rules.overlaps(original, candidate))
            XCTAssertFalse(Rules.overlaps(candidate, original))
        }
    }

    func testStrictDatesAndTimeBoundaries() {
        for invalidDay in ["2026-02-29", "2026-04-31", "2026-13-01", "2026-00-01", "2026-1-02", "", "2026-11-02 trailing"] {
            XCTAssertThrowsError(try Rules.validate(Appointment(day: invalidDay, startMinute: 600, endMinute: 630)), invalidDay)
        }
        XCTAssertNoThrow(try Rules.validate(Appointment(day: "2028-02-29", startMinute: 0, endMinute: 1)))
        XCTAssertNoThrow(try Rules.validate(Appointment(day: day, startMinute: 1438, endMinute: 1439)))
        for (start, end) in [(-1, 30), (600, 600), (601, 600), (1439, 1440), (1430, 1440), (0, 0)] {
            XCTAssertThrowsError(try Rules.validate(Appointment(day: day, startMinute: start, endMinute: end)))
        }
    }

    func testFiveServicesAllowedAndSixRejectedWithoutLimitingCatalogue() throws {
        let repository = try SQLiteRepository(path: ":memory:")
        let services = (0..<6).map { Service(name: "Service \($0)") }
        for service in services { try repository.saveService(service) }
        XCTAssertEqual(try repository.services().count, 6)
        var draft = Appointment(day: day, startMinute: 600, endMinute: 630)
        draft.services = services.prefix(5).map(ServiceSnapshot.init(service:))
        try repository.saveAppointment(draft)
        draft.services.append(ServiceSnapshot(service: services[5]))
        XCTAssertThrowsError(try repository.saveAppointment(draft))
        XCTAssertEqual(try repository.appointments().first?.services.count, 5)
    }

    func testOptionalServiceFieldsAndNonnegativeMoney() throws {
        var service = Service(name: "Unknown price and duration")
        XCTAssertNoThrow(try Rules.validate(service))
        service.priceMinor = 0
        XCTAssertNoThrow(try Rules.validate(service))
        service.priceMinor = -1
        XCTAssertThrowsError(try Rules.validate(service))
        service.priceMinor = nil
        service.durationMinutes = 0
        XCTAssertThrowsError(try Rules.validate(service))
        service.durationMinutes = -1
        XCTAssertThrowsError(try Rules.validate(service))
        var draft = Appointment(day: day, startMinute: 600, endMinute: 630)
        draft.receivedMinor = -1
        XCTAssertThrowsError(try Rules.validate(draft))
        draft.receivedMinor = 0
        XCTAssertNoThrow(try Rules.validate(draft))
    }

    func testNamePhoneAndTextLimits() {
        XCTAssertThrowsError(try Rules.validate(Client(name: " \n", phone: "123")))
        XCTAssertThrowsError(try Rules.validate(Client(name: "Client", phone: " \t")))
        var client = Client(name: "Client", phone: "+380000000000")
        client.note = String(repeating: "я", count: 2000)
        XCTAssertNoThrow(try Rules.validate(client))
        client.note.append("я")
        XCTAssertThrowsError(try Rules.validate(client))
        client.note = ""
        client.reminder.template = String(repeating: "x", count: 2001)
        XCTAssertThrowsError(try Rules.validate(client))
    }

    func testDraftMayBeBlankButCompletedAppointmentRequiresExistingClient() throws {
        let repository = try SQLiteRepository(path: ":memory:")
        var draft = Appointment(day: day, startMinute: 600, endMinute: 630)
        try repository.saveAppointment(draft)
        draft.isDraft = false
        XCTAssertThrowsError(try repository.saveAppointment(draft))
        draft.clientID = UUID()
        XCTAssertThrowsError(try repository.saveAppointment(draft))
        let unchanged = try XCTUnwrap(repository.appointments().first)
        XCTAssertTrue(unchanged.isDraft)
        XCTAssertNil(unchanged.clientID)
    }

    func testRejectedConflictPreservesOriginalAppointmentAndReminder() throws {
        let repository = try SQLiteRepository(path: ":memory:")
        var client = Client(name: "Client", phone: "123")
        client.reminder.enabled = true
        client.reminder.recipient = "123"
        try repository.saveClient(client)
        let first = completedAppointment(client: client)
        let second = completedAppointment(client: client, start: 700, end: 760)
        try repository.saveAppointment(first)
        try repository.saveAppointment(second)
        let appointmentsBefore = try repository.appointments()
        let remindersBefore = try repository.reminders()
        var conflicting = second
        conflicting.startMinute = 630
        conflicting.endMinute = 690
        XCTAssertThrowsError(try repository.saveAppointment(conflicting))
        XCTAssertEqual(try repository.appointments(), appointmentsBefore)
        XCTAssertEqual(try repository.reminders(), remindersBefore)
        try repository.saveAppointment(conflicting, allowOverlap: true)
        XCTAssertEqual(try repository.appointments().first(where: { $0.id == second.id })?.startMinute, 630)
        XCTAssertEqual(try repository.reminders().filter { $0.appointmentID == second.id }.count, 1)
    }

    func testAdjacentCancelledAndSelfUpdatesSaveWithoutOverlapOverride() throws {
        let repository = try SQLiteRepository(path: ":memory:")
        let client = Client(name: "Client", phone: "123")
        try repository.saveClient(client)
        var original = completedAppointment(client: client)
        try repository.saveAppointment(original)
        original.note = "Self update"
        try repository.saveAppointment(original)
        try repository.saveAppointment(completedAppointment(client: client, start: 660, end: 690))
        var cancelled = completedAppointment(client: client, start: 610, end: 620)
        cancelled.status = .cancelled
        try repository.saveAppointment(cancelled)
        XCTAssertEqual(try repository.appointments().count, 3)
    }

    func testDurableReopenPreservesSnapshotsNotesManualEndAndNullVersusFree() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("zapys-qa-\(UUID().uuidString).sqlite")
        defer {
            for suffix in ["", "-wal", "-shm"] { try? FileManager.default.removeItem(atPath: url.path + suffix) }
        }
        let client = Client(name: "Historical client", phone: "123")
        var first = Service(name: "First", durationMinutes: nil, priceMinor: nil)
        first.note = "Snapshot note"
        first.colorHex = "#112233"
        let second = Service(name: "Free", durationMinutes: 15, priceMinor: 0)
        var original = completedAppointment(client: client, start: 600, end: 647)
        original.services = [ServiceSnapshot(service: second), ServiceSnapshot(service: first)]
        original.endManuallySet = true
        original.note = "Text only appointment note"
        original.paid = true
        original.receivedMinor = 0
        do {
            let repository = try SQLiteRepository(path: url.path)
            try repository.saveClient(client)
            try repository.saveService(first)
            try repository.saveService(second)
            try repository.saveAppointment(original)
            first.name = "Edited catalogue"
            first.priceMinor = 500
            first.note = "Edited note"
            try repository.saveService(first)
            try repository.deleteService(second.id)
        }
        let reopened = try SQLiteRepository(path: url.path)
        let saved = try XCTUnwrap(reopened.appointments().first)
        XCTAssertEqual(saved.id, original.id)
        XCTAssertEqual(saved.services, original.services)
        XCTAssertEqual(saved.services.map(\.name), ["Free", "First"])
        XCTAssertEqual(saved.services[0].priceMinor, 0)
        XCTAssertNil(saved.services[1].priceMinor)
        XCTAssertNil(saved.services[1].durationMinutes)
        XCTAssertEqual(saved.services[1].note, "Snapshot note")
        XCTAssertEqual(saved.endMinute, 647)
        XCTAssertTrue(saved.endManuallySet)
        XCTAssertEqual(saved.note, original.note)
        XCTAssertTrue(saved.paid)
        XCTAssertEqual(saved.receivedMinor, 0)
    }

    func testFailedValidationDoesNotReplacePersistedSnapshotOrReminder() throws {
        let repository = try SQLiteRepository(path: ":memory:")
        var client = Client(name: "Client", phone: "123")
        client.reminder.enabled = true
        client.reminder.recipient = "123"
        try repository.saveClient(client)
        var appointment = completedAppointment(client: client)
        appointment.services = [ServiceSnapshot(service: Service(name: "Original"))]
        try repository.saveAppointment(appointment)
        let before = try repository.appointments()
        let reminders = try repository.reminders()
        appointment.note = String(repeating: "x", count: 2001)
        appointment.services = [ServiceSnapshot(service: Service(name: "Changed"))]
        XCTAssertThrowsError(try repository.saveAppointment(appointment))
        XCTAssertEqual(try repository.appointments(), before)
        XCTAssertEqual(try repository.reminders(), reminders)
    }

    func testRepeatIsNewUnpaidDraftAndDoesNotCopyOutboxState() throws {
        let repository = try SQLiteRepository(path: ":memory:")
        var client = Client(name: "Client", phone: "123")
        client.reminder.enabled = true
        client.reminder.recipient = "123"
        try repository.saveClient(client)
        var original = completedAppointment(client: client)
        original.services = [ServiceSnapshot(service: Service(name: "Repeat service", priceMinor: 0))]
        original.paid = true
        original.receivedMinor = 12345
        original.status = .completed
        try repository.saveAppointment(original)
        let repeated = Rules.repeated(original, day: "2099-11-09", startMinute: 720)
        try repository.saveAppointment(repeated)
        XCTAssertNotEqual(repeated.id, original.id)
        XCTAssertEqual(repeated.clientID, original.clientID)
        XCTAssertEqual(repeated.services.map(\.name), original.services.map(\.name))
        XCTAssertEqual(repeated.status, .planned)
        XCTAssertTrue(repeated.isDraft)
        XCTAssertFalse(repeated.paid)
        XCTAssertNil(repeated.receivedMinor)
        XCTAssertEqual(repeated.startMinute, 720)
        XCTAssertEqual(repeated.endMinute, 780)
        XCTAssertFalse(try repository.reminders().contains { $0.appointmentID == repeated.id })
    }

    func testRescheduleRefreshesSingleReminderAndCancellationInvalidatesIt() throws {
        let repository = try SQLiteRepository(path: ":memory:")
        var client = Client(name: "Client", phone: "123")
        client.reminder.enabled = true
        client.reminder.recipient = "123"
        client.reminder.template = "[дата] [час] [послуги]"
        try repository.saveClient(client)
        var appointment = completedAppointment(client: client)
        appointment.services = [ServiceSnapshot(service: Service(name: "First service"))]
        try repository.saveAppointment(appointment)
        let before = try XCTUnwrap(repository.reminders().first)
        appointment.day = "2099-11-03"
        appointment.startMinute = 720
        appointment.endMinute = 780
        appointment.services = [ServiceSnapshot(service: Service(name: "New service"))]
        try repository.saveAppointment(appointment)
        let reminders = try repository.reminders().filter { $0.appointmentID == appointment.id }
        XCTAssertEqual(reminders.count, 1)
        let refreshed = try XCTUnwrap(reminders.first)
        XCTAssertNotEqual(refreshed.dueAt, before.dueAt)
        XCTAssertEqual(refreshed.message, "2099-11-03 12:00 New service")
        appointment.status = .cancelled
        try repository.saveAppointment(appointment)
        XCTAssertFalse(try repository.reminders().contains { $0.appointmentID == appointment.id && $0.state != .cancelled })
    }

    func testPastCancelledCompletedAndDraftAppointmentsDoNotCreateReminders() throws {
        var client = Client(name: "Client", phone: "123")
        client.reminder.enabled = true
        client.reminder.recipient = "123"
        var appointment = completedAppointment(client: client)
        let now = try XCTUnwrap(Rules.date(day: day, minute: 601))
        XCTAssertNil(Rules.makeReminder(appointment: appointment, client: client, now: now))
        appointment.day = "2099-11-03"
        for status in [AppointmentStatus.cancelled, .completed, .noShow] {
            appointment.status = status
            XCTAssertNil(Rules.makeReminder(appointment: appointment, client: client, now: now))
        }
        appointment.status = .planned
        appointment.isDraft = true
        XCTAssertNil(Rules.makeReminder(appointment: appointment, client: client, now: now))
        appointment.isDraft = false
        XCTAssertNotNil(Rules.makeReminder(appointment: appointment, client: client, now: now))
    }

    func testReminderAlreadyDueRequiresManualActionAndNeverClaimsDelivery() throws {
        var client = Client(name: "Client", phone: "123")
        client.reminder.enabled = true
        client.reminder.recipient = "123"
        client.reminder.leadMinutes = 60
        let appointment = completedAppointment(client: client)
        let now = try XCTUnwrap(Rules.date(day: day, minute: 570))
        let reminder = try XCTUnwrap(Rules.makeReminder(appointment: appointment, client: client, now: now))
        XCTAssertEqual(reminder.state, .needsManualAction)
        XCTAssertEqual(reminder.appointmentID, appointment.id)
    }

    func testTemplateSubstitutionUsesLiteralValuesAndAllowsEmptyServices() {
        let appointment = Appointment(day: day, startMinute: 605, endMinute: 630)
        XCTAssertEqual(Rules.render(template: "[ім’я]|[ім'я]|[дата]|[час]|[послуги]|[unknown]", appointment: appointment, clientName: "[час] $1 \\ value", serviceNames: ["B", "A"]), "[час] $1 \\ value|[час] $1 \\ value|2099-11-02|10:05|B, A|[unknown]")
        XCTAssertEqual(Rules.render(template: "[ім’я]:[послуги]", appointment: appointment, clientName: "Client", serviceNames: []), "Client:")
    }

    func testBothUkrainianApostrophesSubstituteWithoutRescanningInsertedVariables() {
        let appointment = Appointment(day: day, startMinute: 605, endMinute: 630)
        // Unicode escapes keep the two apostrophes explicit across terminal encodings.
        let asciiName = "[\u{456}\u{43C}'\u{44F}]"
        let curlyName = "[\u{456}\u{43C}\u{2019}\u{44F}]"
        let dateToken = "[\u{434}\u{430}\u{442}\u{430}]"
        let timeToken = "[\u{447}\u{430}\u{441}]"
        let servicesToken = "[\u{43F}\u{43E}\u{441}\u{43B}\u{443}\u{433}\u{438}]"
        let result = Rules.render(
            template: "\(asciiName)|\(curlyName)|\(servicesToken)|\(dateToken)|\(timeToken)",
            appointment: appointment,
            clientName: "\(dateToken) $1 \\ \(curlyName)",
            serviceNames: [timeToken, asciiName]
        )
        XCTAssertEqual(result, "\(dateToken) $1 \\ \(curlyName)|\(dateToken) $1 \\ \(curlyName)|\(timeToken), \(asciiName)|2099-11-02|10:05")
    }
    func testKyivWallTimesResolveDSTGapForwardAndOverlapToEarlierOccurrence() throws {
        let zone = try XCTUnwrap(TimeZone(identifier: "Europe/Kyiv"))
        let iso = ISO8601DateFormatter()
        // Kyiv skips 03:00 to 04:00 in spring; preserve the requested :30.
        XCTAssertEqual(Rules.date(day: "2026-03-29", minute: 210, timeZone: zone), iso.date(from: "2026-03-29T01:30:00Z"))
        // Noon remains noon after the offset changes, rather than 13:00.
        XCTAssertEqual(Rules.date(day: "2026-03-29", minute: 720, timeZone: zone), iso.date(from: "2026-03-29T09:00:00Z"))
        // The first 03:30 is still summer time, UTC+03:00.
        XCTAssertEqual(Rules.date(day: "2026-10-25", minute: 210, timeZone: zone), iso.date(from: "2026-10-25T00:30:00Z"))
        XCTAssertEqual(Rules.date(day: "2026-10-25", minute: 720, timeZone: zone), iso.date(from: "2026-10-25T10:00:00Z"))
    }

    func testOutboxReadRechecksClockWithoutAnAppointmentEdit() throws {
        let repository = try SQLiteRepository(path: ":memory:")
        var client = Client(name: "Клієнт", phone: "123")
        client.reminder.enabled = true
        client.reminder.recipient = "123"
        client.reminder.leadMinutes = 60
        try repository.saveClient(client)
        let appointment = completedAppointment(client: client)
        let beforeDue = try XCTUnwrap(Rules.date(day: day, minute: 539))
        let due = try XCTUnwrap(Rules.date(day: day, minute: 540))
        let start = try XCTUnwrap(Rules.date(day: day, minute: 600))
        try repository.saveAppointment(appointment, now: beforeDue)
        XCTAssertEqual(try repository.reminders(now: beforeDue).first?.state, .scheduled)
        XCTAssertEqual(try repository.reminders(now: due).first?.state, .needsManualAction)
        let expired = try repository.reminders(now: start)
        XCTAssertFalse(expired.contains { $0.appointmentID == appointment.id && $0.state != .cancelled })
    }

    func testExpiredAndDeletedReminderIdentifiersCannotBeHandedToMessenger() throws {
        let repository = try SQLiteRepository(path: ":memory:")
        var client = Client(name: "Клієнт", phone: "123")
        client.reminder.enabled = true
        client.reminder.recipient = "123"
        try repository.saveClient(client)
        let appointment = completedAppointment(client: client)
        let before = try XCTUnwrap(Rules.date(day: day, minute: 500))
        let start = try XCTUnwrap(Rules.date(day: day, minute: 600))
        try repository.saveAppointment(appointment, now: before)
        let identifier = try XCTUnwrap(repository.reminders(now: before).first?.id)
        try? repository.markHandedToMessenger(identifier, now: start)
        XCTAssertFalse(try repository.reminders(now: start).contains { $0.id == identifier && $0.state == .handedToMessenger })
        try repository.deleteAppointment(appointment.id)
        try? repository.markHandedToMessenger(identifier, now: before)
        XCTAssertFalse(try repository.reminders(now: before).contains { $0.id == identifier && $0.state == .handedToMessenger })
    }
}
