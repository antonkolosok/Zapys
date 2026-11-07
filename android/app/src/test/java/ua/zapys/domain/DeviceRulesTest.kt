package ua.zapys.domain
import org.junit.Test
import org.junit.Assert.*
class DeviceRulesTest {
 @Test fun enabledReminderRequiresAdvanceLead() {
  val client=Client(firstName="A",phone="0671234567",reminder=ReminderPreferences(enabled=true,recipient="123",leadMinutes=0))
  try { Rules.validateClient(client); fail("Enabled zero lead must be rejected") } catch(_:ValidationException) {}
  Rules.validateClient(client.copy(reminder=client.reminder.copy(leadMinutes=1)))
 }
 private val r=Reminder(id="r",appointmentId="a",scheduledAt=100,text="literal $1",channel=Channel.VIBER,recipient="123")
 @Test fun dueBecomesActionAtBoundaryButTerminalDoesNotRevive() {
  assertEquals(ReminderState.SCHEDULED,DeviceRules.reconcile(r,99).state)
  assertEquals(ReminderState.NEEDS_ACTION,DeviceRules.reconcile(r,100).state)
  assertEquals(ReminderState.CANCELLED,DeviceRules.reconcile(r.copy(state=ReminderState.CANCELLED),100).state)
 }
 @Test fun onlyCurrentActionWithRecipientCanOpen() {
  assertNull(DeviceRules.actionable("old",listOf(r.copy(state=ReminderState.NEEDS_ACTION))))
  assertNull(DeviceRules.actionable("r",listOf(r)))
  assertNull(DeviceRules.actionable("r",listOf(r.copy(state=ReminderState.ERROR,recipient=" "))))
  assertEquals(r.copy(state=ReminderState.ERROR),DeviceRules.actionable("r",listOf(r.copy(state=ReminderState.ERROR))))
 }
 @Test fun deniedPermissionCancelsAllRetainedAlarms() {
  assertEquals(setOf("r"),DeviceRules.alarmIds(listOf(r),true))
  assertEquals(setOf("old","r"),DeviceRules.staleIds(setOf("old","r"),DeviceRules.alarmIds(listOf(r),false)))
 }
}
