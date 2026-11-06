package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Independent acceptance cases derived from docs/REQUIREMENTS.md. */
class IndependentRulesTest {
    private val date = LocalDate.of(2030, 6, 15)
    private fun draft(start: Int = 600, end: Int = 630) =
        Appointment(date = date, startMinute = start, endMinute = end)
    private fun invalid(block: () -> Unit) = assertThrows(ValidationException::class.java, block)

    @Test fun clientNameAndPhoneAreRequiredForManualEntry() {
        val client = Client(firstName = "Олена", phone = "+380501234567")
        Rules.validateClient(client)
        invalid { Rules.validateClient(client.copy(firstName = " \t")) }
        invalid { Rules.validateClient(client.copy(phone = " \t")) }
    }

    @Test fun serviceNameAndPositiveDurationAreValidated() {
        val service = Service(name = "Послуга")
        Rules.validateService(service)
        Rules.validateService(service.copy(durationMinutes = 1))
        invalid { Rules.validateService(service.copy(name = " ")) }
        invalid { Rules.validateService(service.copy(durationMinutes = 0)) }
        invalid { Rules.validateService(service.copy(durationMinutes = -1)) }
    }

    @Test fun catalogueAndSnapshotBothRejectNegativePrices() {
        val service = Service(name = "Послуга", priceMinor = -1)
        invalid { Rules.validateService(service) }
        invalid { Rules.validateAppointment(draft().copy(services = listOf(service.snapshot())), emptyList()) }
    }

    @Test fun noteAndTemplateLimitsAcceptExactlyTwoThousandCharacters() {
        val limit = "а".repeat(2000)
        val client = Client(firstName = "Олена", phone = "123", note = limit,
            reminder = ReminderPreferences(template = limit))
        Rules.validateClient(client)
        Rules.validateService(Service(name = "Послуга", note = limit))
        Rules.validateAppointment(draft().copy(note = limit), emptyList())
        invalid { Rules.validateClient(client.copy(note = limit + "а")) }
        invalid { Rules.validateClient(client.copy(reminder = client.reminder.copy(template = limit + "а"))) }
        invalid { Rules.validateService(Service(name = "Послуга", note = limit + "а")) }
        invalid { Rules.validateAppointment(draft().copy(note = limit + "а"), emptyList()) }
    }

    @Test fun reminderAtAppointmentStartIsAlreadyTooLate() {
        val zone = ZoneId.of("UTC")
        val client = Client(firstName = "Олена", phone = "123",
            reminder = ReminderPreferences(enabled = true, recipient = "123", leadMinutes = 60))
        val a = draft().copy(clientId = client.id, status = AppointmentStatus.PLANNED)
        val starts = date.atStartOfDay(zone).plusMinutes(600).toInstant().toEpochMilli()
        assertNull(Rules.reminder(a, client, starts, zone))
        assertNull(Rules.reminder(a, client, starts + 1, zone))
    }

    @Test fun reminderDueNowNeedsManualActionInsteadOfClaimingDelivery() {
        val zone = ZoneId.of("UTC")
        val client = Client(firstName = "Олена", phone = "123",
            reminder = ReminderPreferences(enabled = true, recipient = "123", leadMinutes = 60))
        val a = draft().copy(clientId = client.id, status = AppointmentStatus.PLANNED)
        val due = date.atStartOfDay(zone).plusMinutes(540).toInstant().toEpochMilli()
        assertEquals(ReminderState.SCHEDULED, Rules.reminder(a, client, due - 1, zone)!!.state)
        val reminder = Rules.reminder(a, client, due, zone)!!
        assertEquals(due, reminder.scheduledAt)
        assertEquals(ReminderState.NEEDS_ACTION, reminder.state)
    }

    @Test fun overlapIsSymmetricForContainedAndIdenticalIntervals() {
        val outer = draft(540, 720)
        val inner = draft(600, 630)
        assertTrue(Rules.overlaps(outer, inner))
        assertTrue(Rules.overlaps(inner, outer))
        assertTrue(Rules.overlaps(inner, inner.copy()))
    }

    @Test fun adjacencyAndDifferentDatesNeverConflict() {
        val a = draft()
        assertFalse(Rules.overlaps(a, draft(570, 600)))
        assertFalse(Rules.overlaps(a, draft(630, 660)))
        assertFalse(Rules.overlaps(a, draft().copy(date = date.plusDays(1))))
        assertTrue(Rules.conflicts(a, listOf(a.copy(), draft().copy(status = AppointmentStatus.CANCELLED))).isEmpty())
    }

    @Test fun everyNoncancelledStatusStillParticipatesInConflicts() {
        val candidate = draft()
        for (status in AppointmentStatus.entries.filter { it != AppointmentStatus.CANCELLED }) {
            assertEquals("status=$status", 1, Rules.conflicts(candidate, listOf(draft(610, 640).copy(status = status))).size)
        }
    }

    @Test fun finalMinuteIsValidAndMidnightIsRejected() {
        Rules.validateAppointment(draft(1438, 1439), emptyList())
        Rules.validateAppointment(draft(0, 1), emptyList())
        invalid { Rules.validateAppointment(draft(1439, 1440), emptyList()) }
        invalid { Rules.validateAppointment(draft(-1, 30), emptyList()) }
        invalid { Rules.validateAppointment(draft(600, 599), emptyList()) }
    }

