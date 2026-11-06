package ua.zapys

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import ua.zapys.domain.*
import ua.zapys.ui.CalendarView
import ua.zapys.ui.Theme
import java.time.LocalDate

/** Authored native event-path fixtures. No Activity, repository, DB or messaging is opened. */
class CalendarViewInteractionTest:InstrumentationTestCase() {
 private fun onMain(action:()->Unit) {
  var failure:Throwable?=null
  instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
  failure?.let { throw it }
 }
 private inner class Fixture(week:Boolean=false,status:AppointmentStatus=AppointmentStatus.DRAFT,duration:Int=30) {
  val context=instrumentation.targetContext
  val density=context.resources.displayMetrics.density
  val original=Appointment("fixture",LocalDate.of(2026,11,2),600,600+duration,clientName="Ірина",clientColor=0xff008800.toInt(),status=status)
  val opened=mutableListOf<Appointment>(); val resized=mutableListOf<Appointment>(); val previews=mutableListOf<Appointment?>()
  val dates=if(week) CalendarGeometry.weekDates(original.date) else listOf(original.date)
  val view=CalendarView(context,Theme(context),dates,listOf(original),{ opened.add(it) },{ _,_->fail("Unexpected empty-slot action") },{ resized.add(it) },{ previews.add(it) })
  val parent=FrameLayout(context).apply { addView(view) }
  val bitmap:Bitmap
  val drawn=mutableListOf<String>()
  init {
   val width=((if(week) 2100 else 400)*density).toInt()
   view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
   view.layout(0,0,width,view.measuredHeight)
   // Record draw calls, not screenshots; avoid full-day bitmap allocation on dense devices.
   bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
   view.draw(object:Canvas(bitmap) {
    override fun drawText(text:String,x:Float,y:Float,paint:Paint) { drawn.add(text); super.drawText(text,x,y,paint) }
   })
  }
  val x=100f*density
  val gripY=(CalendarGeometry.top(original.endMinute)-2f)*density
  fun event(action:Int,y:Float=gripY) { val event=MotionEvent.obtain(0L,16L,action,x,y,0); try { assertTrue(view.dispatchTouchEvent(event)) } finally { event.recycle() } }
  fun close() { bitmap.recycle() }
 }
 fun testStationaryGripTapOpensEditor()=onMain {
  val f=Fixture(); try { f.event(MotionEvent.ACTION_DOWN); f.event(MotionEvent.ACTION_UP)
   assertEquals(listOf(f.original),f.opened); assertTrue(f.resized.isEmpty()); assertTrue(f.previews.none { it!=null })
  } finally { f.close() }
 }
 fun testSubSlopGripJitterIsTapWithoutResizePreview()=onMain {
  val f=Fixture(); try { val delta=ViewConfiguration.get(f.context).scaledTouchSlop/2f
   f.event(MotionEvent.ACTION_DOWN); f.event(MotionEvent.ACTION_MOVE,f.gripY+delta); f.event(MotionEvent.ACTION_UP,f.gripY+delta)
   assertEquals(listOf(f.original),f.opened); assertTrue(f.resized.isEmpty()); assertTrue(f.previews.none { it!=null })
  } finally { f.close() }
 }
 fun testDeliberateGripDragEmitsOnlyImmutableCandidateOnRelease()=onMain {
  val f=Fixture(); try { val y=f.gripY+maxOf(40f*f.density,ViewConfiguration.get(f.context).scaledTouchSlop*2f)
   f.event(MotionEvent.ACTION_DOWN); f.event(MotionEvent.ACTION_MOVE,y)
   assertTrue(f.previews.any { it!=null }); assertTrue(f.resized.isEmpty())
   f.event(MotionEvent.ACTION_UP,y)
   assertEquals(1,f.resized.size); assertTrue(f.resized.single().endMinute>f.original.endMinute)
   assertEquals(630,f.original.endMinute); assertTrue(f.opened.isEmpty()); assertNull(f.previews.last())
  } finally { f.close() }
 }
 fun testCancelledDragDiscardsCandidateAndNextGripTapWorks()=onMain {
  val f=Fixture(); try { f.event(MotionEvent.ACTION_DOWN); f.event(MotionEvent.ACTION_MOVE,f.gripY+60f*f.density); f.event(MotionEvent.ACTION_CANCEL)
   assertTrue(f.resized.isEmpty()); assertTrue(f.opened.isEmpty()); assertNull(f.previews.last()); assertEquals(630,f.original.endMinute)
   f.event(MotionEvent.ACTION_DOWN); f.event(MotionEvent.ACTION_UP); assertEquals(listOf(f.original),f.opened)
  } finally { f.close() }
 }
 fun testTallColoredStatusesAndCompactFallbackAreDrawnInDayAndWeek()=onMain {
  for(week in listOf(false,true)) for(status in listOf(AppointmentStatus.DRAFT,AppointmentStatus.PLANNED,AppointmentStatus.COMPLETED,AppointmentStatus.NO_SHOW)) {
   val tall=Fixture(week,status,180)
   try { assertTrue(tall.drawn.any { it.startsWith(status.title+" · ") }); assertEquals((CalendarGeometry.top(1440)*tall.density).toInt(),tall.view.height) } finally { tall.close() }
   val compact=Fixture(week,status,30)
   try {
    val prefix=if(week) when(status) { AppointmentStatus.DRAFT->"Черн."; AppointmentStatus.PLANNED->"План"; AppointmentStatus.COMPLETED->"Вик."; AppointmentStatus.CANCELLED->"Скас."; AppointmentStatus.NO_SHOW->"Неяв." } else status.title
    assertTrue(compact.drawn.any { it.startsWith(prefix+" · ") })
    if(week) assertEquals("Hour rule plus compact event start",2,compact.drawn.count { it=="10:00" })
   } finally { compact.close() }
  }
 }
}
