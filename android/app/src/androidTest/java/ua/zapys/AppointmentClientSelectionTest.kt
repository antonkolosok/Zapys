package ua.zapys

import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import java.time.LocalDate

/** Real editor interaction and recreation; only synthetic records, no external activities. */
class AppointmentClientSelectionTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private var current:MainActivity?=null
 private val createdClients=mutableSetOf<String>()
 private val createdAppointments=mutableSetOf<String>()
 private fun onMain(action:()->Unit) {
  var failure:Throwable?=null
  instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
  failure?.let { throw it }
 }
 private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
 private fun await(message:String,condition:()->Boolean) {
  val until=SystemClock.uptimeMillis()+8000
  do { var ready=false; onMain { ready=condition() }; if(ready) return; SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<until)
  fail(message)
 }
 private val nameLabel="Новий клієнт: ім’я (необов’язково)"
 private val phoneLabel="Новий клієнт: телефон"
 private fun field(a:MainActivity,label:String)=views(a.window.decorView).filterIsInstance<EditText>().single { it.contentDescription==label }
 private fun selection(a:MainActivity)=views(a.window.decorView).filterIsInstance<Spinner>().first()
 private fun select(a:MainActivity,c:Client) { val spinner=selection(a); spinner.setSelection((1 until spinner.count).single { spinner.getItemAtPosition(it).toString().startsWith(c.displayName+" · ") }) }
 private fun button(a:MainActivity,label:String)=views(a.window.decorView).filterIsInstance<TextView>().single { it.text.toString()==label }
 private fun start():Pair<MainActivity,Client> {
  val a=activity; current=a
  await("Calendar did not load") { views(a.window.decorView).any { it.javaClass.simpleName=="CalendarView" } }
  val c=Client(firstName="Task 7 selected synthetic",phone="0670000711")
  SQLiteRepository(a).use { it.saveClient(c) }; createdClients.add(c.id)
  return a to c
 }
 private fun open(a:MainActivity,clientId:String?) {
  val visit=Appointment(date=LocalDate.of(2095,7,7),startMinute=600,endMinute=630,clientId=clientId)
  createdAppointments.add(visit.id)
  onMain { a.openCalendarDraft(visit) }
  await("Editor did not load") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription==phoneLabel } }
  instrumentation.waitForIdleSync()
 }
 private fun assertInlineHidden(a:MainActivity)=onMain {
  assertFalse("Inactive name must be hidden",field(a,nameLabel).isShown)
  assertFalse("Inactive phone must be hidden",field(a,phoneLabel).isShown)
  val labels=views(a.window.decorView).filterIsInstance<TextView>().filter { it !is EditText && (it.text.toString()==nameLabel || it.text.toString()==phoneLabel || it.text.toString().startsWith("Ім’я нового клієнта:")) }
  assertEquals(3,labels.size); labels.forEach { assertFalse("Inactive label is visible: ${it.text}",it.isShown) }
 }
 private fun recreate(a:MainActivity):MainActivity {
  val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
  try {
   onMain { a.recreate() }
   val next=instrumentation.waitForMonitorWithTimeout(monitor,8000) as? MainActivity ?: throw AssertionError("Activity not recreated")
   current=next
   await("Restored editor did not load") { views(next.window.decorView).filterIsInstance<EditText>().any { it.contentDescription==phoneLabel } }
   instrumentation.waitForIdleSync(); return next
  } finally { instrumentation.removeMonitor(monitor) }
 }
 // Catches first-render and restored selected-client forms exposing inactive labels or fields.
 fun testExistingSelectionHidesEntireInlineFormInitiallyAndAfterRecreation() {
  val (first,c)=start(); open(first,c.id); assertInlineHidden(first)
  val next=recreate(first); assertInlineHidden(next)
  onMain { assertTrue(selection(next).selectedItem.toString().contains(c.firstName)) }
 }
 // Catches hidden invalid text replacing selected identity or blocking save, and lost toggle drafts.
 fun testToggleRetainsInvalidRawInputThroughRecreationAndSelectedClientSave() {
  val (first,c)=start(); var a=first; open(a,null)
  onMain {
   assertTrue(field(a,nameLabel).isShown); field(a,nameLabel).setText("x".repeat(51)); field(a,phoneLabel).setText(" bad phone ")
   select(a,c)
  }
  instrumentation.waitForIdleSync(); assertInlineHidden(a)
  a=recreate(a); assertInlineHidden(a)
  onMain { selection(a).setSelection(0) }; instrumentation.waitForIdleSync()
  onMain {
   assertTrue(field(a,nameLabel).isShown); assertTrue(field(a,phoneLabel).isShown)
   assertEquals("x".repeat(51),field(a,nameLabel).text.toString()); assertEquals(" bad phone ",field(a,phoneLabel).text.toString())
   select(a,c)
  }
  instrumentation.waitForIdleSync()
  val before=SQLiteRepository(a).use { it.clients() }
  onMain { button(a,"Зберегти запис").performClick() }
  await("Selected-client save did not finish") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription==phoneLabel } }
  SQLiteRepository(a).use { repo ->
   assertEquals(before,repo.clients())
   val saved=repo.appointments().single { it.id in createdAppointments }
   assertEquals(c.id,saved.clientId); assertEquals(c.displayName,saved.clientName)
  }
 }
 // Catches clearing selection leaving inline validation inactive, or discarding bad raw phone text.
 fun testClearedSelectionValidatesAndFocusesInlinePhoneWithoutCreatingClient() {
  val (a,c)=start(); open(a,c.id)
  onMain { selection(a).setSelection(0) }; instrumentation.waitForIdleSync()
  val before=SQLiteRepository(a).use { it.clients() }
  onMain {
   field(a,nameLabel).setText("Synthetic inline"); field(a,phoneLabel).setText("invalid")
   button(a,"Зберегти чернетку").performClick()
   assertNotNull(field(a,phoneLabel).error); assertTrue(field(a,phoneLabel).hasFocus())
   assertEquals("invalid",field(a,phoneLabel).text.toString())
  }
  SQLiteRepository(a).use { assertEquals(before,it.clients()); assertTrue(it.appointments().none { v->v.id in createdAppointments }) }
 }
 override fun tearDown() {
  try {
   current?.let { a ->
    SQLiteRepository(a).use { repo -> createdAppointments.forEach { repo.deleteAppointment(it) }; createdClients.forEach { repo.deleteClient(it) } }
    onMain { a.finish() }; instrumentation.waitForIdleSync()
   }
  } finally { super.tearDown() }
 }
}
