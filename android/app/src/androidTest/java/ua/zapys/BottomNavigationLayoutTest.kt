package ua.zapys

import android.test.ActivityInstrumentationTestCase2
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import ua.zapys.ui.BottomNavigation
import ua.zapys.ui.Theme
import java.io.File
import kotlin.math.abs

/** Synthetic layout and interaction checks; no client or appointment records are created. */
class BottomNavigationLayoutTest:ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private fun onMain(action:()->Unit) {
  var failure:Throwable?=null
  instrumentation.runOnMainSync { try { action() } catch(t:Throwable) { failure=t } }
  failure?.let { throw it }
 }
 private fun descendants(view:View):List<View> = listOf(view)+(if(view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList())
 private fun activityNavigation():LinearLayout = descendants(activity.findViewById(android.R.id.content)).filterIsInstance<LinearLayout>().single { row->
  row.childCount==4 && (0 until row.childCount).all { row.getChildAt(it) is Button } && (row.getChildAt(3) as Button).text.toString()=="Налаштування"
 }
 private fun measure(nav:LinearLayout,width:Int) {
  nav.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
  nav.layout(0,0,width,nav.measuredHeight)
 }
 private fun assertReadableAlignedItems(nav:LinearLayout,theme:Theme,case:String) {
  val items=(0 until nav.childCount).map { nav.getChildAt(it) as Button }
  assertEquals(4,items.size)
  assertTrue("$case: equal widths",items.maxOf { it.width }-items.minOf { it.width }<=1)
  assertEquals(0,items.first().left)
  assertEquals(nav.width,items.last().right)
  items.zipWithNext().forEach { (left,right)->assertEquals(left.right,right.left) }
  items.forEach { item->
   assertEquals("$case ${item.text}: common top",items.first().top,item.top)
   assertEquals("$case ${item.text}: common bottom",items.first().bottom,item.bottom)
   assertTrue("$case: touch height",item.height>=theme.dp(48))
   assertTrue("$case: clickable",item.isClickable && item.isEnabled)
   val text=item.layout
   val availableWidth=item.width-item.compoundPaddingLeft-item.compoundPaddingRight
   assertTrue("$case ${item.text}: complete text height fits",text.height<=item.height-item.compoundPaddingTop-item.compoundPaddingBottom)
   assertEquals("$case: full label is laid out",item.text.length,text.getLineEnd(text.lineCount-1))
   for(line in 0 until text.lineCount) {
    assertEquals("$case: no ellipsis",0,text.getEllipsisCount(line))
    assertTrue("$case ${item.text}: line $line fits",text.getLineWidth(line)<=availableWidth+1f)
   }
   assertTrue("$case ${item.text}: text vertically centered",abs(item.totalPaddingTop+text.height/2f-item.height/2f)<=1f)
  }
 }

 // Catches unequal wrap-content item heights or baseline shifts when Settings wraps.
 fun testActivityNavigationItemsShareBoundsAtNarrowWidths() {
  val a=activity
  onMain { for(widthDp in listOf(320,360,411)) {
   val nav=activityNavigation()
   val width=a.palette.dp(widthDp)
   measure(nav,width)
   val items=(0 until nav.childCount).map { nav.getChildAt(it) as Button }
   items.forEach { item->
    assertEquals("$widthDp dp ${item.text}: common top",items.first().top,item.top)
    assertEquals("$widthDp dp ${item.text}: common bottom",items.first().bottom,item.bottom)
    assertTrue("Touch target is at least 48dp high",item.height>=a.palette.dp(48))
   }
  } }
 }

 // Catches clipping, ellipsis, unequal targets, or text shifted off-center at enlarged font scales.
 fun testAllLabelsFitSupportedWidthsFontScalesAndThemes()=onMain {
  val base=instrumentation.targetContext
  val output=File(base.getExternalFilesDir(null),"task-2").apply { mkdirs() }
  val measurements=StringBuilder()
  for(dark in listOf(false,true)) for(widthDp in listOf(320,360,411,640,720,822)) for(scale in listOf(1f,1.3f,2f)) {
   val configuration=Configuration(base.resources.configuration).apply {
    fontScale=scale
    uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
   }
   val context=base.createConfigurationContext(configuration)
   val theme=Theme(context)
   val nav=BottomNavigation.create(context,theme,"settings") { }
   measure(nav,theme.dp(widthDp))
   val name="${if(dark) "dark" else "light"}-${widthDp}dp-font$scale"
   assertReadableAlignedItems(nav,theme,name)
   measurements.append(name).append(" height=").append(nav.height).append(" scaledTextPx=").append((nav.getChildAt(0) as Button).textSize).append('\n')
   val bitmap=Bitmap.createBitmap(nav.width,nav.height,Bitmap.Config.ARGB_8888)
   try { nav.draw(Canvas(bitmap)); File(output,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
  }
  File(output,"measurements.txt").writeText(measurements.toString())
 }

 // Catches wrong route keys or selected cues lost when navigation is recreated after a route change.
 fun testEveryDestinationCallbackAndSelectedStateSurvivesRouteChanges() {
  val a=activity
  onMain {
   val routes=listOf("calendar","clients","services","settings")
   val labels=listOf("Календар","Клієнти","Послуги","Налаштування")
   for(route in routes+listOf("clientForm","serviceForm")) {
    val selected=mutableListOf<String>()
    val nav=BottomNavigation.create(a,a.palette,route) { selected.add(it) }
    measure(nav,a.palette.dp(320))
    val items=(0 until nav.childCount).map { nav.getChildAt(it) as Button }
    assertEquals(labels,items.map { it.text.toString() })
    val expected=when(route) { "clientForm"->"clients"; "serviceForm"->"services"; else->route }
    assertEquals(1,items.count { it.isSelected })
    items.forEachIndexed { index,item->
     assertEquals(routes[index]==expected,item.isSelected)
     assertEquals(labels[index]+if(item.isSelected) ", обрано" else "",item.contentDescription.toString())
     assertEquals(if(item.isSelected) a.palette.onAccent else a.palette.ink,item.currentTextColor)
     assertTrue(item.performClick())
    }
    assertEquals(routes,selected)
   }
  }
 }

 // Run attached in portrait/landscape with gesture/three-button navigation to catch system-bar overlap.
 fun testAttachedNavigationStaysInsideSystemInsets() {
  val a=activity
  instrumentation.waitForIdleSync()
  var name=""
  onMain {
   val nav=activityNavigation()
   val root=nav.parent as ViewGroup
   val insets=root.rootWindowInsets
   assertNotNull("Real window insets must be delivered",insets)
   assertTrue(root.paddingBottom>=insets.systemWindowInsetBottom)
   assertTrue(root.paddingLeft>=insets.systemWindowInsetLeft)
   assertTrue(root.paddingRight>=insets.systemWindowInsetRight)
   assertEquals(root.height-root.paddingBottom,nav.bottom)
   assertEquals(root.paddingLeft,nav.left)
   assertEquals(root.width-root.paddingRight,nav.right)
   assertReadableAlignedItems(nav,a.palette,"attached")
   val visible=Rect()
   assertTrue(nav.getGlobalVisibleRect(visible))
   assertEquals("Every navigation target remains fully visible",nav.height,visible.height())
   name="attached-${a.resources.configuration.orientation}-${insets.systemWindowInsetLeft}-${insets.systemWindowInsetRight}-${insets.systemWindowInsetBottom}"
   val output=File(a.getExternalFilesDir(null),"task-2").apply { mkdirs() }
   File(output,"$name.txt").writeText("root=${root.width}x${root.height} padding=${root.paddingLeft},${root.paddingTop},${root.paddingRight},${root.paddingBottom} nav=${nav.left},${nav.top},${nav.right},${nav.bottom}")
  }
  val screenshot=instrumentation.uiAutomation.takeScreenshot()
  assertNotNull("Capture real window for visual review",screenshot)
  try { File(a.getExternalFilesDir(null),"task-2/$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { screenshot.recycle() }
 }
}
