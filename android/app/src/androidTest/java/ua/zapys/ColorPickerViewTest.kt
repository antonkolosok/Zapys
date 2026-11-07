package ua.zapys

import android.test.InstrumentationTestCase
import android.test.ActivityInstrumentationTestCase2
import android.app.AlertDialog
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Parcelable
import android.os.SystemClock
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.EditText
import ua.zapys.ui.Theme
import ua.zapys.ui.ColorPickerView
import ua.zapys.ui.ColorPaletteDialog
import ua.zapys.ui.CalendarView
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.Appointment
import ua.zapys.domain.ServiceSnapshot
import ua.zapys.domain.AppointmentStatus
import java.time.LocalDate
import java.io.File
import kotlin.math.pow

/** Synthetic shared-control fixtures; never contacts or messaging. */
class ColorPickerViewTest : InstrumentationTestCase() {
 private fun onMain(action: () -> Unit) {
  var failure: Throwable? = null
  instrumentation.runOnMainSync { try { action() } catch (t: Throwable) { failure = t } }
  failure?.let { throw it }
 }
 fun testSharedControlShowsNamedSwatchAndPreservesArbitraryArgbWithoutDirtyCallback() = onMain {
  val parent = LinearLayout(instrumentation.targetContext)
  val changes = mutableListOf<Int?>()
  val value = Theme(parent.context).colors(parent, 0x71345678) { changes.add(it) }
  val control = parent.getChildAt(parent.childCount - 1)
  assertTrue("Shared color control must show a named visual swatch instead of a HEX spinner", control is Button)
  control as Button
  assertFalse(control.text.contains("#"))
  assertTrue("A custom stored color needs a visible swatch", control.compoundDrawablesRelative.any { it != null })
  assertEquals(0x71345678, value())
  assertTrue("Initial rendering must not dirty the form", changes.isEmpty())
 }
}

