package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DomainRulesTest {
    private val date = LocalDate.of(2030, 1, 2)
    private fun visit(start: Int = 600, end: Int = 630) = Appointment(date = date, startMinute = start, endMinute = end)
    @Test fun adjacentIntervalsAreAllowed() {
        assertFalse(Rules.overlaps(visit(), visit(630, 660)))
        assertTrue(Rules.overlaps(visit(), visit(629, 660)))
    }
    @Test fun cancelledAndSelfAreNotConflicts() {
        val a = visit()
        assertTrue(Rules.conflicts(a, listOf(a, visit().copy(status = AppointmentStatus.CANCELLED))).isEmpty())
    }
    @Test fun invalidEndAndOvernightAreRejected() {
        assertThrows(ValidationException::class.java) { Rules.validateAppointment(visit(600, 600), emptyList()) }
        assertThrows(ValidationException::class.java) { Rules.validateAppointment(visit(1430, 1440), emptyList()) }
        assertEquals(1439, Rules.calendarDraft(date, 1430).endMinute)
        assertEquals(30, Rules.calendarDraft(date, 1430).let { it.endMinute - it.startMinute })
    }
    @Test fun completedFormNeedsClientButEmptyDraftPersists() {
        Rules.validateAppointment(visit(), emptyList())
        assertThrows(ValidationException::class.java) { Rules.validateAppointment(visit().copy(status = AppointmentStatus.PLANNED), emptyList()) }
    }
    @Test fun sixthServiceIsRejectedAndNullPriceSurvivesSnapshot() {
        val service = Service(name = "Послуга")
        assertNull(service.snapshot().priceMinor)
        assertEquals(0L, service.copy(priceMinor = 0).snapshot().priceMinor)
        assertThrows(ValidationException::class.java) { Rules.validateAppointment(visit().copy(services = List(6) { service.snapshot() }), emptyList()) }
    }
    @Test fun moneyUsesExactMinorUnits() {
        assertNull(Rules.parseMoney(""))
        assertEquals(12345L, Rules.parseMoney("123,45"))
        assertEquals(0L, Rules.parseMoney("0"))
        assertThrows(ValidationException::class.java) { Rules.parseMoney("-1") }
        assertThrows(ValidationException::class.java) { Rules.parseMoney("1.001") }
    }
    @Test fun repeatGetsNewIdentityAndResetsState() {
        val old = visit().copy(paid = true, receivedMinor = 100, status = AppointmentStatus.COMPLETED, manualEnd = true)
        val repeated = Rules.repeat(old, date.plusDays(1), 720)
        assertNotEquals(old.id, repeated.id)
        assertEquals(AppointmentStatus.DRAFT, repeated.status)
        assertFalse(repeated.paid)
        assertNull(repeated.receivedMinor)
        assertFalse(repeated.manualEnd)
    }
    @Test fun templatesReplaceLiterallyWithoutRecursiveExpansion() {
        val client = Client(firstName = "[дата]", phone = "+380000000000")
        val text = Rules.renderTemplate("[ім'я] [дата] [час] [послуги]", visit(), client)
        assertEquals("[дата] 02.01.2030 10:00 ", text)
    }
    @Test fun oneReminderPerAppointmentAndNoPastOrCancelledReminder() {
        assertEquals(1, Limits.MAX_REMINDERS)
        val client = Client(firstName = "Оля", phone = "123", reminder = ReminderPreferences(enabled = true, recipient = "123"))
        val a = visit().copy(clientId = client.id, status = AppointmentStatus.PLANNED)
        assertNotNull(Rules.reminder(a, client, 0))
        assertNull(Rules.reminder(a.copy(status = AppointmentStatus.CANCELLED), client, 0))
        assertNull(Rules.reminder(a, client, Long.MAX_VALUE))
    }
    @Test fun durationSuggestionRespectsManualOverrideAndUnknownDurations() {
        val a = visit().copy(services = listOf(Service(name = "A", durationMinutes = 45).snapshot()))
        assertEquals(645, Rules.suggestEnd(a))
        assertEquals(630, Rules.suggestEnd(a.copy(manualEnd = true)))
        assertEquals(630, Rules.suggestEnd(a.copy(services = listOf(Service(name = "B").snapshot()))))
    }
}
