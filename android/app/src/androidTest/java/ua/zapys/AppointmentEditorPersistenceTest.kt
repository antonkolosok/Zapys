package ua.zapys

import android.test.AndroidTestCase
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import java.time.LocalDate
import java.time.ZoneId

/** Durable editor contracts; fixtures never access contacts or send messages. */
class AppointmentEditorPersistenceTest : AndroidTestCase() {
    private val database = "independent-editor-test.db"
    private lateinit var repo: SQLiteRepository
    private val date = LocalDate.of(2031, 4, 5)
    override fun setUp() { super.setUp(); context.deleteDatabase(database); repo = open() }
    override fun tearDown() { repo.close(); context.deleteDatabase(database); super.tearDown() }
    private fun open() = SQLiteRepository(context, database, { 0L }, ZoneId.of("UTC"))
    private fun reopen() { repo.close(); repo = open() }
    private fun client(): Client = Client(firstName = "Fixture", phone = "100", reminder = ReminderPreferences(enabled = true, recipient = "fixture")).also { repo.saveClient(it) }
    private fun visit(c: Client, start: Int) = Appointment(date = date, startMinute = start, endMinute = start + 30, clientId = c.id, status = AppointmentStatus.PLANNED)

    fun testValidationFailureAndDeclinedConflictPreserveRecordAndOutboxAfterReopen() {
        val c = client()
        val first = repo.saveAppointment(visit(c, 600))
        val edited = repo.saveAppointment(visit(c, 700).copy(note = "original", paid = true, receivedMinor = 123))
        val reminders = repo.reminders().toSet()
        try { repo.saveAppointment(edited.copy(endMinute = 700, note = "invalid")); fail("Invalid interval accepted") } catch (_: ValidationException) { }
        try { repo.saveAppointment(edited.copy(startMinute = 610, endMinute = 640, note = "declined")); fail("Conflict accepted without confirmation") } catch (e: ConflictException) { assertEquals(listOf(first), e.appointments) }
        // User declines: no second save with allowOverlap=true.
        reopen()
        assertEquals(listOf(first, edited), repo.appointments())
        assertEquals(reminders, repo.reminders().toSet())
    }

    fun testConfirmedOverlapPersistsOrderedVisitOverridesWithoutChangingCatalogue() {
        val c = client()
        val first = Service(name = "First", durationMinutes = 20, priceMinor = 1500, note = "catalogue")
        val second = Service(name = "Second", durationMinutes = 40, priceMinor = 2400)
        repo.saveService(first); repo.saveService(second)
        val original = repo.saveAppointment(visit(c, 600))
        val overrides = listOf(second.snapshot().copy(durationMinutes = null, priceMinor = 0), first.snapshot().copy(durationMinutes = 7, priceMinor = null))
        val form = AppointmentDraft.from(visit(c, 610))
        form.end = "10:50"; form.manualEnd = true; form.note = "visit note"; form.paid = true; form.received = "0"
        overrides.forEach(form::add)
        val saved = repo.saveAppointment(form.build(repo.clients()), allowOverlap = true)
        reopen()
        assertEquals(listOf(original, saved), repo.appointments())
        assertEquals(overrides, repo.appointments().single { it.id == saved.id }.services)
        assertEquals(setOf(first, second), repo.services().toSet())
        assertNull(saved.services[0].durationMinutes)
        assertEquals(0L, saved.services[0].priceMinor)
        assertNull(saved.services[1].priceMinor)
    }

    fun testRepeatUsesNewIdAndDoesNotInheritPaymentOrReminderState() {
        val c = client()
        val source = repo.saveAppointment(visit(c, 600).copy(paid = true, receivedMinor = 900, manualEnd = true, services = listOf(Service(name = "Visit").snapshot())))
        val sourceReminder = repo.reminders().single()
        repo.setReminderState(sourceReminder.id, ReminderState.HANDED_OFF)
        val form = AppointmentDraft.repeat(source)
        form.date = date.plusDays(1).toString(); form.start = "13:20"; form.end = "13:50"
        val repeated = repo.saveAppointment(form.build(repo.clients()))
        reopen()
        assertFalse(source.id == repeated.id)
        assertEquals(AppointmentStatus.DRAFT, repeated.status)
        assertFalse(repeated.paid); assertNull(repeated.receivedMinor); assertFalse(repeated.manualEnd)
        assertEquals(source.services, repeated.services); assertEquals(source.clientId, repeated.clientId)
        assertEquals(source, repo.appointments().single { it.id == source.id })
        assertEquals(listOf(sourceReminder.copy(state = ReminderState.HANDED_OFF)), repo.reminders())
        assertEquals(repeated, repo.appointments().single { it.id == repeated.id })
    }
}
