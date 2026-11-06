import XCTest
@testable import ZapysCore

/// Independent regressions use real SQLite files and public production APIs.
/// Authored on Windows; execution requires the macOS Swift package job.
final class IndependentEditorTests: XCTestCase {
    func testVisitOverridesReopenInOrderWithoutChangingCatalogue() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        let catalogue = Service(name: "Catalogue", durationMinutes: 30, priceMinor: 1234)
        let other = Service(name: "Other", durationMinutes: 15, priceMinor: 5678)
        var visit = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 677)
        visit.endManuallySet = true
        var free = ServiceSnapshot(service: catalogue)
        free.durationMinutes = nil; free.priceMinor = 0
        var unknown = ServiceSnapshot(service: other)
        unknown.durationMinutes = 90; unknown.priceMinor = nil
        visit.services = [unknown, free]
        do {
            let repository = try SQLiteRepository(path: url.path)
            try repository.saveService(catalogue); try repository.saveService(other)
            try repository.saveAppointment(visit)
        }
        let reopened = try SQLiteRepository(path: url.path)
        let saved = try XCTUnwrap(reopened.appointments().first)
        XCTAssertEqual(saved, visit)
        XCTAssertEqual(saved.services.map(\.sourceID), [other.id, catalogue.id])
        XCTAssertNil(saved.services[0].priceMinor)
        XCTAssertEqual(saved.services[1].priceMinor, 0)
        XCTAssertNil(saved.services[1].durationMinutes)
        XCTAssertTrue(saved.endManuallySet)
        XCTAssertEqual(saved.endMinute, 677)
        XCTAssertEqual(try reopened.services(), [catalogue, other])
    }

    func testDeclinedConflictPreservesOriginalAppointmentAfterReopen() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        var original = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 660)
        original.status = .completed; original.note = "original"; original.paid = true
        original.receivedMinor = 0
        let blocker = Appointment(day: original.day, startMinute: 720, endMinute: 780)
        do {
            let repository = try SQLiteRepository(path: url.path)
            try repository.saveAppointment(original); try repository.saveAppointment(blocker)
            var unsaved = original
            unsaved.startMinute = 730; unsaved.endMinute = 790
            unsaved.status = .noShow; unsaved.note = "retained unsaved input"
            XCTAssertThrowsError(try repository.saveAppointment(unsaved)) { error in
                XCTAssertEqual(error as? ZapysError, .overlap([blocker.id]))
            }
            // Declining means no allowOverlap retry. The value submitted remains reusable.
            XCTAssertEqual(unsaved.note, "retained unsaved input")
            XCTAssertEqual(unsaved.status, .noShow)
        }
        let reopened = try SQLiteRepository(path: url.path)
        XCTAssertEqual(try reopened.appointments(), [original, blocker])
    }

    func testPersistedRepeatHasNewIdentityAndResetsVisitStateWithoutChangingSource() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        let client = Client(name: "Person", phone: "100")
        var source = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 660)
        source.clientID = client.id; source.clientNameSnapshot = client.name
        source.isDraft = false; source.status = .completed
        source.paid = true; source.receivedMinor = 1234; source.endManuallySet = true
        source.note = "repeat note"
        source.services = [ServiceSnapshot(service: Service(name: "Visit", priceMinor: 0))]
        let repeated: Appointment
        do {
            let repository = try SQLiteRepository(path: url.path)
            try repository.saveClient(client); try repository.saveAppointment(source)
            repeated = try repository.repeatAppointment(source.id, on: "2099-11-03", startMinute: 720)
            XCTAssertNotEqual(repeated.id, source.id)
        }
        let reopened = try SQLiteRepository(path: url.path)
        let records = try reopened.appointments()
        XCTAssertEqual(records.first { $0.id == source.id }, source)
        let copy = try XCTUnwrap(records.first { $0.id == repeated.id })
        XCTAssertEqual(copy.clientID, source.clientID)
        XCTAssertEqual(copy.services, source.services)
        XCTAssertEqual(copy.note, source.note)
        XCTAssertEqual(copy.day, "2099-11-03")
        XCTAssertEqual(copy.startMinute, 720); XCTAssertEqual(copy.endMinute, 780)
        XCTAssertTrue(copy.isDraft); XCTAssertEqual(copy.status, .planned)
        XCTAssertFalse(copy.paid); XCTAssertNil(copy.receivedMinor)
        XCTAssertFalse(copy.endManuallySet)
    }
    func testMissingEndAndInvalidOverridesRetainAllRawInputAndIdentity() throws {
        var draft = AppointmentDraft()
        draft.appointment.note = "unsaved"
        draft.receivedText = "bad money"
        let before = draft
        XCTAssertThrowsError(try draft.prepared(isDraft: true))
        XCTAssertEqual(draft, before)
        draft.endProvided = true; draft.receivedText = ""
        try draft.addService(Service(name: "Unknown"))
        let id = try XCTUnwrap(draft.appointment.services.first?.id)
        draft.durationText[id] = "broken duration"
        draft.priceText[id] = "broken price"
        let invalid = draft
        XCTAssertThrowsError(try draft.prepared(isDraft: true))
        XCTAssertEqual(draft, invalid)
        XCTAssertEqual(draft.appointment.id, before.appointment.id)
    }

    func testManualEndAndOrderedRawOverridesApplyToSnapshotOnly() throws {
        let catalogue = Service(name: "Catalogue", durationMinutes: 30, priceMinor: 1234)
        var draft = AppointmentDraft()
        draft.appointment.startMinute = 600
        try draft.addService(catalogue)
        draft.appointment.endMinute = 677; draft.appointment.endManuallySet = true
        try draft.addService(Service(name: "Second", durationMinutes: 10, priceMinor: 0))
        let firstID = draft.appointment.services[0].id
        let secondID = draft.appointment.services[1].id
        draft.durationText[firstID] = "90"; draft.priceText[firstID] = ""
        draft.priceText[secondID] = "0"
        XCTAssertEqual(draft.suggestion, 700)
        draft.applySuggestion()
        XCTAssertEqual(draft.appointment.endMinute, 677)
        let saved = try draft.prepared(isDraft: true)
        XCTAssertEqual(saved.services.map(\.name), ["Catalogue", "Second"])
        XCTAssertEqual(saved.services[0].durationMinutes, 90)
        XCTAssertNil(saved.services[0].priceMinor)
        XCTAssertEqual(saved.services[1].priceMinor, 0)
        XCTAssertEqual(catalogue.durationMinutes, 30); XCTAssertEqual(catalogue.priceMinor, 1234)
        draft.applySuggestion(force: true)
        XCTAssertEqual(draft.appointment.endMinute, 700)
        XCTAssertFalse(draft.appointment.endManuallySet)
    }

    func testHistoryUsesExactEndAndCapturedNowInsteadOfStartOrStatus() throws {
        let day = "2099-11-02"
        let now = try XCTUnwrap(Rules.date(day: day, minute: 660))
        let ended = Appointment(day: day, startMinute: 600, endMinute: 660)
        var ongoing = Appointment(day: day, startMinute: 630, endMinute: 661)
        ongoing.status = .completed
        var future = Appointment(day: day, startMinute: 720, endMinute: 780)
        future.status = .cancelled
        let split = AppointmentHistory.split([future, ended, ongoing], now: now)
        XCTAssertEqual(split.past.map(\.id), [ended.id])
        XCTAssertEqual(split.upcoming.map(\.id), [ongoing.id, future.id])
    }

    func testPrimaryAndDraftFailedPreparationDoNotMutateRetainedStatusOrInlineInput() {
        var draft = AppointmentDraft()
        draft.endProvided = true; draft.appointment.status = .completed
        draft.inlineName = "Populated"; draft.inlinePhone = ""
        let before = draft
        XCTAssertThrowsError(try draft.prepared(isDraft: false))
        XCTAssertEqual(draft, before)
        XCTAssertThrowsError(try draft.prepared(isDraft: true))
        XCTAssertEqual(draft, before)
    }

    func testSuccessiveInlineClientsAfterDeclinedSavesRemainSeparateAfterReopen() throws {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: url) }
        var draft = AppointmentDraft()
        draft.endProvided = true
        let blocker = Appointment(day: draft.appointment.day, startMinute: 540, endMinute: 600)
        let first: Client
        let second: Client
        do {
            let repository = try SQLiteRepository(path: url.path)
            try repository.saveAppointment(blocker)
            draft.inlineName = "First"; draft.inlinePhone = "100"
            first = try XCTUnwrap(draft.prepareInlineClient())
            XCTAssertEqual(try draft.prepareInlineClient()?.id, first.id)
            try repository.saveClient(first); draft.inlineClientSaved(first)
            XCTAssertNotEqual(draft.inlineClientID, first.id)
            XCTAssertEqual(draft.inlineName, ""); XCTAssertEqual(draft.inlinePhone, "")
            let retained = draft
            XCTAssertThrowsError(try repository.saveAppointment(draft.prepared(isDraft: false)))
            XCTAssertEqual(draft, retained)
            draft.inlineName = "Second"; draft.inlinePhone = "100"
            second = try XCTUnwrap(draft.prepareInlineClient())
            XCTAssertNotEqual(second.id, first.id)
            try repository.saveClient(second); draft.inlineClientSaved(second)
            XCTAssertThrowsError(try repository.saveAppointment(draft.prepared(isDraft: true)))
            draft.selectClient(first)
            XCTAssertEqual(try draft.prepared(isDraft: false).clientID, first.id)
        }
        let reopened = try SQLiteRepository(path: url.path)
        XCTAssertEqual(try reopened.clients(), [first, second])
        XCTAssertEqual(try reopened.appointments(), [blocker])
    }

    func testRepeatFormRequiresEndResetsStateAndKeepsSourceUnchanged() throws {
        var source = Appointment(day: "2099-11-02", startMinute: 600, endMinute: 660)
        source.status = .noShow; source.paid = true; source.receivedMinor = 0
        source.endManuallySet = true; source.note = "source"
        let unchanged = source
        let draft = AppointmentDraft.repeated(source)
        XCTAssertNotEqual(draft.appointment.id, source.id)
        XCTAssertFalse(draft.endProvided)
        XCTAssertTrue(draft.appointment.isDraft)
        XCTAssertEqual(draft.appointment.status, .planned)
        XCTAssertFalse(draft.appointment.paid); XCTAssertNil(draft.appointment.receivedMinor)
        XCTAssertFalse(draft.appointment.endManuallySet)
        XCTAssertThrowsError(try draft.prepared(isDraft: true))
        XCTAssertEqual(source, unchanged)
    }
    func testFiveServicesAndLargePriceTotalDoNotOverflowOrMutateOnSixth() throws {
        var draft = AppointmentDraft()
        draft.endProvided = true
        for index in 1...5 {
            try draft.addService(Service(name: "Service \(index)", priceMinor: Int64.max))
        }
        let before = draft
        XCTAssertThrowsError(try draft.addService(Service(name: "Sixth")))
        XCTAssertEqual(draft, before)
        XCTAssertEqual(try draft.prepared(isDraft: true).services.count, 5)
        // Five Int64.max values expressed in major units, without Int64 summation.
        XCTAssertTrue(draft.totalText.hasPrefix("461168601842738790.35"))
    }
}