/** Real Activity, hierarchy restoration and form/database integration on synthetic device data. */
class ColorPickerActivityTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private var current: MainActivity? = null
 private fun onMain(action: () -> Unit) {
  var failure: Throwable? = null
  instrumentation.runOnMainSync { try { action() } catch (t: Throwable) { failure = t } }
  failure?.let { throw it }
 }
 private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup) (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
 private fun await(message: String, test: () -> Boolean) {
  val until = SystemClock.uptimeMillis() + 8000
  do { var yes = false; onMain { yes = test() }; if (yes) return; SystemClock.sleep(20) } while (SystemClock.uptimeMillis() < until)
  fail(message)
 }
 private fun start(): MainActivity {
  val a = activity; current = a
  await("Initial calendar did not load") { views(a.window.decorView).any { it.javaClass.simpleName == "CalendarView" } }
  return a
 }
 private fun dialog(picker: ColorPickerView): ColorPaletteDialog = ColorPickerView::class.java.getDeclaredField("dialog").apply { isAccessible = true }.get(picker) as ColorPaletteDialog
 private fun click(dialog: AlertDialog, text: String) { views(dialog.window!!.decorView).filterIsInstance<android.widget.RadioButton>().single { it.text.toString() == text }.performClick() }
 private fun action(dialog:AlertDialog,which:Int):Button = views(dialog.window!!.decorView).filterIsInstance<Button>().single { it.text.toString()==if(which==AlertDialog.BUTTON_POSITIVE) "Підтвердити" else "Скасувати" }
 private fun press(picker:ColorPickerView,positive:Boolean) {
  onMain { action(dialog(picker),if(positive) AlertDialog.BUTTON_POSITIVE else AlertDialog.BUTTON_NEGATIVE).performClick() }
  instrumentation.waitForIdleSync()
 }
 private fun slider(d: AlertDialog, label: String): SeekBar = views(d.window!!.decorView).filterIsInstance<SeekBar>().single { it.contentDescription == label }
 private fun fixture(a: MainActivity, initial: Int?, changes: MutableList<Int?>): Pair<ColorPickerView, () -> Int?> {
  val p = a.palette.column(); val read = a.palette.colors(p, initial) { changes.add(it) }
  val picker = p.getChildAt(p.childCount - 1) as ColorPickerView; picker.id = 871
  a.show(a.palette.scroll(p)); return picker to read
 }
 fun testEveryPresetPreviewCancelAndConfirmExactlyOnce() {
  val a = start(); val changes = mutableListOf<Int?>(); lateinit var picker:ColorPickerView; lateinit var read:()->Int?
  onMain { val f=fixture(a,0x71345678,changes); picker=f.first; read=f.second }
   for ((label, expected) in listOf("Зелений" to 0xff79ad85.toInt(), "Блакитний" to 0xff91b7dd.toInt(), "Золотистий" to 0xffe3b273.toInt(), "Бузковий" to 0xffc697c3.toInt())) {
    val before = read(); val count=changes.size
    onMain { picker.performClick(); click(dialog(picker),label); assertEquals(before,read()) }
    press(picker,false)
    onMain { assertEquals(before,read()); assertEquals(count,changes.size); picker.performClick(); val confirmed=dialog(picker); click(confirmed,label)
    val checked = views(confirmed.window!!.decorView).filterIsInstance<android.widget.RadioButton>().single { it.text.toString() == label }
    assertTrue(checked.isChecked); assertTrue(checked.isSelected)
    }
    press(picker,true)
    onMain { assertEquals(expected, read()); assertEquals(count + 1, changes.size); assertEquals(expected, changes.last()) }
   }
   onMain { picker.performClick(); click(dialog(picker), "Без кольору"); assertNotNull(read()) }
   press(picker,true)
   onMain { assertNull(read()); assertEquals(5, changes.size) }
 }
 fun testCustomHsvTouchAndNativeAccessibilityControlsCommitOpaqueRgb() {
  val a = start(); lateinit var picker: ColorPickerView; val changes = mutableListOf<Int?>()
  onMain { picker = fixture(a, 0x71345678, changes).first; picker.performClick(); click(dialog(picker), "Власний колір") }
  instrumentation.waitForIdleSync()
  onMain {
   val d = dialog(picker)
   slider(d, "Відтінок").progress = 120; slider(d, "Насиченість").progress = 100; slider(d, "Яскравість").progress = 100
   val area = views(d.window!!.decorView).single { it.javaClass.simpleName == "SaturationValueView" }
   assertTrue(area.width > 0); assertTrue(area.height > 0)
   listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
    val e = MotionEvent.obtain(0, 16, action, area.width.toFloat(), 0f, 0)
    try { assertTrue(area.dispatchTouchEvent(e)) } finally { e.recycle() }
   }
   assertEquals(0x71345678, picker.selectedColor); assertTrue(changes.isEmpty())
  }
  press(picker,true)
  onMain {
   assertEquals(0xff00ff00.toInt(), picker.selectedColor); assertEquals(listOf(0xff00ff00.toInt()), changes)
   picker.performClick()
  }
  press(picker,true)
  onMain { assertEquals("Each explicit confirmation emits once even if unchanged", 2, changes.size) }
 }
 fun testIdentifiedHierarchyRoundTripPreservesColorWithoutCallback() {
  val a = start(); onMain {
   val changes = mutableListOf<Int?>(); val (picker, _) = fixture(a, 0x12345678, changes)
   val state = SparseArray<Parcelable>(); picker.saveHierarchyState(state)
   val restored = fixture(a, null, changes).first; restored.restoreHierarchyState(state)
   assertEquals(0x12345678, restored.selectedColor); assertTrue(changes.isEmpty())
  }
 }
 private fun button(a: MainActivity, text: String): TextView = views(a.window.decorView).filterIsInstance<TextView>().single { it.text.toString() == text }
 private fun openForm(a: MainActivity, client: Boolean): ColorPickerView {
  onMain { button(a, if (client) "Клієнти" else "Послуги").performClick() }
  val add = if (client) "+ Додати клієнта" else "+ Додати послугу"
  await("Catalogue did not load") { views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString() == add } }
  onMain { button(a, add).performClick() }
  await("Form picker missing") { views(a.window.decorView).any { it is ColorPickerView } }
  var picker: ColorPickerView? = null; onMain { picker = views(a.window.decorView).filterIsInstance<ColorPickerView>().single() }
  return picker!!
 }
 private fun recreate(a: MainActivity): MainActivity {
  val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
  try {
   onMain { a.recreate() }
   val next = instrumentation.waitForMonitorWithTimeout(monitor, 8000) as? MainActivity ?: throw AssertionError("Activity was not recreated")
   current = next
   await("Restored form did not load") { views(next.window.decorView).any { it is ColorPickerView } }
   instrumentation.waitForIdleSync(); return next
  } finally { instrumentation.removeMonitor(monitor) }
 }
 private fun picker(a: MainActivity) = views(a.window.decorView).filterIsInstance<ColorPickerView>().single()
 private fun dirty(a: MainActivity) = MainActivity::class.java.getDeclaredField("dirty").apply { isAccessible = true }.getBoolean(a)
 fun testActualActivityRecreationPreservesOpenHsvThenConfirmedFormAndDirtyState() {
  var a = start(); val p = openForm(a, false)
  onMain { assertFalse(dirty(a)); p.performClick(); val d = dialog(p); click(d, "Власний колір"); slider(d, "Відтінок").progress = 240; slider(d, "Насиченість").progress = 50; slider(d, "Яскравість").progress = 80 }
  a = recreate(a)
  onMain {
   val restored = picker(a); val d = dialog(restored)
   assertTrue(d.isShowing); assertEquals(240, slider(d,"Відтінок").progress); assertEquals(50,slider(d,"Насиченість").progress); assertEquals(80,slider(d,"Яскравість").progress)
   assertNull(restored.selectedColor); assertFalse(dirty(a))
  }
  var restored:ColorPickerView?=null; onMain { restored=picker(a) }; press(restored!!,true)
  onMain { assertEquals(0xff6666cc.toInt(),picker(a).selectedColor); assertTrue(dirty(a)) }
  a = recreate(a)
  onMain { assertEquals(0xff6666cc.toInt(),picker(a).selectedColor); assertTrue(dirty(a)) }
 }
 fun testActualActivityRecreationKeepsPendingNullAndLatentHueAndCancelClean() {
  var a = start(); val p = openForm(a, true)
  onMain { p.performClick(); val d=dialog(p); click(d,"Власний колір"); slider(d,"Відтінок").progress=305; slider(d,"Яскравість").progress=0; click(d,"Без кольору") }
  a = recreate(a)
  onMain {
   val d=dialog(picker(a)); assertFalse(dirty(a)); assertFalse(d.draftState().containsKey("color")); assertEquals(305f,d.draftState().getFloatArray("hsv")!![0])
   action(d,AlertDialog.BUTTON_NEGATIVE).performClick()
  }
  instrumentation.waitForIdleSync()
  onMain { assertNull(picker(a).selectedColor); assertFalse(dirty(a)) }
 }
 fun testClientAndServiceCustomChoicesSaveAndReopenWithoutArgbNormalization() {
  val a = start()
  val ids = mutableListOf<Pair<Boolean,String>>()
  try {
   for(client in listOf(true,false)) {
    val p = openForm(a, client); val label = "Task 3 fixture ${if(client) "client" else "service"}"
    onMain {
     val fields=views(a.window.decorView).filterIsInstance<EditText>(); fields[0].setText(label); if(client) fields[1].setText("0671234567")
     p.performClick(); val d=dialog(p); click(d,"Власний колір"); slider(d,"Відтінок").progress=120; slider(d,"Насиченість").progress=100; slider(d,"Яскравість").progress=100
    }
    press(p,true)
    onMain { button(a,if(client) "Зберегти клієнта" else "Зберегти послугу").performClick() }
    await("Save did not leave form") { views(a.window.decorView).none { it is ColorPickerView } }
    val repo=SQLiteRepository(a); val id=try {
     if(client) repo.clients().single { it.firstName==label }.also { assertEquals(0xff00ff00.toInt(),it.color) }.id
     else repo.services().single { it.name==label }.also { assertEquals(0xff00ff00.toInt(),it.color) }.id
    } finally { repo.close() }
    ids.add(client to id)
    onMain { views(a.window.decorView).filterIsInstance<Button>().single { it.text.startsWith(label+"\n") }.performClick() }
    await("Saved form did not reopen") { views(a.window.decorView).any { it is ColorPickerView } }
    onMain { assertEquals(0xff00ff00.toInt(),picker(a).selectedColor); assertFalse(dirty(a)) }
    // Seed an existing arbitrary ARGB, then reopen and save an unrelated edit.
    onMain { button(a,"‹ Назад").performClick() }
    await("Form did not close") { views(a.window.decorView).none { it is ColorPickerView } }
    val seed=SQLiteRepository(a); try { if(client) seed.saveClient(seed.clients().single { it.id==id }.copy(color=0x71345678)) else seed.saveService(seed.services().single { it.id==id }.copy(color=0x71345678)) } finally { seed.close() }
    onMain { views(a.window.decorView).filterIsInstance<Button>().single { it.text.startsWith(label+"\n") }.performClick() }
    // Catalogue has a snapshot, so reload first via its navigation button.
    await("Existing form missing") { views(a.window.decorView).any { it is ColorPickerView } }
    onMain { assertEquals(0x71345678,picker(a).selectedColor); button(a,if(client) "Зберегти клієнта" else "Зберегти послугу").performClick() }
    await("Unchanged ARGB save did not complete") { views(a.window.decorView).none { it is ColorPickerView } }
    val reopened=SQLiteRepository(a); try { assertEquals(0x71345678,if(client) reopened.clients().single { it.id==id }.color else reopened.services().single { it.id==id }.color) } finally { reopened.close() }
   }
  } finally { val repo=SQLiteRepository(a); try { ids.forEach { (client,id)->if(client) repo.deleteClient(id) else repo.deleteService(id) } } finally { repo.close() } }
 }
 private fun export(a: MainActivity, name: String, view: View) {
  assertTrue("Unlaid-out screenshot $name",view.width>0 && view.height>0)
  val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
  try { view.draw(Canvas(bitmap)); save(a,name,bitmap) } finally { bitmap.recycle() }
 }
 private fun save(a:MainActivity,name:String,bitmap:Bitmap) {
  val dir=File(a.getExternalFilesDir(null),"task-3"); check(dir.isDirectory || dir.mkdirs())
  File(dir,"$name.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
 }
 private fun luminance(color:Int):Double {
  fun channel(c:Int):Double { val s=c/255.0; return if(s<=0.04045) s/12.92 else ((s+0.055)/1.055).pow(2.4) }
  return 0.2126*channel(Color.red(color))+0.7152*channel(Color.green(color))+0.0722*channel(Color.blue(color))
 }
 fun testLargeTextDialogActionsRemainFullyVisible() {
  val a=start(); lateinit var p:ColorPickerView
  onMain {
   val config=Configuration(a.resources.configuration).apply { fontScale=1.6f }
   val context=ContextThemeWrapper(a,android.R.style.Theme_Material_Light_NoActionBar).apply { applyOverrideConfiguration(config) }
   val t=Theme(context); val column=t.column(); t.colors(column,0xff345678.toInt()) {}; p=column.getChildAt(column.childCount-1) as ColorPickerView
   a.show(t.scroll(column)); p.performClick()
  }
  instrumentation.waitForIdleSync()
  val screen=instrumentation.uiAutomation.takeScreenshot() ?: throw AssertionError("No live window screenshot")
  val screenHeight=screen.height
  try { save(a,"large-live-screen",screen) } finally { screen.recycle() }
  onMain {
   val d=dialog(p)
   for(which in listOf(AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE)) {
    val b=action(d,which); val visible=android.graphics.Rect()
    assertTrue("Dialog action must be visible: ${b.text}",b.getGlobalVisibleRect(visible))
    assertEquals("Dialog action must not be clipped vertically: ${b.text}",b.height,visible.height())
    assertEquals("Dialog action must not be clipped horizontally: ${b.text}",b.width,visible.width())
    val pos=IntArray(2); b.getLocationOnScreen(pos)
    assertTrue("Dialog action lies outside physical screen: ${b.text}, y=${pos[1]}, h=${b.height}, screen=$screenHeight",pos[1]>=0 && pos[1]+b.height<=screenHeight)
    val layout=b.layout
    assertTrue("Dialog action text clips inside its own frame: ${b.text}, textBottom=${layout.getLineBottom(layout.lineCount-1)}, textArea=${b.height-b.compoundPaddingTop-b.compoundPaddingBottom}",layout.getLineBottom(layout.lineCount-1)<=b.height-b.compoundPaddingTop-b.compoundPaddingBottom)
   }
  }
 }
 fun testDarkUnselectedRadioIndicatorHasVisibleContrast() {
  val a=start(); lateinit var p:ColorPickerView
  onMain {
   val config=Configuration(a.resources.configuration).apply { uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES }
   val context=ContextThemeWrapper(a,android.R.style.Theme_Material_Light_NoActionBar).apply { applyOverrideConfiguration(config) }
   val t=Theme(context); val column=t.column(); t.colors(column,null) {}; p=column.getChildAt(column.childCount-1) as ColorPickerView
   a.show(t.scroll(column)); p.performClick()
  }
  instrumentation.waitForIdleSync()
  onMain {
   val d=dialog(p); val radio=views(d.window!!.decorView).filterIsInstance<android.widget.RadioButton>().first { !it.isChecked }
   val drawable=radio.buttonDrawable!!; val old=android.graphics.Rect(drawable.bounds)
   val bitmap=Bitmap.createBitmap(drawable.intrinsicWidth,drawable.intrinsicHeight,Bitmap.Config.ARGB_8888)
   val bg=Theme(p.context).background
   try {
    val canvas=Canvas(bitmap); canvas.drawColor(bg); drawable.setBounds(0,0,bitmap.width,bitmap.height); drawable.draw(canvas)
    val bgl=luminance(bg); var best=1.0
    for(y in 0 until bitmap.height) for(x in 0 until bitmap.width) { val l=luminance(bitmap.getPixel(x,y)); best=maxOf(best,(maxOf(l,bgl)+0.05)/(minOf(l,bgl)+0.05)) }
    assertTrue("Unchecked radio ring needs contrast 3 against dark background; actual $best",best>=3)
   } finally { drawable.bounds=old; bitmap.recycle() }
  }
 }
 fun testNamedLightDarkLargeTextPaletteAndBlackWhiteArbitraryGradientFixtures() {
  val a=start()
  for(night in listOf(false,true)) for(large in listOf(false,true)) {
   val name="${if(night) "dark" else "light"}-${if(large) "large" else "normal"}"
   lateinit var p:ColorPickerView
   onMain {
    val config=Configuration(a.resources.configuration).apply { fontScale=if(large) 1.6f else 1f; uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if(night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
    val context=ContextThemeWrapper(a,android.R.style.Theme_Material_Light_NoActionBar).apply { applyOverrideConfiguration(config) }
    val theme=Theme(context); val column=theme.column(); theme.colors(column,0xff345678.toInt()) {}; p=column.getChildAt(column.childCount-1) as ColorPickerView
    a.show(theme.scroll(column)); p.performClick()
   }
   instrumentation.waitForIdleSync()
   onMain {
    val d=dialog(p); export(a,"$name-palette",d.window!!.decorView)
    click(d,"Власний колір")
   }
   instrumentation.waitForIdleSync()
   onMain {
    val d=dialog(p)
    val area=views(d.window!!.decorView).single { it.javaClass.simpleName=="SaturationValueView" }
    area.requestRectangleOnScreen(android.graphics.Rect(0,0,area.width,area.height),true)
   }
   instrumentation.waitForIdleSync()
   onMain {
    val d=dialog(p); export(a,"$name-custom",d.window!!.decorView); action(d,AlertDialog.BUTTON_NEGATIVE).performClick()
    val context=p.context; val theme=Theme(context)
    val date=LocalDate.of(2032,1,2)
    val event=Appointment(id="task3-gradient",date=date,startMinute=0,endMinute=180,clientName="Fixture black",clientColor=Color.BLACK,status=AppointmentStatus.PLANNED,
     services=listOf(ServiceSnapshot(serviceId="white",name="Fixture white",color=Color.WHITE),ServiceSnapshot(serviceId="arbitrary",name="Fixture arbitrary",color=0xff345678.toInt())))
    val view=CalendarView(context,theme,listOf(date),listOf(event),{}, { _,_-> },{}, {})
    val width=theme.dp(400); val height=theme.dp(240)
    view.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED)); view.layout(0,0,width,view.measuredHeight)
    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    var backing:Int?=null; var labels=0; var gradient=false
    val canvas=object:Canvas(bitmap) {
     override fun drawRoundRect(rect:RectF,rx:Float,ry:Float,paint:Paint) { if(paint.shader!=null) gradient=true; super.drawRoundRect(rect,rx,ry,paint) }
     override fun drawRect(left:Float,top:Float,right:Float,bottom:Float,paint:Paint) { backing=paint.color; super.drawRect(left,top,right,bottom,paint) }
     override fun drawText(text:String,x:Float,y:Float,paint:Paint) {
      if(text.startsWith("Fixture")) {
       assertEquals(theme.surface,backing); assertEquals(theme.ink,paint.color); assertEquals(255,Color.alpha(backing!!)); labels++
       val fg=luminance(paint.color); val bg=luminance(backing!!); assertTrue("$name label contrast below 4.5",(maxOf(fg,bg)+0.05)/(minOf(fg,bg)+0.05)>=4.5)
      }; super.drawText(text,x,y,paint)
     }
    }
    try { canvas.drawColor(theme.background); view.draw(canvas); assertTrue("Missing multicolor gradient",gradient); assertEquals(3,labels); save(a,"$name-gradient",bitmap) } finally { bitmap.recycle() }
   }
  }
 }
 override fun tearDown() { current?.let { onMain { it.finish() } }; super.tearDown() }
}