    @Test fun lateCalendarTapCreatesAFullValidThirtyMinuteDraft() {
        for (minute in listOf(1410, 1425, 1430, 1439)) {
            val a = Rules.calendarDraft(date, minute)
            assertEquals(AppointmentStatus.DRAFT, a.status)
            assertEquals(30, a.endMinute - a.startMinute)
            assertTrue(a.startMinute >= 0)
            assertTrue(a.endMinute <= 1439)
            assertNull(a.clientId)
            assertTrue(a.services.isEmpty())
            Rules.validateAppointment(a, emptyList())
        }
    }

    @Test fun exactlyFiveOrderedServicesAreValidAndSixAreRejected() {
        val services = (1..5).map { Service(name = "Service $it").snapshot() }
        val a = draft().copy(services = services)
        Rules.validateAppointment(a, emptyList())
        assertEquals((1..5).map { "Service $it" }, a.services.map { it.name })
        invalid { Rules.validateAppointment(a.copy(services = services + services.first()), emptyList()) }
    }

    @Test fun plannedAppointmentRequiresAnExistingClientButNoServices() {
        val client = Client(firstName = "Олена", phone = "+380501234567")
        val a = draft().copy(clientId = client.id, status = AppointmentStatus.PLANNED)
        Rules.validateAppointment(a, listOf(client))
        invalid { Rules.validateAppointment(a, emptyList()) }
    }

    @Test fun receivedMoneyMayBeUnknownOrZeroButNeverNegative() {
        Rules.validateAppointment(draft().copy(receivedMinor = null), emptyList())
        Rules.validateAppointment(draft().copy(receivedMinor = 0), emptyList())
        invalid { Rules.validateAppointment(draft().copy(receivedMinor = -1), emptyList()) }
    }

    @Test fun moneyParsingPreservesCentsAndRejectsOverflow() {
        assertEquals(1L, Rules.parseMoney("0.01"))
        assertEquals(120L, Rules.parseMoney("1,2"))
        assertEquals(100L, Rules.parseMoney(" 1.00 "))
        invalid { Rules.parseMoney("92233720368547758.08") }
        invalid { Rules.parseMoney("NaN") }
    }

    @Test fun catalogueEditsDoNotMutateAnExistingSnapshot() {
        val service = Service(name = "Перший", durationMinutes = 15, priceMinor = null)
        val snapshot = service.snapshot()
        service.copy(name = "Інший", durationMinutes = 60, priceMinor = 0)
        assertEquals("Перший", snapshot.name)
        assertEquals(15, snapshot.durationMinutes)
        assertNull(snapshot.priceMinor)
        assertEquals(0L, service.copy(priceMinor = 0).snapshot().priceMinor)
    }

    @Test fun durationSuggestionUsesAllServicesAndPreservesManualEnd() {
        val a = draft().copy(services = listOf(
            Service(name = "A", durationMinutes = 20).snapshot(),
            Service(name = "B", durationMinutes = 45).snapshot()
        ))
        assertEquals(665, Rules.suggestEnd(a))
        assertEquals(630, Rules.suggestEnd(a.copy(manualEnd = true)))
        assertEquals(630, Rules.suggestEnd(a.copy(services = a.services + Service(name = "Unknown").snapshot())))
        assertEquals(630, Rules.suggestEnd(a.copy(services = emptyList())))
    }

    @Test fun durationSuggestionCannotCrossTheDayBoundary() {
        val a = draft(1400, 1415).copy(services = listOf(Service(name = "Long", durationMinutes = 120).snapshot()))
        assertTrue(Rules.suggestEnd(a) <= 1439)
        assertTrue(Rules.suggestEnd(a) > a.startMinute)
    }

    @Test fun repeatCopiesClientAndOrderedSnapshotsWithFreshState() {
        val original = draft().copy(
            clientId = "client-id", services = listOf(Service(name = "A").snapshot(), Service(name = "B", priceMinor = 0).snapshot()),
            status = AppointmentStatus.NO_SHOW, paid = true, receivedMinor = 2300, manualEnd = true
        )
        val repeated = Rules.repeat(original, date.plusDays(7), 900)
        assertNotEquals(original.id, repeated.id)
        assertEquals(original.clientId, repeated.clientId)
        assertEquals(original.services, repeated.services)
        assertEquals(date.plusDays(7), repeated.date)
        assertEquals(900, repeated.startMinute)
        assertEquals(AppointmentStatus.DRAFT, repeated.status)
        assertFalse(repeated.paid)
        assertNull(repeated.receivedMinor)
        assertFalse(repeated.manualEnd)
    }

    @Test fun templatesSubstituteRepeatedVariablesLiterallyWithoutExpansion() {
        val client = Client(firstName = "[дата] $1 \\name", phone = "123")
        val a = draft().copy(services = listOf(Service(name = "A [час]").snapshot(), Service(name = "B").snapshot()))
        val rendered = Rules.renderTemplate("[ім'я]|[ім'я]|[дата]|[час]|[послуги]|[невідомо]", a, client)
        assertTrue(rendered.startsWith("${client.firstName}|${client.firstName}|15.06.2030|10:00|"))
        assertTrue(rendered.contains("A [час]"))
        assertTrue(rendered.contains("B"))
        assertTrue(rendered.endsWith("|[невідомо]"))
    }

    @Test fun disabledDraftAndCancelledAppointmentsNeverProduceReminder() {
        val client = Client(firstName = "Олена", phone = "123", reminder = ReminderPreferences(enabled = true, recipient = "123"))
        val a = draft().copy(clientId = client.id)
        assertNull(Rules.reminder(a, client, 0))
        assertNull(Rules.reminder(a.copy(status = AppointmentStatus.CANCELLED), client, 0))
        assertNull(Rules.reminder(a.copy(status = AppointmentStatus.PLANNED), client.copy(reminder = client.reminder.copy(enabled = false)), 0))
    }
}
