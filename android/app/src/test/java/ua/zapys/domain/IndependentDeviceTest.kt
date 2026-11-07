package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test

/** Device policy tests: no Android framework, permissions, alarms or external composers. */
class IndependentDeviceTest {
    private fun row(id: String = "current", state: ReminderState = ReminderState.SCHEDULED) =
        Reminder(id, "visit", 1000L, "Synthetic preview", Channel.TELEGRAM, "synthetic-recipient", state)

    @Test fun clockBoundaryPromotesExistingRowWithoutReplacingItsIdentityOrPreview() {
        val pending = row()
        assertEquals(pending, DeviceRules.reconcile(pending, 999L))
        assertEquals(pending.copy(state = ReminderState.NEEDS_ACTION), DeviceRules.reconcile(pending, 1000L))
        assertEquals(pending.copy(state = ReminderState.NEEDS_ACTION), DeviceRules.reconcile(pending, 1001L))
    }

    @Test fun clockReconciliationNeverRevivesOutcomesOrClearsAnError() {
        ReminderState.entries.filter { it != ReminderState.SCHEDULED }.forEach { state ->
            val original = row(state = state)
            assertEquals("state=$state", original, DeviceRules.reconcile(original, Long.MAX_VALUE))
        }
    }

    @Test fun deniedNotificationsRemoveOsSchedulesButKeepDueQueueActionable() {
        val due = DeviceRules.reconcile(row(), 1000L)
        val rows = listOf(due, row("future").copy(scheduledAt = 2000L))
        assertTrue(DeviceRules.alarmIds(rows, false).isEmpty())
        assertEquals(due, DeviceRules.actionable(due.id, rows))
        assertEquals(setOf("future"), DeviceRules.alarmIds(rows, true))
    }

    @Test fun staleSchedulesAreCancelledAfterReplacementDisableAndDeletion() {
        assertEquals(setOf("old"), DeviceRules.staleIds(setOf("old", "unchanged"), setOf("new", "unchanged")))
        assertEquals(setOf("old", "unchanged"), DeviceRules.staleIds(setOf("old", "unchanged"), emptySet()))
        assertTrue(DeviceRules.staleIds(emptySet(), setOf("new")).isEmpty())
    }

    @Test fun explicitOpenRejectsStaleIdAndNonactionableStates() {
        val replacement = row("replacement", ReminderState.NEEDS_ACTION)
        assertNull(DeviceRules.actionable("old", listOf(replacement)))
        listOf(ReminderState.SCHEDULED, ReminderState.HANDED_OFF, ReminderState.CANCELLED).forEach { state ->
            assertNull("state=$state", DeviceRules.actionable("current", listOf(row(state = state))))
        }
        val blank = row(state = ReminderState.NEEDS_ACTION).copy(recipient = " \t")
        assertEquals(blank, DeviceRules.actionable("current", listOf(blank)))
    }

    @Test fun launchErrorCanRetryWhileHandoffAndCancellationAreTerminal() {
        val failed = row(state = ReminderState.ERROR)
        assertEquals(failed, DeviceRules.actionable(failed.id, listOf(failed)))
        assertTrue(DeviceRules.alarmIds(listOf(failed), true).isEmpty())
        listOf(ReminderState.HANDED_OFF, ReminderState.CANCELLED).forEach { outcome ->
            val terminal = failed.copy(state = outcome)
            assertNull(DeviceRules.actionable(terminal.id, listOf(terminal)))
            assertEquals(terminal, DeviceRules.reconcile(terminal, 2000L))
        }
    }
}
