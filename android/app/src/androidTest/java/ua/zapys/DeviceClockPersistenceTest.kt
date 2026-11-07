package ua.zapys
import android.test.AndroidTestCase
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import java.time.LocalDate
import java.time.ZoneId

/** Authored UNRUN; synthetic databases only, no contacts/messages/notifications. */
class DeviceClockPersistenceTest:AndroidTestCase() {
 fun testTimezoneChangeAndUnchangedSavesKeepBothTerminalIdentities() {
  for(state in listOf(ReminderState.HANDED_OFF,ReminderState.CANCELLED)) {
   val db="clock-terminal-${state.name}.db"; context.deleteDatabase(db)
   val client=Client(firstName="Synthetic",phone="0671234567",reminder=ReminderPreferences(enabled=true,recipient="synthetic",leadMinutes=60))
   val appointment=Appointment(date=LocalDate.of(2030,1,2),startMinute=600,endMinute=630,clientId=client.id,status=AppointmentStatus.PLANNED)
   var old:Reminder?=null
   try {
    SQLiteRepository(context,db,{ 0L },ZoneId.of("UTC")).use { r -> r.saveClient(client); r.saveAppointment(appointment); old=r.reminders().single(); r.setReminderState(old!!.id,state) }
    SQLiteRepository(context,db,{ 0L },ZoneId.of("Europe/Kyiv")).use { r ->
     // Write before the first queue read also has to rebase safely.
     r.saveAppointment(appointment); r.saveClient(client)
     val actual=r.reminders().single()
     assertEquals(old!!.id,actual.id); assertEquals(state,actual.state)
     assertFalse(old!!.scheduledAt==actual.scheduledAt)
    }
    SQLiteRepository(context,db,{ 0L },ZoneId.of("Europe/Kyiv")).use { r -> assertEquals(old!!.id,r.reminders().single().id); assertEquals(state,r.reminders().single().state) }
   } finally { context.deleteDatabase(db) }
  }
 }
 fun testClockBackwardsPreservesErrorAndRestoresFutureEligibility() {
  val db="clock-backwards.db"; context.deleteDatabase(db)
  var clock=0L
  try { SQLiteRepository(context,db,{ clock },ZoneId.of("UTC")).use { r ->
   val client=Client(firstName="Synthetic",phone="0671234567",reminder=ReminderPreferences(enabled=true,recipient="synthetic",leadMinutes=60))
   r.saveClient(client); r.saveAppointment(Appointment(date=LocalDate.of(2030,1,2),startMinute=600,endMinute=630,clientId=client.id,status=AppointmentStatus.PLANNED))
   val row=r.reminders().single(); clock=row.scheduledAt
   assertEquals(ReminderState.NEEDS_ACTION,r.reminders().single().state)
   r.setReminderState(row.id,ReminderState.ERROR); clock--
   val before=r.reminders()
   assertEquals(ReminderState.ERROR,before.single().state)
   assertNull(DeviceRules.actionableAt(row.id,before,clock))
   assertEquals(setOf(row.id),DeviceRules.futureAlarmIds(before,true,clock))
   assertTrue(DeviceRules.dueIds(before,true,clock).isEmpty())
   clock++; assertEquals(setOf(row.id),DeviceRules.dueIds(r.reminders(),true,clock))
  } } finally { context.deleteDatabase(db) }
 }
}
