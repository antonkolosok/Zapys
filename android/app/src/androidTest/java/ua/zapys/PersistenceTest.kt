package ua.zapys

import android.test.AndroidTestCase
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import java.time.LocalDate
import java.time.ZoneId

class PersistenceTest : AndroidTestCase() {
    private val name = "persistence-test.db"
    private lateinit var repo: SQLiteRepository
    override fun setUp() { super.setUp(); context.deleteDatabase(name); repo = open() }
    override fun tearDown() { repo.close(); context.deleteDatabase(name); super.tearDown() }
    private fun open() = SQLiteRepository(context, name, { 0L }, ZoneId.of("UTC"))
    private fun visit(c: Client) = Appointment(date = LocalDate.of(2030, 1, 2), startMinute = 600, endMinute = 630, clientId = c.id, status = AppointmentStatus.PLANNED)
    fun testReopenPreservesNullZeroAndOrderedHistoricalSnapshots() {
        val c = Client(firstName = "Олена", phone = "0671234567"); repo.saveClient(c)
        val unknown = Service(name = "Невідома"); val free = Service(name = "Безкоштовна", priceMinor = 0)
        repo.saveService(unknown); repo.saveService(free)
        val a = repo.saveAppointment(visit(c).copy(services = listOf(free.snapshot(), unknown.snapshot()), note = "Нотатка", manualEnd = true))
        repo.saveService(free.copy(name = "Змінена", priceMinor = 123)); repo.deleteService(unknown.id)
        repo.close(); repo = open()
        val saved = repo.appointments().single()
        assertEquals(a, saved); assertEquals(0L, saved.services[0].priceMinor); assertNull(saved.services[1].priceMinor)
    }
    fun testReferencedClientCannotBeDeleted() {
        val c = Client(firstName = "Олена", phone = "0671234567"); repo.saveClient(c); repo.saveAppointment(visit(c))
        try { repo.deleteClient(c.id); fail("Referenced client deletion must fail") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(c, repo.clients().single())
    }
    fun testDatabaseFailureRollsBackAppointmentSnapshotsAndReminder() {
        val c = Client(firstName = "Олена", phone = "0671234567", reminder = ReminderPreferences(enabled = true, recipient = "123")); repo.saveClient(c)
        val a = repo.saveAppointment(visit(c)); val reminder = repo.reminders().single()
        context.openOrCreateDatabase(name, 0, null).use { db -> db.execSQL("CREATE TRIGGER fail_snapshot BEFORE INSERT ON appointment_services BEGIN SELECT RAISE(ABORT, 'fixture failure'); END") }
        try { repo.saveAppointment(a.copy(startMinute = 700, endMinute = 730, services = listOf(Service(name = "Нова").snapshot()))); fail("Must fail") } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals(a, repo.appointments().single()); assertEquals(reminder, repo.reminders().single())
    }
    fun testRescheduleAndCancellationInvalidateOldReminder() {
        val c = Client(firstName = "Олена", phone = "0671234567", reminder = ReminderPreferences(enabled = true, recipient = "123")); repo.saveClient(c)
        val a = repo.saveAppointment(visit(c)); val old = repo.reminders().single()
        val changed = repo.saveAppointment(a.copy(startMinute = 700, endMinute = 730))
        val fresh = repo.reminders().single(); assertFalse(old.id == fresh.id); assertFalse(old.text == fresh.text)
        repo.setReminderState(old.id, ReminderState.HANDED_OFF); assertEquals(fresh, repo.reminders().single())
        repo.saveAppointment(changed.copy(status = AppointmentStatus.CANCELLED)); assertEquals(0, repo.reminders().size)
    }
    fun testPaymentOnlyEditDoesNotReviveHandedOffReminder() {
        val c = Client(firstName = "Олена", phone = "0671234567", reminder = ReminderPreferences(enabled = true, recipient = "123")); repo.saveClient(c)
        val a = repo.saveAppointment(visit(c)); val old = repo.reminders().single()
        repo.setReminderState(old.id, ReminderState.HANDED_OFF)
        repo.saveAppointment(a.copy(paid = true))
        assertEquals(old.copy(state = ReminderState.HANDED_OFF), repo.reminders().single())
    }
    fun testClockExpiryInvalidatesReadAndOldActionWithoutAppointmentSave() {
        repo.close()
        var clock = 0L
        repo = SQLiteRepository(context, name, { clock }, ZoneId.of("UTC"))
        val c = Client(firstName = "Олена", phone = "0671234567", reminder = ReminderPreferences(enabled = true, recipient = "123")); repo.saveClient(c)
        val a = repo.saveAppointment(visit(c)); val old = repo.reminders().single()
        clock = a.date.atTime(10, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        repo.setReminderState(old.id, ReminderState.HANDED_OFF)
        assertTrue(repo.reminders().isEmpty())
        repo.close(); repo = SQLiteRepository(context, name, { clock }, ZoneId.of("UTC"))
        assertTrue(repo.reminders().isEmpty())
    }
    fun testClockExpiryIsHiddenBeforeAnyMutation() {
        repo.close()
        var clock = 0L
        repo = SQLiteRepository(context, name, { clock }, ZoneId.of("UTC"))
        val c = Client(firstName = "Олена", phone = "0671234567", reminder = ReminderPreferences(enabled = true, recipient = "123")); repo.saveClient(c)
        repo.saveAppointment(visit(c)); assertEquals(1, repo.reminders().size)
        clock = Long.MAX_VALUE
        assertTrue(repo.reminders().isEmpty())
    }
    fun testConflictingSaveKeepsOriginalUntouched() {
        val c = Client(firstName = "Олена", phone = "0671234567"); repo.saveClient(c)
        val a = repo.saveAppointment(visit(c)); val b = repo.saveAppointment(visit(c).copy(startMinute = 700, endMinute = 730))
        try { repo.saveAppointment(b.copy(startMinute = 610, endMinute = 640)); fail("Must require confirmation") } catch (_: ConflictException) { }
        assertEquals(listOf(a, b), repo.appointments())
        repo.saveAppointment(b.copy(startMinute = 610, endMinute = 640), true)
    }
    fun testClientPreferenceEditRebuildsPendingReminderAndProfileIsDurable() {
        val c = Client(firstName = "Олена", phone = "0671234567", reminder = ReminderPreferences(enabled = true, recipient = "123")); repo.saveClient(c); repo.saveAppointment(visit(c))
        repo.saveClient(c.copy(reminder = c.reminder.copy(enabled = false))); assertTrue(repo.reminders().isEmpty())
        val p = LocalProfile("123", "local@example.invalid", "Олена"); repo.saveProfile(p); repo.close(); repo = open(); assertEquals(p, repo.profile())
    }
}
