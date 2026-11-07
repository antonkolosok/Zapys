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
 @Test fun onlyCurrentActionCanOpenAndRecipientIsChosenInMessenger() {
  assertNull(DeviceRules.actionable("old",listOf(r.copy(state=ReminderState.NEEDS_ACTION))))
  assertNull(DeviceRules.actionable("r",listOf(r)))
  val blank=r.copy(state=ReminderState.ERROR,recipient=" ")
  assertEquals(blank,DeviceRules.actionable("r",listOf(blank)))
  assertEquals(r.copy(state=ReminderState.ERROR),DeviceRules.actionable("r",listOf(r.copy(state=ReminderState.ERROR))))
 }
 @Test fun enabledReminderWithBlankRecipientValidatesAndBecomesDueWithoutRouting() {
  val c=Client(firstName="Synthetic",phone="0670000703",reminder=ReminderPreferences(enabled=true,recipient=""))
  Rules.validateClient(c)
  val date=java.time.LocalDate.of(2031,1,2)
  val starts=date.atStartOfDay(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()+600*60000L
  val a=Appointment(date=date,startMinute=600,endMinute=630,clientId=c.id,status=AppointmentStatus.PLANNED)
  val row=Rules.reminder(a,c,starts-1,java.time.ZoneId.of("UTC"))!!
  assertEquals("",row.recipient); assertEquals(ReminderState.NEEDS_ACTION,row.state)
  assertEquals(row,DeviceRules.actionableAt(row.id,listOf(row),starts-1))
  assertNull(Rules.reminder(a,c,starts,java.time.ZoneId.of("UTC")))
 }
 @Test fun blankRecipientKeepsTimeStateAndStaleIdGates() {
  for(recipient in listOf("","legacy recipient")) {
   val due=r.copy(state=ReminderState.NEEDS_ACTION,recipient=recipient)
   assertNull(DeviceRules.actionableAt("old",listOf(due),100))
   assertNull(DeviceRules.actionableAt("r",listOf(due),99))
   assertEquals(due,DeviceRules.actionableAt("r",listOf(due),100))
   assertEquals(setOf("r"),DeviceRules.dueIds(listOf(due),true,100))
   assertTrue(DeviceRules.dueIds(listOf(due),false,100).isEmpty())
   for(state in listOf(ReminderState.SCHEDULED,ReminderState.CANCELLED,ReminderState.HANDED_OFF))
    assertNull(DeviceRules.actionableAt("r",listOf(due.copy(state=state)),100))
  }
  assertFalse(DeviceRules.canLaunch(false,2,2)); assertFalse(DeviceRules.canLaunch(true,1,2)); assertTrue(DeviceRules.canLaunch(true,2,2))
 }
 @Test fun deniedPermissionCancelsAllRetainedAlarms() {
  assertEquals(setOf("r"),DeviceRules.alarmIds(listOf(r),true))
  assertEquals(setOf("old","r"),DeviceRules.staleIds(setOf("old","r"),DeviceRules.alarmIds(listOf(r),false)))
 }
}
