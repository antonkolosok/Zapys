package ua.zapys

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.test.AndroidTestCase
import org.json.JSONObject
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import java.time.LocalDate

class ClientPhonePersistenceTest : AndroidTestCase() {
 private val database="task-4-phone-synthetic.db"
 override fun setUp() { super.setUp(); context.deleteDatabase(database) }
 override fun tearDown() { context.deleteDatabase(database); super.tearDown() }
 fun testRepositoryRejectsInvalidClientWriteAndPreservesFormattedDuplicates() {
  val first=Client(id="first",firstName="Synthetic",phone=" \u00a0+380 (67) 123-45-67\u00a0 ")
  SQLiteRepository(context,database).use { repo->
   repo.saveClient(first)
   try { repo.saveClient(first.copy(phone="123")); fail("Invalid update accepted") } catch(_:ValidationException) { }
   try { repo.saveClient(first.copy(id="invalid",phone="+48123456789")); fail("Invalid insert accepted") } catch(_:ValidationException) { }
   assertEquals(listOf(first),repo.clients())
   val other=first.copy(id="other",phone="0671234567")
   assertEquals(listOf(first),FormRules.duplicates(other,repo.clients()))
   repo.saveClient(other)
  }
  SQLiteRepository(context,database).use { repo->
   assertEquals(2,repo.clients().size)
   assertEquals(first,repo.clients().single { it.id==first.id })
  }
 }
 fun testLegacyPhoneIsReadableUnchangedAndDoesNotBlockAppointmentSave() {
  val original=Client(id="legacy",firstName="Synthetic legacy",phone="0671234567")
  SQLiteRepository(context,database).use { it.saveClient(original) }
  // Reproduce an older app's payload without weakening the current repository write contract.
  val db=SQLiteDatabase.openDatabase(context.getDatabasePath(database).absolutePath,null,SQLiteDatabase.OPEN_READWRITE)
  try {
   val payload=db.query("clients",arrayOf("payload"),"id=?",arrayOf(original.id),null,null,null).use { cursor->cursor.moveToFirst();JSONObject(cursor.getString(0)) }
   payload.put("phone","123")
   db.update("clients",ContentValues().apply { put("payload",payload.toString()) },"id=?",arrayOf(original.id))
  } finally { db.close() }
  val legacy=original.copy(phone="123")
  SQLiteRepository(context,database).use { repo->
   assertEquals(listOf(legacy),repo.clients())
   val saved=repo.saveAppointment(Appointment(date=LocalDate.of(2030,1,2),startMinute=540,endMinute=570,clientId=legacy.id,status=AppointmentStatus.PLANNED))
   assertEquals("123",saved.clientPhone)
   repo.saveAppointment(saved.copy(note="Still usable"))
   try { repo.saveClient(legacy.copy(note="Attempted edit")); fail("Legacy client save requires correction") } catch(_:ValidationException) { }
   assertEquals(listOf(legacy),repo.clients())
  }
  SQLiteRepository(context,database).use { assertEquals(listOf(legacy),it.clients()); assertEquals("Still usable",it.appointments().single().note) }
 }
}
