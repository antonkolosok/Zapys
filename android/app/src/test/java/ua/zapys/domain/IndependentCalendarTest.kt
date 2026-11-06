package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/** Independent boundary/oracle cases against the production calendar geometry. */
class IndependentCalendarTest {
    private val day = LocalDate.of(2026, 12, 31)
    private fun event(id: String, start: Int, end: Int, date: LocalDate = day) =
        Appointment(id = id, date = date, startMinute = start, endMinute = end)

    @Test fun mondayFirstWeekCrossesYearAndEachHeaderRetainsItsDate() {
        val week = CalendarGeometry.weekDates(day)
        assertEquals(7, week.size)
        assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
        assertEquals(LocalDate.of(2026, 12, 28), week.first())
        assertEquals(LocalDate.of(2027, 1, 3), week.last())
        week.forEachIndexed { index, date ->
            assertEquals(week.first().plusDays(index.toLong()), date)
            assertEquals(week, CalendarGeometry.weekDates(date))
        }
        assertEquals(LocalDate.of(2027, 1, 7), CalendarGeometry.navigate(day, 1))
        assertEquals(day, CalendarGeometry.navigate(CalendarGeometry.navigate(day, -1), 1))
        assertEquals(LocalDate.of(2024, 3, 4), CalendarGeometry.navigate(LocalDate.of(2024, 2, 26), 1))
    }

    @Test fun tappedDraftSnapsQuarterHourAndClampsLastHalfHourWithoutChangingDate() {
        for ((tap, start) in listOf(-10f to 0, 601f to 600, 609f to 615, 1439f to 1409, 2000f to 1409)) {
            val draft = CalendarGeometry.draft(day, tap, "tap")
            assertEquals(day, draft.date); assertEquals("tap", draft.id)
            assertEquals(start, draft.startMinute); assertEquals(start + 30, draft.endMinute)
            assertEquals(AppointmentStatus.DRAFT, draft.status)
            assertNull(draft.clientId); assertTrue(draft.services.isEmpty())
            Rules.validateAppointment(draft, emptyList())
        }
    }

    @Test fun chainedOverlapUsesPeakConcurrencyRatherThanConnectedGroupSize() {
        val a = event("a", 600, 630); val b = event("b", 620, 650); val c = event("c", 640, 670)
        val rows = CalendarGeometry.lanes(listOf(c, a, b)).associateBy { it.appointment.id }
        assertEquals(setOf("a", "b", "c"), rows.keys)
        assertTrue(rows.values.all { it.laneCount == 2 })
        assertEquals(rows.getValue("a").lane, rows.getValue("c").lane)
        assertNotEquals(rows.getValue("a").lane, rows.getValue("b").lane)
    }

    @Test fun adjacentOtherDateAndCancelledRecordsDoNotConsumeActiveLanes() {
        val a = event("a", 600, 630); val adjacent = event("b", 630, 660)
        val cancelled = event("cancelled", 590, 680).copy(status = AppointmentStatus.CANCELLED)
        val tomorrow = event("tomorrow", 600, 630, day.plusDays(1))
        val rows = CalendarGeometry.lanes(listOf(cancelled, tomorrow, adjacent, a))
        assertEquals(setOf("a", "b", "tomorrow"), rows.map { it.appointment.id }.toSet())
        assertTrue(rows.all { it.lane == 0 && it.laneCount == 1 })
        assertTrue(Rules.conflicts(a, listOf(a, adjacent, cancelled, tomorrow)).isEmpty())
    }

    @Test fun tripleConcurrencyGetsThreeDistinctLanesAndFreshGroupResets() {
        val records = listOf(event("a", 600, 660), event("b", 615, 645), event("c", 630, 675), event("d", 675, 690))
        val rows = CalendarGeometry.lanes(records).associateBy { it.appointment.id }
        assertEquals(3, listOf("a", "b", "c").map { rows.getValue(it).lane }.toSet().size)
        assertTrue(listOf("a", "b", "c").all { rows.getValue(it).laneCount == 3 })
        assertEquals(1, rows.getValue("d").laneCount)
    }

    @Test fun shortEventsKeepActualDurationAndTimelineHasFullDayScale() {
        assertEquals(42f, CalendarGeometry.GUTTER, 0.001f)
        assertEquals(72f, CalendarGeometry.height(600, 660), 0.001f)
        assertEquals(36f, CalendarGeometry.height(600, 630), 0.001f)
        assertEquals(1.2f, CalendarGeometry.height(1438, 1439), 0.001f)
        assertEquals(0f, CalendarGeometry.top(0), 0.001f)
        assertEquals(648f, CalendarGeometry.top(540), 0.001f)
        assertEquals(1726.8f, CalendarGeometry.top(1439), 0.001f)
    }

    @Test fun resizingReturnsIndependentPreviewWithSnappingAndBoundaryClamps() {
        val original = event("persisted", 607, 667).copy(note = "retain", paid = true)
        val preview = CalendarGeometry.resize(original, 683f)
        assertEquals(690, preview.endMinute); assertTrue(preview.manualEnd)
        assertEquals(original.copy(endMinute = 690, manualEnd = true), preview)
        assertEquals(667, original.endMinute); assertFalse(original.manualEnd)
        assertEquals(608, CalendarGeometry.resize(original, 0f).endMinute)
        assertEquals(1439, CalendarGeometry.resize(original, 2000f).endMinute)
        val late = event("late", 1438, 1439)
        assertEquals(1439, CalendarGeometry.resize(late, 1430f).endMinute)
        // Discarding a candidate is cancellation: no mutable state is shared with the original.
        assertEquals(event("persisted", 607, 667).copy(note = "retain", paid = true), original)
    }

    @Test fun unsetMiddleLabelDoesNotCompressSecondServiceAnchor() {
        for (centers in listOf(listOf(16f, 36f, 56f), listOf(12f, 30f, 48f))) {
            val stops = CalendarGeometry.gradient(listOf(0x112233, null, 0x445566), centers)
            assertEquals(listOf(0x112233, 0x445566), stops.map { it.color })
            assertEquals(listOf(centers[0], centers[2]), stops.map { it.center })
            // A 1-minute or 30-minute block clips rows but does not move logical shader anchors.
            assertTrue(stops.last().center > CalendarGeometry.height(600, 630))
        }
    }

    @Test fun zeroOneAndFiveColorsPreserveContributionOrder() {
        assertTrue(CalendarGeometry.gradient(listOf(null, null), listOf(10f, 30f)).isEmpty())
        val single = CalendarGeometry.gradient(listOf(null, 7, null), listOf(10f, 30f, 50f))
        assertEquals(listOf(7), single.map { it.color }); assertEquals(listOf(30f), single.map { it.center })
        val ordered = CalendarGeometry.gradient(listOf(5, 4, 3, 2, 1, 0), listOf(10f, 30f, 50f, 70f, 90f, 110f))
        assertEquals(listOf(5, 4, 3, 2, 1, 0), ordered.map { it.color })
        assertEquals(listOf(10f, 30f, 50f, 70f, 90f, 110f), ordered.map { it.center })
    }
}
