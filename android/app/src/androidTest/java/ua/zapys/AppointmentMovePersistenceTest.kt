package ua.zapys

import android.test.AndroidTestCase
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import java.time.LocalDate
import java.time.ZoneId

/** Real isolated SQLite transactions, including failure after the appointment update. */
class AppointmentMovePersistenceTest:AndroidTestCase() {
 private val database="task9-move.db"
 private lateinit var repo:SQLiteRepository
 private val date=LocalDate.of(2031,12,31)
 override fun setUp() { super.setUp(); context.deleteDatabase(database); repo=open() }
 override fun tearDown() { repo.close(); context.deleteDatabase(database); super.tearDown() }
 private fun open()=SQLiteRepository(context,database,{0L},ZoneId.of("UTC"))
 private fun reopen() { repo.close(); repo=open() }
 private fun sql(action:(SQLiteDatabase)->Unit) { SQLiteDatabase.openDatabase(context.getDatabasePath(database).path,null,SQLiteDatabase.OPEN_READWRITE).use(action) }
 private fun fixture():Appointment {
  val client=Client("move-client","Historical","0671234567",color=123,reminder=ReminderPreferences(enabled=true,recipient="",leadMinutes=60))
  repo.saveClient(client)
  val saved=repo.saveAppointment(Appointment("move-record",date,607,667,client.id,services=listOf(ServiceSnapshot("s","Snapshot",60,123,"service note",456)),note="original note",status=AppointmentStatus.PLANNED,paid=true,receivedMinor=999,manualEnd=true))
  repo.saveClient(client.copy(firstName="Current",phone="0501234567",color=789))
  return saved
 }
 fun testMoveReopensWithHistoricalSnapshotsAndReschedulesBlankRecipientReminder() {
  val original=fixture(); val before=repo.reminders().single()
  val saved=repo.moveAppointment(original,date.plusDays(1),637)
  reopen()
  assertEquals(original.copy(date=LocalDate.of(2032,1,1),startMinute=637,endMinute=697),saved)
  assertEquals(listOf(saved),repo.appointments())
  val after=repo.reminders().single()
  assertEquals(before.scheduledAt+88200000L,after.scheduledAt)
  assertEquals("",after.recipient); assertEquals(original.id,after.appointmentId)
  assertTrue(after.text.contains("10:37")); assertFalse(before.id==after.id)
 }
 fun testMoveReadsLegacyClientWithoutRevalidatingOrRefreshingSnapshots() {
  val original=fixture()
  sql { db-> val json=db.rawQuery("SELECT payload FROM clients WHERE id='move-client'",null).use { it.moveToFirst(); JSONObject(it.getString(0)) }
   json.put("firstName","L".repeat(60)); json.put("phone","legacy phone")
   db.execSQL("UPDATE clients SET payload=? WHERE id='move-client'",arrayOf(json.toString()))
  }
  repo.moveAppointment(original,date.plusDays(1),607); reopen()
  assertEquals(original.copy(date=date.plusDays(1)),repo.appointments().single())
  assertEquals("legacy phone",repo.clients().single().phone)
 }
 fun testConflictRefusalThenExplicitOverridePreservesOneIdentity() {
  val original=fixture()
  val other=repo.saveAppointment(Appointment("other",date,700,760,status=AppointmentStatus.DRAFT))
  val reminders=repo.reminders()
  try { repo.moveAppointment(original,date,697); fail("Conflict requires explicit override") } catch(e:ConflictException) { assertEquals(listOf(other),e.appointments) }
  reopen(); assertEquals(original,repo.appointments().single { it.id==original.id }); assertEquals(reminders,repo.reminders())
  val saved=repo.moveAppointment(original,date,697,allowOverlap=true)
  reopen(); assertEquals(original.copy(startMinute=697,endMinute=757),saved)
  assertEquals(2,repo.appointments().size); assertEquals(1,repo.appointments().count { it.id==original.id }); assertEquals(1,repo.reminders().size)
 }
 fun testStorageFailureRollsBackRecordAndReminderTogether() {
  val original=fixture(); val reminders=repo.reminders()
  sql { it.execSQL("CREATE TRIGGER fail_move_reminder BEFORE INSERT ON reminders BEGIN SELECT RAISE(ABORT, 'synthetic reminder failure'); END") }
  try { repo.moveAppointment(original,date,637); fail("Injected storage failure was ignored") } catch(_:android.database.sqlite.SQLiteException) {}
  reopen(); assertEquals(listOf(original),repo.appointments()); assertEquals(reminders,repo.reminders())
 }
 fun testStaleOriginalCannotOverwriteAConcurrentEdit() {
  val original=fixture(); val edited=repo.saveAppointment(original.copy(note="newer edit"))
  try { repo.moveAppointment(original,date,637); fail("Stale move accepted") } catch(_:ValidationException) {}
  reopen(); assertEquals(listOf(edited),repo.appointments())
 }
 fun testPersistingClampedPreviewDoesNotSnapItAgain() {
  val original=fixture()
  val preview=CalendarGeometry.move(original,date,10000f)
  assertEquals(1379,preview.startMinute); assertEquals(1439,preview.endMinute)
  val saved=repo.moveAppointment(original,preview.date,preview.startMinute)
  reopen(); assertEquals(preview,saved); assertEquals(listOf(preview),repo.appointments())
 }
}
