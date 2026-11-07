package ua.zapys

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import ua.zapys.ui.CalendarView
import java.io.File
import java.time.LocalDate

/** Real Activity calendar defaults and lifecycle/navigation state; no records are written. */
class CalendarDefaultModeTest:ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private var current:MainActivity?=null
 private fun onMain(action:()->Unit) {
  var failure:Throwable?=null
  instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
  failure?.let { throw it }
 }
 private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
 private fun await(message:String,condition:()->Boolean) {
  val deadline=SystemClock.uptimeMillis()+8000
  do { var ready=false; onMain { ready=condition() }; if(ready) return; SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<deadline)
  fail(message)
 }
 private fun calendar(a:MainActivity) {
  await("Calendar did not load") { views(a.window.decorView).filterIsInstance<CalendarView>().any { it.width>0 } }
  instrumentation.waitForIdleSync()
 }
 private fun start():MainActivity = activity.also { current=it; calendar(it) }
 private fun button(a:MainActivity,label:String)=views(a.window.decorView).filterIsInstance<Button>().single { it.text.toString()==label }
 private fun reveal(a:MainActivity,label:String) {
  await("Action not laid out: $label") { views(a.window.decorView).filterIsInstance<Button>().any { it.text.toString()==label && it.width>0 && it.height>0 } }
  onMain { button(a,label).let { it.requestRectangleOnScreen(Rect(0,0,it.width,it.height),true) } }
  instrumentation.waitForIdleSync()
  onMain {
   val action=button(a,label); val visible=Rect()
   assertTrue("Action must be visible through all ancestors: $label",action.isShown && action.getGlobalVisibleRect(visible))
   assertEquals("Action width must fit: $label",action.width,visible.width())
   assertEquals("Action height must fit: $label",action.height,visible.height())
  }
 }
 private fun click(a:MainActivity,label:String) {
  reveal(a,label)
  onMain { assertTrue("Action must be enabled: $label",button(a,label).isEnabled); assertTrue(button(a,label).performClick()) }
 }
 private fun selectedDate(a:MainActivity):LocalDate = LocalDate.parse(views(a.window.decorView).filterIsInstance<Button>().single {
  it.isSelected && it.contentDescription?.toString()?.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*"))==true
 }.contentDescription.toString().take(10))
 private fun assertDay(a:MainActivity,date:LocalDate)=onMain {
  assertEquals("Day selection must retain the exact date",date,selectedDate(a))
  assertNotNull(button(a,"✓ День")); assertNotNull(button(a,"Тиждень"))
  assertTrue("Day timeline must not retain week-column headings",views(a.window.decorView).none { it.contentDescription?.toString()?.endsWith(", колонка календаря")==true })
 }
 private fun chooseDayAndNextWeek(a:MainActivity):LocalDate {
  var original=LocalDate.MIN; onMain { original=selectedDate(a) }
  click(a,"День"); calendar(a); assertDay(a,original)
  click(a,"Тиждень ›"); calendar(a)
  return original.plusDays(7).also { assertDay(a,it) }
 }
 private fun recreate(a:MainActivity):MainActivity {
  val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
  try {
   onMain { a.recreate() }
   val next=instrumentation.waitForMonitorWithTimeout(monitor,8000) as? MainActivity ?: throw AssertionError("Activity did not recreate")
   assertNotSame("Framework recreation must produce a new Activity",a,next)
   current=next; calendar(next); return next
  } finally { instrumentation.removeMonitor(monitor) }
 }
 // Catches a fresh launch using a single-day timeline instead of the seven-column week.
 fun testFreshLaunchSelectsWeekAndSevenDateColumns() {
  val a=start()
  onMain {
   val all=views(a.window.decorView)
   assertTrue("Week switch must be selected on fresh launch",all.filterIsInstance<Button>().any { it.text.toString()=="✓ Тиждень" })
   assertTrue("Day switch remains available",all.filterIsInstance<Button>().any { it.text.toString()=="День" })
   val headings=all.filterIsInstance<TextView>().filter { it.contentDescription?.toString()?.endsWith(", колонка календаря")==true }
   assertEquals("Week timeline must expose all seven date columns",7,headings.size)
   assertEquals("Every column has a distinct date",7,headings.map { it.contentDescription.toString().take(10) }.distinct().size)
  }
  // Capture the actual attached fresh-launch window with its Week switch scrolled into view.
  reveal(a,"✓ Тиждень")
  val screenshot=instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("Fresh Week screenshot unavailable")
  try {
   val output=File(a.getExternalFilesDir(null),"task-10").apply { mkdirs() }
   File(output,"fresh-week.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
  } finally { screenshot.recycle() }
 }
 // Catches a render/recreation default overriding an intentional Day choice or selected date.
 fun testDayChoiceAndDateSurviveActualActivityRecreation() {
  var a=start(); val date=chooseDayAndNextWeek(a)
  a=recreate(a); assertDay(a,date)
 }
 // Catches editor/settings navigation resetting the calendar mode or date on return.
 fun testDayChoiceAndDateSurviveEditorAndSettingsReturn() {
  val a=start(); val date=chooseDayAndNextWeek(a)
  click(a,"+ Новий запис")
  await("Appointment editor did not load") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription=="Закінчення * (ГГ:ХХ)" } }
  click(a,"‹ Назад"); calendar(a); assertDay(a,date)
  click(a,"Налаштування")
  await("Settings did not load") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription=="Email (необов’язково)" } }
  click(a,"‹ Назад"); calendar(a); assertDay(a,date)
 }
 override fun tearDown() {
  try { current?.let { a->onMain { if(!a.isDestroyed) a.finish() }; instrumentation.waitForIdleSync() } }
  finally { super.tearDown() }
 }
}
