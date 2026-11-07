package ua.zapys

import android.test.ActivityInstrumentationTestCase2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import ua.zapys.ui.CalendarDateHeader
import ua.zapys.ui.CalendarScreen
import ua.zapys.ui.CalendarView
import ua.zapys.ui.Theme
import java.io.File
import java.time.LocalDate
import kotlin.math.abs

/** Measures the real calendar composition; no repository records are created. */
class CalendarHeaderLayoutTest:ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private fun onMain(action:()->Unit) {
  var failure:Throwable?=null
  instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
  failure?.let { throw it }
 }
 private fun descendants(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList())
 private fun boundsIn(root:ViewGroup,view:View):Rect = Rect(0,0,view.width,view.height).also { root.offsetDescendantRectToMyCoords(view,it) }
 private fun directChild(root:ViewGroup,view:View):View { var child=view; while(child.parent!==root) child=child.parent as View; return child }
 private val boundaryDates=listOf("2026-12-28","2026-12-29","2026-12-30","2026-12-31","2027-01-01","2027-01-02","2027-01-03").map(LocalDate::parse)
 private inner class Fixture(val a:MainActivity,widthDp:Int,val mode:String,date:LocalDate=LocalDate.of(2026,12,30)) {
  val selected=mutableListOf<Pair<LocalDate,String>>()
  val screen=CalendarScreen.create(a,emptyList(),date,mode) { day,nextMode->selected.add(day to nextMode) }
  val dates=descendants(screen).filterIsInstance<Button>().filter { it.contentDescription?.toString()?.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*"))==true }
  val selector=dates.first().parent as ViewGroup
  val headings=descendants(screen).filterIsInstance<TextView>().filter { it !is Button && it.contentDescription?.toString()?.endsWith(", колонка календаря")==true }
  val grid=descendants(screen).filterIsInstance<CalendarView>().single()
  init {
   val width=a.palette.dp(widthDp)
   screen.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(a.palette.dp(900),View.MeasureSpec.EXACTLY))
   screen.layout(0,0,width,screen.measuredHeight)
  }
 }

 // Catches a time-label spacer incorrectly applied to the independent date selector.
 fun testMondayStartsAtCalendarContentEdgeAtAllSupportedWidths() {
  val a=activity
  onMain {
   for(widthDp in listOf(320,360,411)) for(mode in listOf("day","week")) {
    val f=Fixture(a,widthDp,mode)
    assertEquals("$widthDp dp $mode: Monday must start at selector content edge",f.selector.paddingLeft,f.dates.first().left)
    val selectorBounds=boundsIn(f.screen,f.selector)
    assertEquals("Selector must retain normal calendar outer padding",f.screen.paddingLeft,selectorBounds.left)
    assertEquals(f.screen.width-f.screen.paddingRight,selectorBounds.right)
   }
  }
 }
 // Catches unequal weighted cells, a clipped Sunday, or an unwanted day-mode gutter.
 fun testSevenEqualDateCellsFillSelectorIncludingSunday() {
  val a=activity
  onMain { for(widthDp in listOf(320,360,411)) for(mode in listOf("day","week")) {
   val f=Fixture(a,widthDp,mode)
   assertEquals(7,f.dates.size)
   assertEquals("Only seven dates occupy the selector",7,f.selector.childCount)
   assertTrue("Cell widths differ by at most one pixel",f.dates.maxOf { it.width }-f.dates.minOf { it.width }<=1)
   f.dates.zipWithNext().forEach { (left,right)->assertEquals(left.right,right.left) }
   assertEquals(f.selector.width-f.selector.paddingRight,f.dates.last().right)
   if(mode=="day") assertTrue("Day mode has no week-column headings",f.headings.isEmpty())
  } }
 }
 // Catches a missing gutter, wrong dates or headings shifted from the real grid.
 fun testWeekHeadingsMatchGridColumnsAcrossYearBoundary() {
  val a=activity
  onMain { for(widthDp in listOf(320,360,411)) {
   val f=Fixture(a,widthDp,"week")
   assertEquals(7,f.headings.size)
   assertEquals(f.selector.width,f.grid.width)
   val row=f.headings.first().parent as ViewGroup
   assertEquals(boundsIn(f.screen,f.selector).left,boundsIn(f.screen,row).left)
   // Literal 42dp is the approved time-label gutter; expected geometry is independent of header code.
   val gutter=42f*a.resources.displayMetrics.density
   f.headings.forEachIndexed { index,heading->
    assertEquals(boundaryDates[index].toString()+", колонка календаря",heading.contentDescription.toString())
    val expected=gutter+(index+0.5f)*(f.grid.width-gutter)/7f
    val actual=(heading.left+heading.right)/2f
    assertTrue("$widthDp dp column $index center $actual vs $expected",abs(actual-expected)<=1f)
   }
   assertEquals("Compact headings sit immediately before the scrolling timeline",f.screen.indexOfChild(row)+1,f.screen.indexOfChild(directChild(f.screen,f.grid)))
  } }
 }
 // Catches date order or callback regressions at a month/year boundary.
 fun testEveryDateTapOpensTheCorrectDayAcrossYearBoundary() {
  val a=activity
  onMain { for(mode in listOf("day","week")) {
   val f=Fixture(a,320,mode)
   assertEquals(boundaryDates,f.dates.map { LocalDate.parse(it.contentDescription.toString().take(10)) })
   f.dates.forEach { assertTrue(it.performClick()) }
   assertEquals(boundaryDates.map { it to "day" },f.selected)
   assertTrue(f.dates[2].isSelected)
   assertTrue(f.dates[2].contentDescription.toString().contains("обрано"))
   assertEquals(1,f.dates.count { it.isSelected })
  } }
 }
 // Catches loss of today's accessible cue when another date is selected.
 fun testTodayAndSelectedDateRemainDistinct() {
  val a=activity
  onMain {
   val today=LocalDate.now()
   val selected=if(today.dayOfWeek.value==1) today.plusDays(1) else today.minusDays(1)
   val f=Fixture(a,320,"week",selected)
   val todayButton=f.dates.single { it.contentDescription.toString().startsWith(today.toString()) }
   assertFalse(todayButton.isSelected)
   assertTrue(todayButton.contentDescription.toString().contains("сьогодні"))
   assertTrue(todayButton.background is android.graphics.drawable.GradientDrawable)
   assertEquals(1,f.dates.count { it.isSelected })
  }
 }
 // Saves deterministic synthetic rendering evidence and catches text-height clipping at large font sizes.
 fun testRenderLightDarkAndLargeTextHeadersWithoutClipping()=onMain {
  val base=instrumentation.targetContext
  val output=File(base.getExternalFilesDir(null),"task-1").apply { mkdirs() }
  val measurements=StringBuilder()
  for(dark in listOf(false,true)) for(widthDp in listOf(320,360,411)) for(scale in listOf(1f,2f)) for(mode in listOf("day","week")) {
   val config=Configuration(base.resources.configuration).apply {
    uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
    fontScale=scale
   }
   val context=base.createConfigurationContext(config)
   val theme=Theme(context)
   val header=CalendarDateHeader(context,theme,LocalDate.of(2026,12,30),mode) { }
   val root=LinearLayout(context).apply {
    orientation=LinearLayout.VERTICAL; setPadding(theme.dp(12),0,theme.dp(12),0); setBackgroundColor(theme.background)
    addView(header.selector); header.gridHeadings?.let { addView(it) }
    addView(CalendarView(context,theme,if(mode=="week") boundaryDates else listOf(boundaryDates[2]),emptyList(),{},{ _,_-> },{},{ }))
   }
   val width=theme.dp(widthDp); val height=theme.dp(260)
   root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY))
   root.layout(0,0,width,height)
   descendants(header.selector).filterIsInstance<TextView>().forEach { label->
    assertTrue("$widthDp dp $mode font $scale: date text height fits",label.layout.height<=label.height-label.compoundPaddingTop-label.compoundPaddingBottom)
    for(line in 0 until label.layout.lineCount) assertEquals("Date label must not truncate",0,label.layout.getEllipsisCount(line))
   }
   val name="${if(dark) "dark" else "light"}-${widthDp}dp-font$scale-$mode"
   measurements.append(name).append(" selector=").append(header.selector.left).append("..").append(header.selector.right)
   for(i in 0 until header.selector.childCount) {
    val cell=header.selector.getChildAt(i); measurements.append(" cell$i=").append(cell.left).append("..").append(cell.right)
   }
   header.gridHeadings?.let { row->
    for(i in 1 until row.childCount) { val cell=row.getChildAt(i); measurements.append(" heading${i-1}=").append((cell.left+cell.right)/2f) }
   }
   measurements.append('\n')
   val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
   try { root.draw(Canvas(bitmap)); File(output,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
  }
  File(output,"measurements.txt").writeText(measurements.toString())
 }
}
