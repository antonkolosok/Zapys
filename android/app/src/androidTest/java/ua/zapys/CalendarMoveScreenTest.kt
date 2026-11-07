package ua.zapys

import android.database.sqlite.SQLiteDatabase
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.graphics.Rect
import android.test.ActivityInstrumentationTestCase2
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import ua.zapys.ui.CalendarScreen
import ua.zapys.ui.CalendarView
import java.time.LocalDate
import java.io.File

/** Complete CalendarScreen gesture/dialog/repository path with synthetic records only. */
class CalendarMoveScreenTest:ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private var current:MainActivity?=null
 private val ids=mutableSetOf<String>(); private var clientId:String?=null
 private val day=LocalDate.of(2097,12,31)
 private var refreshes=0
 private fun onMain(action:()->Unit) { if(Looper.myLooper()==Looper.getMainLooper()) { action(); return }; var failure:Throwable?=null; instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }; failure?.let { throw it } }
 private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
 private fun await(message:String,condition:()->Boolean) { val end=SystemClock.uptimeMillis()+8000; do { if(condition()) return; SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<end); fail(message) }
 private fun uiReady(a:MainActivity) { await("Calendar did not load") { var ready=false; onMain { ready=views(a.window.decorView).any { it is CalendarView } }; ready }; instrumentation.waitForIdleSync() }
 private fun sql(action:(SQLiteDatabase)->Unit) { SQLiteDatabase.openDatabase(instrumentation.targetContext.getDatabasePath("zapys.db").path,null,SQLiteDatabase.OPEN_READWRITE).use(action) }
 private fun writes():Int { var count=0; sql { db-> count=db.rawQuery("SELECT count FROM task9_write_count",null).use { it.moveToFirst(); it.getInt(0) } }; return count }
 private fun show(a:MainActivity,date:LocalDate=day,mode:String="day") {
  val records=SQLiteRepository(a).use { it.appointments() }
  onMain { a.show(CalendarScreen.create(a,records,date,mode) { next,m->refreshes++; show(a,next,m) }) }
  if(Looper.myLooper()!=Looper.getMainLooper()) instrumentation.waitForIdleSync()
 }
 private fun start(landscape:Boolean=false):Pair<MainActivity,Appointment> {
  var a=activity; current=a; uiReady(a)
  if(landscape) {
   val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
   try {
    onMain { a.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
    (instrumentation.waitForMonitorWithTimeout(monitor,2500) as? MainActivity)?.let { a=it; current=a }
   } finally { instrumentation.removeMonitor(monitor) }
   uiReady(a)
   await("Move dialog fixture must actually be landscape") { var ready=false; onMain { ready=a.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE && a.window.decorView.width>a.window.decorView.height }; ready }
  }
  val client=Client(id="task9-screen-client-"+newId(),firstName="Move historical",phone="0670000901",reminder=ReminderPreferences(enabled=true,recipient="",leadMinutes=60)); clientId=client.id
  val original=SQLiteRepository(a).use { repo->
   // Recover only this test class's UUID namespace after a previous interrupted run.
   repo.appointments().filter { it.id.startsWith("task9-screen-") }.forEach { repo.deleteAppointment(it.id) }
   repo.clients().filter { it.id.startsWith("task9-screen-client-") }.forEach { repo.deleteClient(it.id) }
   repo.saveClient(client)
   repo.saveAppointment(Appointment(id="task9-screen-visit-"+newId(),date=day,startMinute=607,endMinute=667,clientId=client.id,note="preserved",paid=true,receivedMinor=123,status=AppointmentStatus.PLANNED,manualEnd=true)).also { ids.add(it.id); repo.saveClient(client.copy(firstName="Move current")) }
  }
  sql { db-> db.execSQL("DROP TRIGGER IF EXISTS task9_count_move"); db.execSQL("CREATE TABLE IF NOT EXISTS task9_write_count (count INTEGER)"); db.execSQL("DELETE FROM task9_write_count"); db.execSQL("INSERT INTO task9_write_count VALUES (0)")
   db.execSQL("CREATE TRIGGER task9_count_move AFTER UPDATE ON appointments WHEN NEW.id='${original.id}' BEGIN UPDATE task9_write_count SET count=count+1; END") }
  show(a); return a to original
 }
 private fun record(a:MainActivity,id:String)=SQLiteRepository(a).use { repo->repo.appointments().single { it.id==id } }
 private fun accessible(a:MainActivity) { onMain {
  val actions=views(a.window.decorView).filterIsInstance<Button>().filter { it.text.toString().startsWith("Перенести запис: Move historical") }
  assertEquals("Every appointment needs an accessible move action",1,actions.size); assertTrue(actions.single().performClick())
 }; instrumentation.waitForIdleSync() }
 private fun nodes(node:AccessibilityNodeInfo?):List<AccessibilityNodeInfo> = if(node==null) emptyList() else listOf(node)+(0 until node.childCount).flatMap { nodes(node.getChild(it)) }
 private fun screenshot(label:String) {
  val bitmap=instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("Screenshot unavailable")
  val context=instrumentation.targetContext
  val output=File(context.getExternalFilesDir(null),"task-9").apply { mkdirs() }
  val orientation=if(bitmap.width>bitmap.height) "-landscape" else ""
  try { File(output,"$label$orientation-font${context.resources.configuration.fontScale}.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
 }
 private fun fullyVisible(node:AccessibilityNodeInfo):Boolean {
  if(!node.isVisibleToUser) return false
  val bounds=Rect(); node.getBoundsInScreen(bounds)
  if(bounds.isEmpty) return false
  var parent=node.parent
  while(parent!=null) {
   val clip=Rect(); parent.getBoundsInScreen(clip)
   if(parent.isScrollable && !clip.contains(bounds)) return false
   parent=parent.parent
  }
  val window=Rect(); instrumentation.uiAutomation.rootInActiveWindow?.getBoundsInScreen(window)
  return window.contains(bounds)
 }
 private fun setDialogField(label:String,value:String) {
  var field:AccessibilityNodeInfo?=null
  await("Move control never became accessible and fully visible: $label") {
   val tree=nodes(instrumentation.uiAutomation.rootInActiveWindow)
   field=tree.singleOrNull { it.contentDescription?.toString()==label && it.isEditable }
   if(field!=null) { field!!.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.id); field!!.refresh(); fullyVisible(field!!) }
   else { if(tree.any { it.text?.toString()=="Перенести запис" }) tree.firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); false }
  }
  assertTrue(field!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value) })); instrumentation.waitForIdleSync()
  screenshot(if(label.startsWith("Нова")) "move-date-control" else "move-time-control")
 }
 private fun action(label:String) {
  await("Dialog action missing: $label") {
   val node=nodes(instrumentation.uiAutomation.rootInActiveWindow).firstOrNull { it.isClickable && it.text?.toString()?.equals(label,ignoreCase=true)==true }
   if(node==null || !fullyVisible(node)) false else { assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)); instrumentation.waitForIdleSync(); true }
  }
 }
 private fun target(date:String="2098-01-01",time:String="10:37") { setDialogField("Нова дата (РРРР-ММ-ДД)",date); setDialogField("Новий початок (ГГ:ХХ)",time); action("Далі") }
 fun testAccessibleMoveConfirmsActualDateTimeThenPersistsExactlyOnce() {
  val (a,original)=start(); accessible(a); target()
  await("Confirmation must expose the exact target date and range") { nodes(instrumentation.uiAutomation.rootInActiveWindow).any { it.text?.toString()?.contains("2098-01-01") == true && it.text.toString().contains("10:37–11:37") } }
  screenshot("move-confirmation")
  assertEquals(original,record(a,original.id)); assertEquals(0,writes())
  action("Зберегти"); await("Move did not persist") { record(a,original.id).date==day.plusDays(1) }
  assertEquals(original.copy(date=day.plusDays(1),startMinute=637,endMinute=697),record(a,original.id)); assertEquals(1,writes())
 }
 fun testLandscapeMoveControlsAndConfirmationAreFullyReachable() {
  val (a,original)=start(landscape=true)
  accessible(a); target()
  await("Landscape confirmation must expose target date and range") { nodes(instrumentation.uiAutomation.rootInActiveWindow).any { it.text?.toString()?.contains("2098-01-01") == true && it.text.toString().contains("10:37–11:37") } }
  screenshot("move-confirmation")
  val tree=nodes(instrumentation.uiAutomation.rootInActiveWindow)
  for(label in listOf("Скасувати","Зберегти")) {
   val button=tree.singleOrNull { it.isClickable && it.text?.toString()?.equals(label,ignoreCase=true)==true }
   assertNotNull("Landscape confirmation action missing: $label",button)
   assertTrue("Landscape confirmation action must be fully visible: $label",fullyVisible(button!!))
  }
  action("Скасувати"); assertEquals(original,record(a,original.id)); assertEquals(0,writes())
 }
 fun testAccessibleNoOpCancelBackAndRecreationDoNotWrite() {
  val (first,original)=start(); var a=first
  accessible(a); action("Далі"); assertEquals(0,writes())
  accessible(a); target(); action("Скасувати"); assertEquals(0,writes())
  accessible(a); target(); instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); instrumentation.waitForIdleSync(); assertEquals(0,writes())
  accessible(a); target()
  val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
  try { onMain { a.recreate() }; a=instrumentation.waitForMonitorWithTimeout(monitor,8000) as? MainActivity ?: throw AssertionError("Recreation failed"); current=a; uiReady(a) } finally { instrumentation.removeMonitor(monitor) }
  assertEquals(original,record(a,original.id)); assertEquals(0,writes())
 }
 fun testConflictDeclineThenExplicitOverrideWritesOnce() {
  val (a,original)=start()
  SQLiteRepository(a).use { repo->val other=repo.saveAppointment(Appointment(id="task9-screen-conflict-"+newId(),date=day,startMinute=700,endMinute=760)); ids.add(other.id) }; show(a)
  accessible(a); target(day.toString(),"11:37"); action("Зберегти"); action("Скасувати")
  assertEquals(original,record(a,original.id)); assertEquals(0,writes())
  accessible(a); target(day.toString(),"11:37"); action("Зберегти"); action("Зберегти попри перетин")
  await("Override did not persist") { record(a,original.id).startMinute==697 }
  assertEquals(original.copy(startMinute=697,endMinute=757),record(a,original.id)); assertEquals(1,writes())
 }
 private fun beginDrag(a:MainActivity,original:Appointment):()->Unit {
  lateinit var grid:CalendarView; lateinit var scroll:ScrollView; var x=0f; var y=0f
  onMain {
   grid=views(a.window.decorView).filterIsInstance<CalendarView>().single(); scroll=grid.parent.parent as ScrollView
   x=(42f+(grid.width/a.resources.displayMetrics.density-42f)/2)*a.resources.displayMetrics.density
   y=CalendarGeometry.top(original.startMinute)*a.resources.displayMetrics.density-scroll.scrollY+12*a.resources.displayMetrics.density
  }
  val down=SystemClock.uptimeMillis()
  fun event(type:Int,py:Float) { onMain { val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),type,x,py,0); try { scroll.dispatchTouchEvent(e) } finally { e.recycle() } } }
  event(MotionEvent.ACTION_DOWN,y); SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong()+80)
  event(MotionEvent.ACTION_MOVE,y+36*a.resources.displayMetrics.density)
  onMain { assertTrue("Lifecycle/write assertions require an active visible preview",views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString().startsWith("Попередній перегляд:") }) }
  return { event(MotionEvent.ACTION_UP,y+36*a.resources.displayMetrics.density) }
 }
 fun testDragPreviewDoesNotWriteAndDropUsesSameConfirmation() {
  val (a,original)=start(); val reminders=SQLiteRepository(a).use { it.reminders().filter { it.appointmentId==original.id } }
  val release=beginDrag(a,original)
  assertEquals("Pointer previews must not update SQLite",0,writes()); assertEquals(original,record(a,original.id))
  assertEquals(reminders,SQLiteRepository(a).use { it.reminders().filter { it.appointmentId==original.id } })
  release(); action("Зберегти")
  await("Drag move did not persist") { record(a,original.id).startMinute==637 }; assertEquals(1,writes())
 }
 fun testActiveDragRecreationAndBackDiscardPreviewWithoutWrites() {
  val (first,original)=start(); var a=first
  beginDrag(a,original); assertEquals(0,writes())
  val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
  try { onMain { a.recreate() }; a=instrumentation.waitForMonitorWithTimeout(monitor,8000) as? MainActivity ?: throw AssertionError("Active drag recreation failed"); current=a; uiReady(a) } finally { instrumentation.removeMonitor(monitor) }
  assertEquals(original,record(a,original.id)); assertEquals(0,writes()); show(a)
  beginDrag(a,original); instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); instrumentation.waitForIdleSync()
  assertEquals(original,record(a,original.id)); assertEquals(0,writes())
 }
 override fun tearDown() {
  try { sql { it.execSQL("DROP TRIGGER IF EXISTS task9_count_move"); it.execSQL("DROP TABLE IF EXISTS task9_write_count") }
   current?.let { a->SQLiteRepository(a).use { repo->ids.forEach { repo.deleteAppointment(it) }; clientId?.let { repo.deleteClient(it) } }; onMain { a.finish() }; instrumentation.waitForIdleSync() }
  } finally { super.tearDown() }
 }
}
