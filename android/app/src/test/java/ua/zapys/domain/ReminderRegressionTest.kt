package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReminderRegressionTest {
    private val zone = ZoneId.of("Europe/Kyiv")
    private val client = Client(firstName = "[дата]$1", phone = "123", reminder = ReminderPreferences(enabled = true, recipient = "123", leadMinutes = 0))
    private fun reminder(day: String, minute: Int): Reminder = Rules.reminder(Appointment(date = LocalDate.parse(day), startMinute = minute, endMinute = minute + 30, status = AppointmentStatus.PLANNED), client, 0, zone)!!
    @Test fun springTransitionKeepsTenOClockWallTime() {
        assertEquals(Instant.parse("2030-03-31T07:00:00Z").toEpochMilli(), reminder("2030-03-31", 600).scheduledAt)
    }
    @Test fun autumnTransitionKeepsTenOClockWallTime() {
        assertEquals(Instant.parse("2030-10-27T08:00:00Z").toEpochMilli(), reminder("2030-10-27", 600).scheduledAt)
    }
    @Test fun gapResolvesForwardAndOverlapUsesEarlierOffset() {
        assertEquals(Instant.parse("2030-03-31T01:30:00Z").toEpochMilli(), reminder("2030-03-31", 210).scheduledAt)
        assertEquals(Instant.parse("2030-10-27T00:30:00Z").toEpochMilli(), reminder("2030-10-27", 210).scheduledAt)
    }
    @Test fun bothApostrophesSubstituteNameLiterally() {
        val a = Appointment(date = LocalDate.of(2030, 1, 2), startMinute = 600, endMinute = 630)
        assertEquals("${client.firstName}|${client.firstName}", Rules.renderTemplate("[ім’я]|[ім'я]", a, client))
    }
}
