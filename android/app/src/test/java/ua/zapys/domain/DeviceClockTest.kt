package ua.zapys.domain
import org.junit.Test
import org.junit.Assert.*
class DeviceClockTest {
 private val row=Reminder(id="r",appointmentId="a",scheduledAt=100,text="same",channel=Channel.VIBER,recipient="123",state=ReminderState.NEEDS_ACTION)
 @Test fun backwardsClockSuppressesActionAndRestoresAlarmThenCrossesDueAgain() {
  for(state in listOf(ReminderState.NEEDS_ACTION,ReminderState.ERROR)) {
   val r=row.copy(state=state)
   assertNotNull(DeviceRules.actionableAt("r",listOf(r),100))
   assertNull(DeviceRules.actionableAt("r",listOf(r),99))
   assertEquals(setOf("r"),DeviceRules.futureAlarmIds(listOf(r),true,99))
   assertTrue(DeviceRules.dueIds(listOf(r),true,99).isEmpty())
   assertEquals(setOf("r"),DeviceRules.dueIds(listOf(r),true,100))
   assertEquals(state,DeviceRules.reconcile(r,99).state)
  }
 }
 @Test fun rebootRestoresDueDisplayButOrdinaryForegroundPreservesDismissal() {
  assertEquals(setOf("r"),DeviceRules.displayedLedger(setOf("r"),false))
  assertTrue(DeviceRules.displayedLedger(setOf("r"),true).isEmpty())
 }
 @Test fun timezoneRebaseKeepsTerminalIdentityAndOutcome() {
  for(state in listOf(ReminderState.HANDED_OFF,ReminderState.CANCELLED)) {
   val r=row.copy(state=state)
   val changed=DeviceRules.rebaseTerminal(r,200)
   assertEquals(r.copy(scheduledAt=200),changed)
   assertNull(DeviceRules.actionableAt("r",listOf(changed),300))
   assertTrue(DeviceRules.futureAlarmIds(listOf(changed),true,0).isEmpty())
  }
 }
 @Test fun stoppedOrObsoleteLaunchIsInert() {
  assertFalse(DeviceRules.canLaunch(false,1,1))
  assertFalse(DeviceRules.canLaunch(true,1,2))
  assertTrue(DeviceRules.canLaunch(true,2,2))
 }
}
