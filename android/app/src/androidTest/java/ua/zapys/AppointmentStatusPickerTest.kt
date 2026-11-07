package ua.zapys

import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import java.time.LocalDate

/** Real status selection, saves and recreation with synthetic fixtures only. */
class AppointmentStatusPickerTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private var current:MainActivity?=null
 private val appointmentIds=mutableSetOf<String>()
 private val clientIds=mutableSetOf<String>()
 private val day=LocalDate.of(2096,8,8)
 private val endLabel="Закінчення * (ГГ:ХХ)"
 private val noteLabel="Нотатка (до 2000 символів)"
 private val receivedLabel="Фактично отримано, грн (необов’язково)"
 private val labels=listOf("Заплановано","Завершено","Скасовано","Не з’явився / не з’явилась")
 private val normalStatuses=listOf(AppointmentStatus.PLANNED,AppointmentStatus.COMPLETED,AppointmentStatus.CANCELLED,AppointmentStatus.NO_SHOW)
 private fun onMain(action:()->Unit) {
  var failure:Throwable?=null
  instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
  failure?.let { throw it }
 }
 private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
 private fun await(message:String,condition:()->Boolean) {
  val deadline=SystemClock.uptimeMillis()+8000
  do { var ready=false; onMain { ready=condition() }; if(ready) return; SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<deadline)
  fail(message)
 }
 private fun field(a:MainActivity,label:String)=views(a.window.decorView).filterIsInstance<EditText>().single { it.contentDescription==label }
 private fun button(a:MainActivity,label:String)=views(a.window.decorView).filterIsInstance<TextView>().single { it.text.toString()==label }
 private fun picker(a:MainActivity)=views(a.window.decorView).filterIsInstance<Spinner>().last()
 private fun draft(a:MainActivity)=MainActivity::class.java.getDeclaredField("appointmentDraft").apply { isAccessible=true }.get(a) as AppointmentDraft
 private fun editorReady(a:MainActivity) {
  await("Appointment editor did not load") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription==endLabel } }
  instrumentation.waitForIdleSync()
 }
 private fun calendarReady(a:MainActivity) {
  await("Calendar did not load") { views(a.window.decorView).any { it.javaClass.simpleName=="CalendarView" } }
 }
 private fun start():Pair<MainActivity,Client> {
  val a=activity; current=a; calendarReady(a)
  val c=Client(firstName="Task 8 synthetic",phone="0670000801")
  SQLiteRepository(a).use { it.saveClient(c) }; clientIds.add(c.id)
  return a to c
 }
 private fun fresh(a:MainActivity):String {
  onMain { button(a,"+ Новий запис").performClick() }; editorReady(a)
  var id=""; onMain { id=draft(a).id }; appointmentIds.add(id); return id
 }
 private fun store(a:MainActivity,value:Appointment):Appointment {
  appointmentIds.add(value.id); return SQLiteRepository(a).use { it.saveAppointment(value) }
 }
 private fun open(a:MainActivity,id:String) { onMain { a.openAppointment(id) }; editorReady(a) }
 private fun selectClient(a:MainActivity,c:Client) {
  val clients=views(a.window.decorView).filterIsInstance<Spinner>().first()
  clients.setSelection((1 until clients.count).single { clients.getItemAtPosition(it).toString().startsWith(c.displayName+" · ") })
 }
 private fun save(a:MainActivity,label:String="Зберегти запис") {
  onMain { button(a,label).performClick() }; calendarReady(a)
 }
 private fun saved(a:MainActivity,id:String)=SQLiteRepository(a).use { it.appointments().single { value->value.id==id } }
 private fun recreate(a:MainActivity):MainActivity {
  val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
  try {
   onMain { a.recreate() }
   val next=instrumentation.waitForMonitorWithTimeout(monitor,8000) as? MainActivity ?: throw AssertionError("Activity did not recreate")
   current=next; editorReady(next); return next
  } finally { instrumentation.removeMonitor(monitor) }
 }
 private fun confirmBackDraft() {
  val deadline=SystemClock.uptimeMillis()+8000
  do {
   val action=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("android:id/button1")?.firstOrNull { it.text?.toString()?.equals("Зберегти чернетку",ignoreCase=true)==true }
   if(action!=null) { assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_CLICK)); instrumentation.waitForIdleSync(); return }
   SystemClock.sleep(20)
  } while(SystemClock.uptimeMillis()<deadline)
  fail("Back save-draft confirmation was missing")
 }
 // Catches accidental inclusion of draft/placeholder and filtered-index label mismatches.
 fun testNormalPickerContainsOnlyFourLiteralChoices() {
  val (a,_)=start(); fresh(a)
  onMain { val status=picker(a); assertEquals(labels,(0 until status.count).map { status.getItemAtPosition(it).toString() }); assertEquals("Заплановано",status.selectedItem.toString()) }
 }
 // Catches fresh status remaining DRAFT, end becoming optional, or primary save accepting no client.
 fun testFreshDefaultsToPlannedAndPrimaryRequiresEndAndClient() {
  val (a,c)=start(); val id=fresh(a)
  onMain {
   assertEquals(AppointmentStatus.PLANNED,draft(a).status); assertEquals("",field(a,endLabel).text.toString())
   button(a,"Зберегти запис").performClick(); assertNotNull(field(a,endLabel).error)
   field(a,endLabel).setText("10:00"); button(a,"Зберегти запис").performClick()
   assertTrue(views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString().contains("клієнта") && it.text.toString().contains("Оберіть") })
  }
  SQLiteRepository(a).use { assertTrue(it.appointments().none { value->value.id==id }) }
  onMain { selectClient(a,c) }; instrumentation.waitForIdleSync(); save(a)
  assertEquals(AppointmentStatus.PLANNED,saved(a,id).status); assertEquals(c.id,saved(a,id).clientId)
 }
 // Catches using enum ordinals after removing DRAFT from the adapter.
 fun testEveryDisplayedChoiceSavesItsCorrespondingStatus() {
  val (a,c)=start()
  normalStatuses.forEachIndexed { index,want ->
   val value=store(a,Appointment(date=day,startMinute=600+index*60,endMinute=630+index*60,clientId=c.id))
   open(a,value.id)
   onMain { picker(a).setSelection(index) }; instrumentation.waitForIdleSync(); save(a)
   assertEquals("Choice ${labels[index]}",want,saved(a,value.id).status)
  }
 }
 // Catches render callbacks changing a stored non-draft status, including after real recreation.
 fun testExistingNormalStatusesRoundTripThroughRecreationUnchanged() {
  val (first,c)=start(); var a=first
  normalStatuses.forEachIndexed { index,want ->
   val value=store(a,Appointment(date=day,startMinute=600+index*60,endMinute=630+index*60,clientId=c.id,status=want))
   open(a,value.id)
   onMain { assertEquals(labels[index],picker(a).selectedItem.toString()); assertEquals(want,draft(a).status) }
   a=recreate(a)
   onMain { assertEquals(labels[index],picker(a).selectedItem.toString()); assertEquals(want,draft(a).status) }
   save(a); assertEquals(want,saved(a,value.id).status)
  }
 }
 // Catches displayed PLANNED overwriting DRAFT identity or failed-save raw state during recreation.
 fun testStoredDraftKeepsIdentityThroughFailedPrimaryAndActualRecreationThenPromotes() {
  val (first,c)=start(); var a=first
  val value=store(a,CalendarGeometry.draft(day,600f)); assertEquals(30,value.endMinute-value.startMinute)
  open(a,value.id)
  onMain {
   assertEquals("Заплановано",picker(a).selectedItem.toString()); assertEquals(AppointmentStatus.DRAFT,draft(a).status)
   field(a,noteLabel).setText("Task 8 retained note"); field(a,receivedLabel).setText("bad money")
   button(a,"Зберегти запис").performClick(); assertNotNull(field(a,receivedLabel).error)
   assertEquals(AppointmentStatus.DRAFT,draft(a).status)
  }
  assertEquals(value,saved(a,value.id)); a=recreate(a)
  onMain {
   assertEquals(value.id,draft(a).id); assertEquals(AppointmentStatus.DRAFT,draft(a).status); assertEquals("Заплановано",picker(a).selectedItem.toString())
   assertEquals("bad money",field(a,receivedLabel).text.toString()); assertEquals("Task 8 retained note",field(a,noteLabel).text.toString())
   field(a,receivedLabel).setText("")
  }
  save(a,"Зберегти чернетку"); assertEquals(AppointmentStatus.DRAFT,saved(a,value.id).status)
  open(a,value.id); onMain { selectClient(a,c) }; instrumentation.waitForIdleSync(); save(a)
  assertEquals(AppointmentStatus.PLANNED,saved(a,value.id).status); assertEquals("Task 8 retained note",saved(a,value.id).note)
 }
 // Catches either draft action accidentally using the displayed normal status or requiring a client.
 fun testExplicitAndBackDraftActionsSaveWithoutClient() {
  val (a,_)=start()
  for(back in listOf(false,true)) {
   val id=fresh(a)
   onMain { field(a,endLabel).setText("10:00") }
   if(back) { onMain { button(a,"‹ Назад").performClick() }; confirmBackDraft(); calendarReady(a) }
   else save(a,"Зберегти чернетку")
   assertEquals(AppointmentStatus.DRAFT,saved(a,id).status); assertNull(saved(a,id).clientId)
   // Keep the second draft independent of the first fixture's interval.
   SQLiteRepository(a).use { it.deleteAppointment(id) }
  }
 }
 // Catches fresh() changes leaking into repeat identity, payment, or date/time reset behavior.
 fun testRepeatKeepsSeparateDraftIdentityAndResetVisitState() {
  val (a,c)=start()
  val source=store(a,Appointment(date=day,startMinute=600,endMinute=630,clientId=c.id,status=AppointmentStatus.COMPLETED,paid=true,receivedMinor=900,manualEnd=true,services=listOf(Service(name="Retained synthetic").snapshot()),note="retained"))
  open(a,source.id); onMain { button(a,"Повторити запис").performClick() }; editorReady(a)
  onMain {
   val repeated=draft(a); appointmentIds.add(repeated.id)
   assertFalse(source.id==repeated.id); assertEquals(AppointmentStatus.DRAFT,repeated.status)
   assertEquals("Заплановано",picker(a).selectedItem.toString()); assertEquals("",repeated.date); assertEquals("",repeated.start); assertEquals("",repeated.end)
   assertFalse(repeated.paid); assertEquals("",repeated.received); assertFalse(repeated.manualEnd)
   assertEquals(source.clientId,repeated.clientId); assertEquals(source.services,repeated.services); assertEquals(source.note,repeated.note)
  }
  assertEquals(source,saved(a,source.id))
 }
 override fun tearDown() {
  try { current?.let { a -> SQLiteRepository(a).use { repo->appointmentIds.forEach { repo.deleteAppointment(it) }; clientIds.forEach { repo.deleteClient(it) } }; onMain { a.finish() }; instrumentation.waitForIdleSync() } }
  finally { super.tearDown() }
 }
}
