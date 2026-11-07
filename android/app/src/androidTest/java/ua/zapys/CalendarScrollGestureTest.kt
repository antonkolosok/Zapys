package ua.zapys

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.test.ActivityInstrumentationTestCase2
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.InputDevice
import android.widget.FrameLayout
import android.widget.ScrollView
import ua.zapys.domain.*
import ua.zapys.ui.CalendarView
import java.time.LocalDate

/** Events go through an attached, real ScrollView, including parent interception. */
class CalendarScrollGestureTest:ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private fun onMain(action:()->Unit) { var failure:Throwable?=null; instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }; failure?.let { throw it } }
 private fun await(message:String,condition:()->Boolean) {
  val deadline=SystemClock.uptimeMillis()+5000
  do { var ready=false; onMain { ready=condition() }; if(ready) return; SystemClock.sleep(20) } while(SystemClock.uptimeMillis()<deadline)
  fail(message)
 }
 private fun hasGrid(v:View):Boolean = v is CalendarView || v is ViewGroup && (0 until v.childCount).any { hasGrid(v.getChildAt(it)) }
 private inner class Fixture(val week:Boolean=false) {
  val a=activity
  val density=a.resources.displayMetrics.density
  val original=Appointment("gesture",LocalDate.of(2026,12,31),607,667,clientName="Synthetic",manualEnd=true)
  val dates=if(week) CalendarGeometry.weekDates(original.date) else listOf(original.date)
  val opened=mutableListOf<Appointment>(); val moved=mutableListOf<Appointment>(); val resized=mutableListOf<Appointment>(); val previews=mutableListOf<Appointment?>()
  lateinit var grid:CalendarView; lateinit var scroll:ScrollView
  var downTime=0L
  var x=0f; var y=0f
  init {
   // Finish MainActivity's initial asynchronous render before installing the event fixture.
   await("Initial calendar not ready") { hasGrid(a.window.decorView) }; instrumentation.waitForIdleSync()
   onMain {
    grid=CalendarView(a,a.palette,dates,listOf(original),{opened.add(it)},{_,_->fail("Unexpected empty action")},{resized.add(it)},{previews.add(it)},move={moved.add(it)})
    scroll=ScrollView(a).apply { addView(grid) }
    a.show(FrameLayout(a).apply { addView(scroll,FrameLayout.LayoutParams(-1,a.palette.dp(300))) })
   }
   await("Fixture not laid out") { grid.width>0 && scroll.height>0 }
   onMain {
    scroll.scrollTo(0,(CalendarGeometry.top(540)*density).toInt())
    val bitmap=Bitmap.createBitmap(1,1,Bitmap.Config.ARGB_8888)
    try { grid.draw(Canvas(bitmap)) } finally { bitmap.recycle() }
    val gutter=42f*density
    x=gutter+(dates.indexOf(original.date)+0.5f)*(grid.width-gutter)/dates.size
    y=CalendarGeometry.top(original.startMinute)*density-scroll.scrollY+12f*density
   }
  }
  fun event(action:Int,px:Float=x,py:Float=y) {
   onMain { if(action==MotionEvent.ACTION_DOWN) downTime=SystemClock.uptimeMillis()
    val e=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),action,px,py,0)
    try { scroll.dispatchTouchEvent(e) } finally { e.recycle() }
   }
  }
  fun hold() { event(MotionEvent.ACTION_DOWN); SystemClock.sleep(ViewConfiguration.getLongPressTimeout().toLong()+80); instrumentation.waitForIdleSync() }
  fun extraPointer()=onMain {
   val properties=Array(2) { index->MotionEvent.PointerProperties().apply { id=index; toolType=MotionEvent.TOOL_TYPE_FINGER } }
   val coordinates=Array(2) { index->MotionEvent.PointerCoords().apply { x=this@Fixture.x+index*20*density; y=this@Fixture.y+36*density; pressure=1f; size=1f } }
   val e=MotionEvent.obtain(downTime,SystemClock.uptimeMillis(),MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),2,properties,coordinates,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
   try { scroll.dispatchTouchEvent(e); e.action=MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT); scroll.dispatchTouchEvent(e) } finally { e.recycle() }
  }
  fun assertUntouched()=onMain { assertTrue(moved.isEmpty()); assertTrue(resized.isEmpty()); assertTrue(opened.isEmpty()); assertEquals(607,original.startMinute) }
 }
 fun testShortBodyTapAndSubSlopJitterOpenOnce() {
  val f=Fixture(); f.event(MotionEvent.ACTION_DOWN); f.event(MotionEvent.ACTION_MOVE,py=f.y+2); f.event(MotionEvent.ACTION_UP,py=f.y+2)
  onMain { assertEquals(listOf(f.original),f.opened); assertTrue(f.moved.isEmpty()); assertTrue(f.previews.none { it!=null }) }
 }
 fun testOrdinaryBodySwipeScrollsParentWithoutMove() {
  val f=Fixture(); val initial=f.scroll.scrollY
  f.event(MotionEvent.ACTION_DOWN); f.event(MotionEvent.ACTION_MOVE,py=f.y-60*f.density); f.event(MotionEvent.ACTION_MOVE,py=f.y-90*f.density); f.event(MotionEvent.ACTION_UP,py=f.y-90*f.density)
  onMain { assertTrue("ScrollView must own the ordinary swipe",f.scroll.scrollY>initial) }; f.assertUntouched()
 }
 fun testLongPressBodyPreviewsThenEmitsOnlyMoveOnRelease() {
  for(week in listOf(false,true)) {
   val f=Fixture(week); f.hold()
   onMain { assertEquals("Activation must retain finger offset",f.original,f.previews.lastOrNull()); assertTrue(f.moved.isEmpty()) }
   f.event(MotionEvent.ACTION_MOVE,py=f.y+36*f.density)
   onMain { assertEquals(f.original.copy(startMinute=637,endMinute=697),f.previews.last()); assertTrue(f.moved.isEmpty()); assertTrue(f.resized.isEmpty()) }
   f.event(MotionEvent.ACTION_UP,py=f.y+36*f.density)
   onMain { assertEquals(listOf(f.original.copy(startMinute=637,endMinute=697)),f.moved); assertTrue(f.opened.isEmpty()); assertNull(f.previews.last()) }
  }
 }
 fun testHorizontalMovePreservesOddMinuteAcrossYearBoundary() {
  val f=Fixture(true); f.hold(); val next=f.x+(f.grid.width-42*f.density)/7f
  f.event(MotionEvent.ACTION_MOVE,px=next); f.event(MotionEvent.ACTION_UP,px=next)
  onMain { assertEquals(listOf(f.original.copy(date=LocalDate.of(2027,1,1))),f.moved); assertTrue(f.resized.isEmpty()) }
 }
 fun testNoOpCancelOutsideGridGutterAndExtraPointerDoNotMoveAndNextTapWorks() {
  for(kind in listOf("noop","cancel","outside","gutter","pointer")) {
   val f=Fixture(); f.hold()
   if(kind!="noop") f.event(MotionEvent.ACTION_MOVE,py=f.y+36*f.density)
   when(kind) {
    "cancel"->f.event(MotionEvent.ACTION_CANCEL)
    "outside"->f.event(MotionEvent.ACTION_UP,px=f.grid.width+10f)
    "gutter"->f.event(MotionEvent.ACTION_UP,px=10f)
    "pointer"->{ f.extraPointer(); f.event(MotionEvent.ACTION_UP,py=f.y+36*f.density) }
    else->f.event(MotionEvent.ACTION_UP)
   }
   f.assertUntouched(); onMain { assertNull(f.previews.lastOrNull()) }
   f.event(MotionEvent.ACTION_DOWN); f.event(MotionEvent.ACTION_UP)
   onMain { assertEquals("$kind cleanup restores tapping",listOf(f.original),f.opened) }
  }
 }
 fun testEdgeScrollTracksStationaryFingerAndStopsAfterCancellation() {
  val f=Fixture(); f.hold(); val before=f.scroll.scrollY
  f.event(MotionEvent.ACTION_MOVE,py=f.scroll.height-8*f.density)
  var initialPreview=0; onMain { initialPreview=f.previews.last()?.startMinute ?: -1 }
  await("Active bottom-edge drag did not scroll") { f.scroll.scrollY>before+20*f.density }
  onMain { assertTrue("Preview must follow scrolled content without pointer jump",f.previews.last()!!.startMinute>initialPreview) }
  f.event(MotionEvent.ACTION_CANCEL); var stopped=0; onMain { stopped=f.scroll.scrollY }
  SystemClock.sleep(150); onMain { assertEquals("Cancelled edge loop continued",stopped,f.scroll.scrollY) }; f.assertUntouched()
 }
 fun testUpperEdgeScrollReachesEarlierTimesAndDetachCancels() {
  val f=Fixture(); f.hold(); val before=f.scroll.scrollY
  f.event(MotionEvent.ACTION_MOVE,py=8*f.density)
  await("Active top-edge drag did not scroll") { f.scroll.scrollY<before-20*f.density }
  onMain { f.a.show(FrameLayout(f.a)) }
  SystemClock.sleep(100); f.assertUntouched(); onMain { assertNull(f.previews.lastOrNull()) }
 }
}
