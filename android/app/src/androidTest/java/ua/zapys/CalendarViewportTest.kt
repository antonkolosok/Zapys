package ua.zapys

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ScrollView
import ua.zapys.ui.CalendarView
import java.io.File

/** Runs in the actual Activity body after real system insets and bottom navigation. */
class CalendarViewportTest:ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private var current:MainActivity?=null
 private fun onMain(action:()->Unit) { var failure:Throwable?=null; instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }; failure?.let { throw it } }
 private fun views(v:View):List<View> = listOf(v)+if(v is ViewGroup) (0 until v.childCount).flatMap { views(v.getChildAt(it)) } else emptyList()
 fun testLandscapeTimelineAndNewAppointmentRemainReachableAtCurrentFontScale() {
  var a=activity; current=a
  val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
  try {
   onMain { a.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
   val next=instrumentation.waitForMonitorWithTimeout(monitor,2500) as? MainActivity
   if(next!=null) { a=next; current=a }
  } finally { instrumentation.removeMonitor(monitor) }
  val end=SystemClock.uptimeMillis()+8000
  var ready=false
  do { onMain { ready=views(a.window.decorView).any { it is CalendarView && it.width>0 } }; if(ready) break; SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<end)
  assertTrue("Calendar did not lay out after rotation",ready); instrumentation.waitForIdleSync()
  onMain {
   val grid=views(a.window.decorView).filterIsInstance<CalendarView>().single()
   val scroll=grid.parent.parent as ScrollView
   val new=views(a.window.decorView).filterIsInstance<Button>().single { it.text.toString()=="+ Новий запис" }
   val visible=Rect()
   assertTrue("New appointment is clipped outside the actual body",new.getGlobalVisibleRect(visible))
   assertEquals("Complete New appointment action must fit the viewport",new.height,visible.height())
   assertTrue("Enlarged action text must remain fully readable",new.layout.height<=new.height-new.compoundPaddingTop-new.compoundPaddingBottom)
   assertTrue("Timeline needs a usable visible touch region",grid.getLocalVisibleRect(visible) && visible.height()>=a.palette.dp(48))
   val initial=scroll.scrollY
   scroll.scrollTo(0,0); assertEquals("Early records are reachable",0,scroll.scrollY)
   scroll.scrollTo(0,grid.height-scroll.height); assertTrue("Late records are reachable",scroll.scrollY>initial)
   scroll.scrollTo(0,initial)
   val output=File(a.getExternalFilesDir(null),"task-9").apply { mkdirs() }
   val root=a.window.decorView
   val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888)
   try { root.draw(Canvas(bitmap)); File(output,"landscape-font${a.resources.configuration.fontScale}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
   assertTrue(new.performClick())
  }
  var opened=false
  val editorEnd=SystemClock.uptimeMillis()+8000
  do { onMain { opened=views(a.window.decorView).any { it.contentDescription=="Закінчення * (ГГ:ХХ)" } }; if(opened) break; SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<editorEnd)
  assertTrue("Reachable action must actually open the new appointment editor",opened)
 }
 override fun tearDown() { try { current?.let { a->onMain { a.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; a.finish() }; instrumentation.waitForIdleSync() } } finally { super.tearDown() } }
}
