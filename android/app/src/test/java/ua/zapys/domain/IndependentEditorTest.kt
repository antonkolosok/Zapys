package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class IndependentEditorTest {
    private val day = LocalDate.of(2031, 4, 5)
    private val client = Client(firstName = "Fixture", phone = "100")
    private fun invalid(block: () -> Unit) {
        try { block(); fail("Expected validation failure") } catch (_: ValidationException) { }
    }
    @Test fun freshRequiresEndAndFailureRetainsRawFieldsAndStableIdentity() {
        val form = AppointmentDraft.fresh(id = "stable", date = day, start = 600)
        assertEquals("", form.end)
        form.note = "unsaved note"; form.received = "bad money"
        invalid { form.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT) }
        assertEquals("stable", form.id); assertEquals("", form.end)
        assertEquals("unsaved note", form.note); assertEquals("bad money", form.received)
        form.end = "10:30"; form.received = ""
        assertEquals("stable", form.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT).id)
    }
    @Test fun draftAllowsNoClientButPlannedRequiresExistingClient() {
        val form = AppointmentDraft.fresh(date = day); form.end = "10:00"
        assertNull(form.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT).clientId)
        form.status = AppointmentStatus.PLANNED
        invalid { form.build(listOf(client)) }
        form.clientId = "deleted-client"; invalid { form.build(listOf(client)) }
        form.clientId = client.id
        assertEquals(client.id, form.build(listOf(client)).clientId)
    }
    @Test fun fiveOrderedSnapshotsAllowedAndSixthCannotMutateSelection() {
        val form = AppointmentDraft.fresh(date = day)
        val snapshots = (1..5).map { Service(id = "s$it", name = "Service $it", durationMinutes = it).snapshot() }
        snapshots.forEach(form::add)
        invalid { form.add(Service(name = "Sixth").snapshot()) }
        assertEquals(snapshots, form.services)
        assertEquals(snapshots, form.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT).services)
        // Direct state restoration must also be validated at build, not just add.
        form.services.add(Service(name = "Sixth").snapshot())
        invalid { form.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT) }
    }
    @Test fun manualEndSurvivesDurationChangesAndUnknownDoesNotInventEnd() {
        val form = AppointmentDraft.fresh(date = day, start = 600)
        form.add(Service(name = "Known", durationMinutes = 20).snapshot())
        assertEquals("10:20", form.end)
        form.end = "11:17"; form.manualEnd = true
        form.services[0] = form.services[0].copy(durationMinutes = 90)
        form.add(Service(name = "Unknown").snapshot()); form.suggest()
        val saved = form.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT)
        assertEquals(677, saved.endMinute); assertTrue(saved.manualEnd)
        val unknown = AppointmentDraft.fresh(date = day, start = 600)
        unknown.add(Service(name = "Unknown").snapshot())
        assertEquals("", unknown.end); invalid { unknown.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT) }
    }
    @Test fun visitOverridesPreserveOrderNullFreeAndDetachedCatalogueSnapshots() {
        val catalogue = Service(name = "Catalogue", durationMinutes = 30, priceMinor = 1234)
        val form = AppointmentDraft.fresh(date = day, start = 600)
        form.end = "11:00"; form.manualEnd = true
        form.add(catalogue.snapshot().copy(durationMinutes = null, priceMinor = 0))
        form.add(Service(name = "Other", durationMinutes = 10).snapshot().copy(priceMinor = null))
        val saved = form.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT)
        assertNull(saved.services[0].durationMinutes); assertEquals(0L, saved.services[0].priceMinor)
        assertNull(saved.services[1].priceMinor)
        assertEquals(30, catalogue.durationMinutes); assertEquals(1234L, catalogue.priceMinor)
        form.services.reverse()
        assertEquals("Catalogue", saved.services[0].name)
    }
    @Test fun repeatRequiresNewTimeAndResetsVisitStateWithoutChangingSource() {
        val source = Appointment(date = day, startMinute = 600, endMinute = 660, clientId = client.id,
            services = listOf(Service(name = "Retained").snapshot()), note = "retained note",
            status = AppointmentStatus.COMPLETED, paid = true, receivedMinor = 100, manualEnd = true)
        val form = AppointmentDraft.repeat(source)
        assertNotEquals(source.id, form.id); assertEquals("", form.date); assertEquals("", form.start); assertEquals("", form.end)
        assertEquals(AppointmentStatus.DRAFT, form.status); assertFalse(form.paid); assertEquals("", form.received); assertFalse(form.manualEnd)
        assertEquals(source.services, form.services); assertEquals(source.clientId, form.clientId); assertEquals(source.note, form.note)
        invalid { form.build(listOf(client)) }
        form.date = day.plusDays(1).toString(); form.start = "12:00"; form.end = "12:30"
        assertEquals(720, form.build(listOf(client)).startMinute)
        assertEquals(AppointmentStatus.COMPLETED, source.status); assertTrue(source.paid)
    }
    @Test fun endBoundaryRejectsOvernightAndAccepts2359() {
        val form = AppointmentDraft.fresh(date = day, start = 1380)
        for (end in listOf("24:00", "00:30", "23:00")) { form.end = end; invalid { form.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT) } }
        form.end = "23:59"; assertEquals(1439, form.build(emptyList(), requestedStatus = AppointmentStatus.DRAFT).endMinute)
    }
}
