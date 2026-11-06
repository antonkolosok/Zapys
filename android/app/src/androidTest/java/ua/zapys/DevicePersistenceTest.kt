package ua.zapys

import android.test.AndroidTestCase
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Synthetic SQLite fixtures only; never launches contact, messenger or notification UI. */
class DevicePersistenceTest : AndroidTestCase() {
    private val databaseName = "independent-device-test.db"
    private val date = LocalDate.of(2030, 6, 15)
    private val due = date.atTime(9, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private var clock = 0L
    private lateinit var repo: SQLiteRepository
    private fun open() = SQLiteRepository(context, databaseName, { clock }, ZoneId.of("UTC"))
    override fun setUp() { super.setUp(); context.deleteDatabase(databaseName); clock = due - 1; repo = open() }
    override fun tearDown() { repo.close(); context.deleteDatabase(databaseName); super.tearDown() }
    private fun reopen() { repo.close(); repo = open() }
    private fun fixture(): Pair<Client, Appointment> {
        val client = Client(firstName = "Synthetic", phone = "000123", reminder = ReminderPreferences(
            enabled = true, recipient = "synthetic-only", leadMinutes = 60, template = "Synthetic [час]"))
        repo.saveClient(client)
        val appointment = repo.saveAppointment(Appointment(date = date, startMinute = 600, endMinute = 630,
            clientId = client.id, status = AppointmentStatus.PLANNED))
        return client to appointment
    }

    fun testDueReadPersistsManualActionWithoutAppointmentEditOrNotificationPermission() {
        val (_, appointment) = fixture()
        val before = repo.reminders().single()
        assertEquals(ReminderState.SCHEDULED, before.state)
        clock = due
        val after = repo.reminders().single()
        assertEquals(before.copy(state = ReminderState.NEEDS_ACTION), after)
        assertEquals(appointment, repo.appointments().single())
        assertTrue(DeviceRules.alarmIds(listOf(after), false).isEmpty())
        assertEquals(after, DeviceRules.actionable(after.id, repo.reminders()))
        reopen()
        assertEquals(after, repo.reminders().single())
    }

    fun testReplacedIdCannotOpenOrMutateNewRowAndDisableRemovesSchedules() {
        val (client, appointment) = fixture()
        val old = repo.reminders().single()
        repo.saveAppointment(appointment.copy(startMinute = 660, endMinute = 690))
        val fresh = repo.reminders().single()
        assertFalse(old.id == fresh.id)
        assertEquals(setOf(old.id), DeviceRules.staleIds(setOf(old.id), DeviceRules.alarmIds(listOf(fresh), true)))
        repo.setReminderState(old.id, ReminderState.HANDED_OFF)
        assertEquals(fresh, repo.reminders().single())
        assertNull(DeviceRules.actionable(old.id, repo.reminders()))
        repo.saveClient(client.copy(reminder = client.reminder.copy(enabled = false)))
        assertTrue(repo.reminders().isEmpty())
        assertEquals(setOf(fresh.id), DeviceRules.staleIds(setOf(fresh.id), DeviceRules.alarmIds(repo.reminders(), true)))
        reopen(); assertTrue(repo.reminders().isEmpty())
    }

    fun testErrorRetryHandoffAndCancellationRemainDistinctAcrossReopenAndClockReads() {
        fixture(); clock = due
        val row = repo.reminders().single()
        repo.setReminderState(row.id, ReminderState.ERROR)
        reopen()
        val error = repo.reminders().single()
        assertEquals(ReminderState.ERROR, error.state)
        assertEquals(error, DeviceRules.actionable(row.id, listOf(error)))
        repo.setReminderState(row.id, ReminderState.HANDED_OFF)
        repo.setReminderState(row.id, ReminderState.ERROR)
        reopen(); assertEquals(ReminderState.HANDED_OFF, repo.reminders().single().state)
        assertNull(DeviceRules.actionable(row.id, repo.reminders()))
        val appointment = repo.appointments().single()
        repo.saveAppointment(appointment.copy(startMinute = 660, endMinute = 690))
        val replacement = repo.reminders().single()
        repo.setReminderState(replacement.id, ReminderState.CANCELLED)
        clock = due + 3600000L
        reopen(); assertEquals(ReminderState.CANCELLED, repo.reminders().single().state)
        assertNull(DeviceRules.actionable(replacement.id, repo.reminders()))
    }

    fun testAppointmentCancellationAndDeleteInvalidateRetainedOsIds() {
        val (_, appointment) = fixture(); val first = repo.reminders().single()
        repo.saveAppointment(appointment.copy(status = AppointmentStatus.CANCELLED))
        assertEquals(setOf(first.id), DeviceRules.staleIds(setOf(first.id), DeviceRules.alarmIds(repo.reminders(), true)))
        repo.saveAppointment(appointment)
        val next = repo.reminders().single()
        repo.deleteAppointment(appointment.id)
        reopen()
        assertTrue(repo.reminders().isEmpty())
        assertEquals(setOf(next.id), DeviceRules.staleIds(setOf(next.id), emptySet()))
    }
}
