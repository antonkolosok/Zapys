package ua.zapys

import android.test.ActivityInstrumentationTestCase2
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContentValues
import android.os.SystemClock
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real forms and SQLite; contact fixtures enter at the selected-contact result boundary only. */
class ClientFormValidationTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private var current:MainActivity?=null
 private var beforeClients=emptyList<Client>()
 private var beforeAppointments=emptyList<Appointment>()
 private var snapshotTaken=false
 private fun onMain(action:()->Unit) {
  var failure:Throwable?=null
  instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
  failure?.let { throw it }
 }
 private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
 private fun await(message:String, condition:()->Boolean) {
  val deadline=SystemClock.uptimeMillis()+8000
  do { var ready=false; onMain { ready=condition() }; if(ready) return; SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<deadline)
  fail(message)
 }
 private fun field(a:MainActivity,label:String)=views(a.window.decorView).filterIsInstance<EditText>().single { it.contentDescription==label }
 private fun button(a:MainActivity,label:String)=views(a.window.decorView).filterIsInstance<TextView>().single { it.text.toString()==label }
 private fun start():MainActivity {
  val a=activity; current=a
  await("Calendar did not load") { views(a.window.decorView).any { it.javaClass.simpleName=="CalendarView" } }
  SQLiteRepository(a).use { beforeClients=it.clients(); beforeAppointments=it.appointments(); snapshotTaken=true }
  return a
 }
 private fun fullForm(a:MainActivity) {
  onMain { button(a,"Клієнти").performClick() }
  await("Clients did not load") { views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="+ Додати клієнта" } }
  onMain { button(a,"+ Додати клієнта").performClick() }
  await("Full form did not load") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription=="Телефон *" } }
 }
 private fun recreate(a:MainActivity,label:String):MainActivity {
  val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
  try {
   onMain { a.recreate() }
   val next=instrumentation.waitForMonitorWithTimeout(monitor,8000) as? MainActivity ?: throw AssertionError("Activity was not recreated")
   current=next
   await("Restored form missing") { views(next.window.decorView).filterIsInstance<EditText>().any { it.contentDescription==label } }
   instrumentation.waitForIdleSync(); return next
  } finally { instrumentation.removeMonitor(monitor) }
 }
 private fun paste(a:MainActivity,label:String,raw:String) = onMain {
  val target=field(a,label); target.requestFocus(); target.selectAll()
  (a.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Synthetic Task 5 name",raw))
  assertTrue("Paste action must be handled",target.onTextContextMenuItem(android.R.id.paste))
  assertEquals("Paste must not truncate",raw,target.text.toString())
 }
 private fun assertNameFailure(a:MainActivity,label:String,raw:String) = onMain {
  val name=field(a,label)
  assertEquals("Invalid name must stay visible",raw,name.text.toString())
  assertTrue("First invalid field must receive focus: $label",name.hasFocus())
  assertTrue("Field must explain limit",name.error.toString().contains("50"))
 }
 /** Serialize a read behind any attempted UI write, so a missing write assertion cannot race IO. */
 private fun assertNoWrites(a:MainActivity) {
  val done=CountDownLatch(1); var clients:List<Client>?=null; var appointments:List<Appointment>?=null
  onMain { a.load({ it.clients() to it.appointments() }) { clients=it.first; appointments=it.second; done.countDown() } }
  assertTrue("Repository barrier did not finish",done.await(8,TimeUnit.SECONDS))
  assertEquals(beforeClients,clients); assertEquals(beforeAppointments,appointments)
 }
 private fun assertPhoneFailure(a:MainActivity,label:String,raw:String) = onMain {
  val phone=field(a,label)
  assertEquals("Invalid input must stay visible",raw,phone.text.toString())
  assertTrue("Phone must receive focus",phone.hasFocus())
  assertTrue("Phone must explain the Ukrainian format",phone.error.toString().contains("0671234567"))
 }
 private fun dialogAction(label:String) {
  val deadline=SystemClock.uptimeMillis()+8000
  val buttonId=if(label=="Скасувати") "android:id/button2" else "android:id/button1"
  do {
   val root=instrumentation.uiAutomation.rootInActiveWindow
   val action=root?.findAccessibilityNodeInfosByViewId(buttonId)?.firstOrNull { it.text?.toString()?.equals(label,ignoreCase=true)==true && it.isClickable }
   if(action!=null) { assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_CLICK)); instrumentation.waitForIdleSync(); return }
   SystemClock.sleep(20)
  } while(SystemClock.uptimeMillis()<deadline)
  fun describe(node:AccessibilityNodeInfo?):String {
   if(node==null) return "<no accessibility root>"
   return "[text=${node.text}, id=${node.viewIdResourceName}, class=${node.className}, clickable=${node.isClickable}]"+(0 until node.childCount).joinToString("") { describe(node.getChild(it)) }
  }
  fail("Dialog action missing: $label; active nodes: ${describe(instrumentation.uiAutomation.rootInActiveWindow)}")
 }
 fun testCanonicalDuplicateConfirmationKeepsSeparateUuidsAndCancelDoesNotWrite() {
  val a=start()
  val first=Client(firstName="Task 4 original synthetic",phone="+380 (67) 000-04-04",note="Unchanged")
  SQLiteRepository(a).use { it.saveClient(first) }
  fullForm(a)
  onMain { field(a,"Ім’я *").setText("Task 4 duplicate synthetic"); field(a,"Телефон *").setText("0670000404"); button(a,"Зберегти клієнта").performClick() }
  dialogAction("Скасувати")
  SQLiteRepository(a).use { assertEquals(beforeClients+first,it.clients()) }
  onMain { assertEquals("0670000404",field(a,"Телефон *").text.toString()); button(a,"Зберегти клієнта").performClick() }
  dialogAction("Підтвердити")
  await("Confirmed duplicate did not save") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription=="Телефон *" } }
  SQLiteRepository(a).use { repo->
   assertEquals(first,repo.clients().single { it.id==first.id })
   val second=repo.clients().single { it.firstName=="Task 4 duplicate synthetic" }
   assertFalse(first.id==second.id); assertEquals("0670000404",second.phone)
  }
 }
 fun testFullFormRejectsInvalidPhoneWithoutWritingThenPreservesFormattedSave() {
  val a=start(); fullForm(a)
  onMain { field(a,"Ім’я *").setText("Task 4 full synthetic"); field(a,"Телефон *").setText("123"); button(a,"Зберегти клієнта").performClick() }
  assertPhoneFailure(a,"Телефон *","123"); assertNoWrites(a)
  val formatted=" \u00a0+380 (67) 123-45-67\u00a0 "
  onMain { field(a,"Телефон *").setText(formatted); button(a,"Зберегти клієнта").performClick() }
  await("Valid full save did not finish") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription=="Телефон *" } }
  SQLiteRepository(a).use { assertEquals(formatted,it.clients().single { c->c.firstName=="Task 4 full synthetic" }.phone) }
 }
 fun testInlinePrimaryAndDraftRejectInvalidPhoneWithoutWritingThenSaveFormattedPhone() {
  val a=start()
  onMain { a.openAppointment() }
  await("Appointment form missing") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription=="Новий клієнт: телефон" } }
  onMain {
   field(a,"Закінчення * (ГГ:ХХ)").setText("23:59")
   field(a,"Новий клієнт: ім’я (необов’язково)").setText("Task 4 inline synthetic")
   val phone=field(a,"Новий клієнт: телефон"); assertEquals(InputType.TYPE_CLASS_PHONE,phone.inputType and InputType.TYPE_MASK_CLASS); phone.setText("+48123456789")
  }
  for(action in listOf("Зберегти запис","Зберегти чернетку")) {
   onMain { button(a,action).performClick() }
   assertPhoneFailure(a,"Новий клієнт: телефон","+48123456789"); assertNoWrites(a)
  }
  val formatted="\u00a0(050) 123-45-67 "
  onMain { field(a,"Новий клієнт: телефон").setText(formatted); button(a,"Зберегти чернетку").performClick() }
  await("Valid inline draft save did not finish") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription=="Новий клієнт: телефон" } }
  SQLiteRepository(a).use { repo->
   val client=repo.clients().single { it.firstName=="Task 4 inline synthetic" }
   assertEquals(formatted,client.phone)
   assertEquals(formatted,repo.appointments().single { it.clientId==client.id }.clientPhone)
  }
 }
 fun testSyntheticImportedContactUsesSameValidationAndKeepsInput() {
  val a=start(); fullForm(a)
  onMain {
   // Exercise the real applyContact path without querying, inserting or enumerating contacts.
   val identity=MainActivity::class.java.getDeclaredMethod("formIdentity").apply { isAccessible=true }.invoke(a)
   MainActivity::class.java.getDeclaredField("contactOwner").apply { isAccessible=true }.set(a,identity)
   MainActivity::class.java.getDeclaredField("pendingContact").apply { isAccessible=true }.set(a,"Task 4 contact synthetic" to "0671234567 ext 1")
   MainActivity::class.java.getDeclaredMethod("applyContact").apply { isAccessible=true }.invoke(a)
   assertEquals("Task 4 contact synthetic",field(a,"Ім’я *").text.toString())
   button(a,"Зберегти клієнта").performClick()
  }
  assertPhoneFailure(a,"Телефон *","0671234567 ext 1"); assertNoWrites(a)
  onMain { field(a,"Телефон *").setText("+380 (44) 123-45-67"); button(a,"Зберегти клієнта").performClick() }
  await("Corrected contact did not save") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription=="Телефон *" } }
  SQLiteRepository(a).use { assertEquals("+380 (44) 123-45-67",it.clients().single { c->c.firstName=="Task 4 contact synthetic" }.phone) }
 }
 fun testFullFormTypingPasteAndFieldOrderNeverTruncateThenSaveAll50CodePoints() {
  val a=start(); fullForm(a)
  val first="N".repeat(51); val surname="\uD801\uDC00".repeat(51); val patronymic="Ї".repeat(51)
  onMain { field(a,"Ім’я *").requestFocus() }
  instrumentation.sendStringSync(first); instrumentation.waitForIdleSync()
  onMain {
   assertEquals("Typing must not truncate",first,field(a,"Ім’я *").text.toString())
   field(a,"Телефон *").setText("123")
  }
  paste(a,"Прізвище (необов’язково)",surname)
  paste(a,"По батькові (необов’язково)",patronymic)
  onMain { button(a,"Зберегти клієнта").performClick() }
  assertNameFailure(a,"Ім’я *",first); assertNoWrites(a)
  onMain { field(a,"Ім’я *").setText(" "+"N".repeat(50)+" "); button(a,"Зберегти клієнта").performClick() }
  assertPhoneFailure(a,"Телефон *","123"); assertNoWrites(a)
  val phone=" \u00a0+380 (67) 000-05-01\u00a0 "
  onMain { field(a,"Телефон *").setText(phone); button(a,"Зберегти клієнта").performClick() }
  assertNameFailure(a,"Прізвище (необов’язково)",surname); assertNoWrites(a)
  onMain { field(a,"Прізвище (необов’язково)").setText(" \u00a0"+"\uD801\uDC00".repeat(50)+" "); button(a,"Зберегти клієнта").performClick() }
  assertNameFailure(a,"По батькові (необов’язково)",patronymic); assertNoWrites(a)
  onMain { field(a,"По батькові (необов’язково)").setText(" "+"Ї".repeat(50)+" "); button(a,"Зберегти клієнта").performClick() }
  await("Boundary client did not save") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription=="Телефон *" } }
  SQLiteRepository(a).use { repo->
   val saved=repo.clients().single { it.firstName=="N".repeat(50) }
   assertEquals("\uD801\uDC00".repeat(50),saved.surname); assertEquals("Ї".repeat(50),saved.patronymic); assertEquals(phone,saved.phone)
   assertEquals(152,saved.displayName.codePointCount(0,saved.displayName.length))
  }
 }
 fun testImportedOverlongNameAndAllRawFieldsSurviveErrorAndActualRecreation() {
  var a=start(); fullForm(a)
  val raw=" \u00a0"+"І".repeat(51)+"\u00a0 "; val surname=" "+"Ї".repeat(51)+" "; val patronymic=" \uD801\uDC00".repeat(26)
  val phone=" +380 (67) 000-05-02 "
  onMain {
   val identity=MainActivity::class.java.getDeclaredMethod("formIdentity").apply { isAccessible=true }.invoke(a)
   MainActivity::class.java.getDeclaredField("contactOwner").apply { isAccessible=true }.set(a,identity)
   MainActivity::class.java.getDeclaredField("pendingContact").apply { isAccessible=true }.set(a,raw to phone)
   MainActivity::class.java.getDeclaredMethod("applyContact").apply { isAccessible=true }.invoke(a)
   field(a,"Прізвище (необов’язково)").setText(surname); field(a,"По батькові (необов’язково)").setText(patronymic)
   button(a,"Зберегти клієнта").performClick()
  }
  assertNameFailure(a,"Ім’я *",raw); assertNoWrites(a)
  a=recreate(a,"Телефон *")
  onMain {
   assertEquals(raw,field(a,"Ім’я *").text.toString()); assertEquals(phone,field(a,"Телефон *").text.toString())
   assertEquals(surname,field(a,"Прізвище (необов’язково)").text.toString()); assertEquals(patronymic,field(a,"По батькові (необов’язково)").text.toString())
   button(a,"Зберегти клієнта").performClick()
  }
  assertNameFailure(a,"Ім’я *",raw); assertNoWrites(a)
  onMain {
   field(a,"Ім’я *").setText("Task 5 corrected contact"); field(a,"Прізвище (необов’язково)").setText(""); field(a,"По батькові (необов’язково)").setText("")
   button(a,"Зберегти клієнта").performClick()
  }
  await("Corrected contact did not save") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription=="Телефон *" } }
  SQLiteRepository(a).use { assertEquals(phone,it.clients().single { c->c.firstName=="Task 5 corrected contact" }.phone) }
 }
 fun testInlinePasteRecreationAndBothSaveRoutesShareCodePointLimit() {
  var a=start(); onMain { a.openAppointment() }
  val label="Новий клієнт: ім’я (необов’язково)"; val raw=" \u00a0"+"\uD801\uDC00".repeat(51)+" "
  await("Inline form missing") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription==label } }
  onMain { field(a,"Закінчення * (ГГ:ХХ)").setText("23:59"); field(a,"Новий клієнт: телефон").setText("123") }
  paste(a,label,raw)
  for(action in listOf("Зберегти запис","Зберегти чернетку")) {
   onMain { button(a,action).performClick() }; assertNameFailure(a,label,raw); assertNoWrites(a)
  }
  a=recreate(a,label)
  onMain { assertEquals(raw,field(a,label).text.toString()); assertEquals("123",field(a,"Новий клієнт: телефон").text.toString()); button(a,"Зберегти чернетку").performClick() }
  assertNameFailure(a,label,raw); assertNoWrites(a)
  val phone="\u00a0(050) 000-05-03 "
  onMain { field(a,label).setText(" "+"\uD801\uDC00".repeat(50)+" "); button(a,"Зберегти чернетку").performClick() }
  assertPhoneFailure(a,"Новий клієнт: телефон","123"); assertNoWrites(a)
  onMain { field(a,"Новий клієнт: телефон").setText(phone); button(a,"Зберегти чернетку").performClick() }
  await("Boundary inline draft did not save") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription==label } }
  SQLiteRepository(a).use { repo->
   val client=repo.clients().single { it.firstName=="\uD801\uDC00".repeat(50) }
   assertEquals(phone,client.phone); assertEquals(client.displayName,repo.appointments().single { it.clientId==client.id }.clientName)
  }
 }
 fun testLegacyOverlongNamesStayReadableAndUnchangedUntilDeliberateCorrection() {
  val a=start(); val legacy=Client(firstName="L".repeat(50),surname="S".repeat(50),patronymic="P".repeat(50),phone="0670000504")
  SQLiteRepository(a).use { it.saveClient(legacy) }
  // Model a pre-limit stored client; the production save path is intentionally not bypassable.
  val raw=legacy.copy(firstName="L".repeat(51),surname="S".repeat(51),patronymic="P".repeat(51))
  android.database.sqlite.SQLiteDatabase.openDatabase(a.getDatabasePath("zapys.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE).use { db->
   val payload=db.rawQuery("SELECT payload FROM clients WHERE id=?",arrayOf(legacy.id)).use { it.moveToFirst(); JSONObject(it.getString(0)) }
   payload.put("firstName",raw.firstName); payload.put("surname",raw.surname); payload.put("patronymic",raw.patronymic)
   assertEquals(1,db.update("clients",ContentValues().apply { put("payload",payload.toString()) },"id=?",arrayOf(legacy.id)))
  }
  SQLiteRepository(a).use { repo->
   assertEquals(raw,repo.clients().single { it.id==raw.id })
   val saved=repo.saveAppointment(Appointment(date=java.time.LocalDate.of(2040,5,5),startMinute=540,endMinute=570,clientId=raw.id,status=AppointmentStatus.PLANNED))
   assertEquals(raw.displayName,saved.clientName)
  }
  onMain { button(a,"Клієнти").performClick() }
  await("Legacy client missing") { views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="${raw.displayName}\n${raw.phone}" } }
  onMain { button(a,"${raw.displayName}\n${raw.phone}").performClick() }
  await("Legacy form missing") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription=="Телефон *" } }
  onMain { assertEquals(raw.firstName,field(a,"Ім’я *").text.toString()); assertEquals(raw.surname,field(a,"Прізвище (необов’язково)").text.toString()); assertEquals(raw.patronymic,field(a,"По батькові (необов’язково)").text.toString()); button(a,"Зберегти клієнта").performClick() }
  assertNameFailure(a,"Ім’я *",raw.firstName)
  SQLiteRepository(a).use { assertEquals(raw,it.clients().single { c->c.id==raw.id }) }
  onMain { field(a,"Ім’я *").setText(legacy.firstName); field(a,"Прізвище (необов’язково)").setText(legacy.surname); field(a,"По батькові (необов’язково)").setText(legacy.patronymic); button(a,"Зберегти клієнта").performClick() }
  await("Corrected legacy client did not save") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription=="Телефон *" } }
  SQLiteRepository(a).use { repo-> assertEquals(legacy,repo.clients().single { it.id==raw.id }); assertEquals(raw.displayName,repo.appointments().single { it.clientId==raw.id }.clientName) }
 }
 override fun tearDown() {
  current?.let { a->
   onMain { a.finish() }
   if(snapshotTaken) SQLiteRepository(a).use { repo->
    repo.appointments().filter { row->beforeAppointments.none { it.id==row.id } }.forEach { repo.deleteAppointment(it.id) }
    repo.clients().filter { row->beforeClients.none { it.id==row.id } }.forEach { repo.deleteClient(it.id) }
   }
  }
  super.tearDown()
 }
}
