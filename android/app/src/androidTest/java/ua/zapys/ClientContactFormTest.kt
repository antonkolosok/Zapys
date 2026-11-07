package ua.zapys

import android.app.Activity
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.SystemClock
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.test.ActivityInstrumentationTestCase2
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import ua.zapys.data.SQLiteRepository
import ua.zapys.device.ContactPicker
import ua.zapys.domain.*
import ua.zapys.ui.ClientsScreen
import ua.zapys.ui.ColorPaletteDialog
import ua.zapys.ui.ColorPickerView
import ua.zapys.ui.Theme
import java.io.File

/** Synthetic forms only: no real contacts authority is queried or populated. */
class ClientContactFormTest : ActivityInstrumentationTestCase2<MainActivity>(MainActivity::class.java) {
 private var current:MainActivity?=null
 private var beforeClients=emptyList<Client>()
 private var snapshotTaken=false
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
 private fun start():MainActivity {
  val a=activity; current=a
  await("Calendar did not load") { views(a.window.decorView).any { it.javaClass.simpleName=="CalendarView" } }
  SQLiteRepository(a).use { beforeClients=it.clients(); snapshotTaken=true }
  return a
 }
 private fun openForm(a:MainActivity,original:Client?=null) {
  original?.let { SQLiteRepository(a).use { repo->repo.saveClient(it) } }
  onMain { button(a,"Клієнти").performClick() }
  val action=original?.let { "${it.displayName}\n${it.phone}" } ?: "+ Додати клієнта"
  await("Clients did not load") { views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()==action } }
  onMain { button(a,action).performClick() }
  await("Client form did not load") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription=="Телефон *" } }
  instrumentation.waitForIdleSync()
 }
 private fun assertContactAtTop(a:MainActivity,heading:String)=onMain {
  val contact=button(a,"Обрати телефон з контактів")
  val parent=contact.parent as ViewGroup
  assertEquals("Back remains first",0,parent.indexOfChild(button(a,"‹ Назад")))
  assertEquals("Heading follows back",1,parent.indexOfChild(button(a,heading)))
  assertEquals("Contact action must immediately follow heading and precede client fields",2,parent.indexOfChild(contact))
  assertTrue("Contact action must precede name",contact.bottom<=field(a,"Ім’я *").top)
  assertTrue("Contact action must precede phone",contact.bottom<=field(a,"Телефон *").top)
  assertTrue("Contact action is available",contact.isEnabled && contact.isClickable)
 }
 private fun privateValue(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(a)
 private fun setPrivate(a:MainActivity,name:String,value:Any?) { MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.set(a,value) }
 private fun identity(a:MainActivity)=MainActivity::class.java.getDeclaredMethod("formIdentity").apply { isAccessible=true }.invoke(a)
 private fun applySelected(a:MainActivity,name:String,phone:String) {
  // Decoded result boundary: preserve the real ownership/readiness gate and bound form fields.
  setPrivate(a,"pendingContact",name to phone)
  MainActivity::class.java.getDeclaredMethod("applyContact").apply { isAccessible=true }.invoke(a)
 }
 private fun deliverResult(a:MainActivity,result:Int,data:Intent?=null) {
  MainActivity::class.java.getDeclaredMethod("onActivityResult",Int::class.javaPrimitiveType,Int::class.javaPrimitiveType,Intent::class.java).apply { isAccessible=true }.invoke(a,ContactPicker.REQUEST,result,data)
 }
 private fun beginSyntheticPick(a:MainActivity) {
  val filter=IntentFilter(Intent.ACTION_PICK).apply { addDataType(Phone.CONTENT_TYPE) }
  val monitor=instrumentation.addMonitor(filter,null,true)
  try {
   onMain { assertTrue(button(a,"Обрати телефон з контактів").performClick()) }
   instrumentation.waitForIdleSync()
   assertEquals("Exactly the selective phone picker was requested and blocked",1,monitor.hits)
   onMain { assertEquals(identity(a),privateValue(a,"contactOwner")) }
  } finally { instrumentation.removeMonitor(monitor) }
 }
 private fun picker(a:MainActivity)=views(a.window.decorView).filterIsInstance<ColorPickerView>().single()
 private fun colorDialog(a:MainActivity)=ColorPickerView::class.java.getDeclaredField("dialog").apply { isAccessible=true }.get(picker(a)) as ColorPaletteDialog
 private fun metadata()=Client(firstName="Task 6 original synthetic",phone="0670000602",surname=" Прізвище ",patronymic=" По батькові ",note="  Synthetic note\nretained  ",color=0x71345678,
  reminder=ReminderPreferences(true,Channel.TELEGRAM," raw recipient ",37," raw [ім’я] template "))
 private fun assertMetadata(a:MainActivity,c:Client) {
  assertEquals(c.surname,field(a,"Прізвище (необов’язково)").text.toString())
  assertEquals(c.patronymic,field(a,"По батькові (необов’язково)").text.toString())
  assertEquals(c.note,field(a,"Нотатка (текст, до ${Limits.MAX_NOTE} символів)").text.toString())
  assertEquals(c.color,picker(a).selectedColor)
  assertEquals(c.reminder.enabled,views(a.window.decorView).filterIsInstance<Switch>().single().isChecked)
  assertEquals(c.reminder.channel.ordinal,views(a.window.decorView).filterIsInstance<Spinner>().single().selectedItemPosition)
  assertEquals(c.reminder.recipient,field(a,"Одержувач у месенджері").text.toString())
  assertEquals(c.reminder.leadMinutes.toString(),field(a,"За скільки хвилин (1–525600 для нагадувань)").text.toString())
  assertEquals(c.reminder.template,field(a,"Шаблон: [ім’я], [дата], [час], [послуги]").text.toString())
 }
 private fun recreate(a:MainActivity):MainActivity {
  val monitor=instrumentation.addMonitor(MainActivity::class.java.name,null,false)
  try {
   onMain { a.recreate() }
   val next=instrumentation.waitForMonitorWithTimeout(monitor,8000) as? MainActivity ?: throw AssertionError("Activity was not recreated")
   current=next
   await("Restored client form did not load") { views(next.window.decorView).filterIsInstance<EditText>().any { it.contentDescription=="Телефон *" } }
   instrumentation.waitForIdleSync(); return next
  } finally { instrumentation.removeMonitor(monitor) }
 }
 private fun saveManual(a:MainActivity,name:String,phone:String) {
  onMain { field(a,"Ім’я *").setText(name); field(a,"Телефон *").setText(phone); button(a,"Зберегти клієнта").performClick() }
  await("Manual client save did not finish") { views(a.window.decorView).filterIsInstance<EditText>().none { it.contentDescription=="Телефон *" } }
  SQLiteRepository(a).use { repo->assertEquals(phone,repo.clients().single { it.firstName==name }.phone) }
 }
 // Catches restoring the contact action below name/phone on the create screen.
 fun testCreateContactActionDirectlyFollowsHeadingBeforeFields() {
  val a=start(); openForm(a); assertContactAtTop(a,"Новий клієнт")
 }
 // Catches fixing create while leaving the edit screen with the old field ordering.
 fun testEditContactActionDirectlyFollowsHeadingBeforeFields() {
  val a=start(); openForm(a,Client(firstName="Task 6 edit synthetic",phone="0670000601")); assertContactAtTop(a,"Клієнт")
 }
 // Catches binding before fields exist, wrong field targets, or contact import mutating other data.
 fun testSelectedContactChangesOnlyNameAndPhoneOnExistingForm() {
  val a=start(); val original=metadata(); openForm(a,original); beginSyntheticPick(a)
  onMain {
   applySelected(a," Selected synthetic "," +380 (67) 000-06-03 ")
   assertEquals(" Selected synthetic ",field(a,"Ім’я *").text.toString())
   assertEquals(" +380 (67) 000-06-03 ",field(a,"Телефон *").text.toString())
   assertMetadata(a,original)
   assertNull(privateValue(a,"contactOwner")); assertNull(privateValue(a,"pendingContact"))
  }
  SQLiteRepository(a).use { assertEquals(original,it.clients().single { c->c.id==original.id }); assertEquals(beforeClients.size+1,it.clients().size) }
 }
 // Catches cancellation clearing manual inputs or leaving the form unusable.
 fun testCancelledSelectionKeepsInputsAndManualSaveUsable() {
  val a=start(); openForm(a)
  onMain { field(a,"Ім’я *").setText("Task 6 cancel synthetic"); field(a,"Телефон *").setText("0670000604") }
  beginSyntheticPick(a)
  onMain {
   deliverResult(a,Activity.RESULT_CANCELED)
   assertEquals("Task 6 cancel synthetic",field(a,"Ім’я *").text.toString()); assertEquals("0670000604",field(a,"Телефон *").text.toString())
   assertNull(privateValue(a,"contactOwner"))
  }
  saveManual(a,"Task 6 cancel manual"," +380 (67) 000-06-04 ")
 }
 // Exercises the real async unavailable-provider read branch, without accessing any contacts authority.
 fun testUnavailableSelectedProviderKeepsInputsAndManualSaveUsable() {
  val a=start(); openForm(a)
  onMain { field(a,"Ім’я *").setText("Task 6 unavailable synthetic"); field(a,"Телефон *").setText("0670000605") }
  beginSyntheticPick(a)
  onMain { deliverResult(a,Activity.RESULT_OK,Intent().setData(Uri.parse("content://ua.zapys.task6.missing-provider/selected"))) }
  await("Unavailable provider callback did not finish") { privateValue(a,"pendingContactUri")==null }
  onMain {
   assertEquals("Task 6 unavailable synthetic",field(a,"Ім’я *").text.toString()); assertEquals("0670000605",field(a,"Телефон *").text.toString())
   assertNull(privateValue(a,"pendingContact"))
  }
  saveManual(a,"Task 6 unavailable manual","0670000605")
 }
 // Catches lost ownership, apply-before-restore and shifted hierarchy IDs, including an uncommitted color draft.
 fun testPendingSelectionSurvivesActualRecreationWithoutChangingOtherFieldsOrColorDraft() {
  var a=start(); val original=metadata(); openForm(a,original); beginSyntheticPick(a)
  var ids=emptyMap<String,Int>()
  onMain {
   ids=views(a.window.decorView).filterIsInstance<EditText>().associate { it.contentDescription.toString() to it.id }
   picker(a).performClick()
   val d=colorDialog(a)
   views(d.window!!.decorView).filterIsInstance<RadioButton>().single { it.text.toString()=="Власний колір" }.performClick()
   views(d.window!!.decorView).filterIsInstance<SeekBar>().single { it.contentDescription=="Відтінок" }.progress=240
   setPrivate(a,"contactFormReady",false)
   applySelected(a," Pending synthetic "," +380 (67) 000-06-06 ")
   assertEquals(original.firstName,field(a,"Ім’я *").text.toString())
  }
  a=recreate(a)
  onMain {
   assertEquals(" Pending synthetic ",field(a,"Ім’я *").text.toString()); assertEquals(" +380 (67) 000-06-06 ",field(a,"Телефон *").text.toString())
   assertMetadata(a,original)
   assertEquals(ids,views(a.window.decorView).filterIsInstance<EditText>().associate { it.contentDescription.toString() to it.id })
   val d=colorDialog(a); assertTrue(d.isShowing); assertTrue(d.draftState().getBoolean("custom")); assertEquals(240f,d.draftState().getFloatArray("hsv")!![0])
   views(d.window!!.decorView).filterIsInstance<TextView>().single { it.text.toString()=="Скасувати" }.performClick()
   assertEquals(original.color,picker(a).selectedColor)
   assertNull(privateValue(a,"contactOwner")); assertNull(privateValue(a,"pendingContact"))
  }
 }
 // Catches late results owned by a previous draft leaking into a new client's form.
 fun testPreviousDraftSelectionCannotUpdateNewDraft() {
  val a=start(); openForm(a); beginSyntheticPick(a)
  var oldOwner:Any?=null
  onMain { oldOwner=privateValue(a,"contactOwner"); a.finish("clients") }
  await("Client catalogue did not return") { views(a.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="+ Додати клієнта" } }
  onMain { button(a,"+ Додати клієнта").performClick() }
  await("New draft did not load") { views(a.window.decorView).filterIsInstance<EditText>().any { it.contentDescription=="Телефон *" } }
  onMain {
   assertFalse("New draft must have its own identity",oldOwner==identity(a))
   applySelected(a,"Stale synthetic","0670000607")
   assertEquals("",field(a,"Ім’я *").text.toString()); assertEquals("",field(a,"Телефон *").text.toString())
  }
 }
 // Measures the real shared form composition at narrow widths and saves synthetic rendering evidence.
 fun testContactActionRemainsReadableInLightDarkAndLargeTextForms() {
  val a=start()
  onMain {
   val originalTheme=a.palette
   val output=File(a.getExternalFilesDir(null),"task-6").apply { mkdirs() }
   try { for(dark in listOf(false,true)) for(widthDp in listOf(320,360,411)) for(scale in listOf(1f,2f)) for(existing in listOf(false,true)) {
    val config=Configuration(a.resources.configuration).apply {
     uiMode=(uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or (if(dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
     fontScale=scale
    }
    a.palette=Theme(a.createConfigurationContext(config))
    val screen=ClientsScreen.form(a,Client(firstName="Synthetic",phone="0670000608"),existing,emptyList(),emptyList())
    val width=a.palette.dp(widthDp); val height=a.palette.dp(650)
    screen.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY)); screen.layout(0,0,width,height)
    val contact=views(screen).filterIsInstance<TextView>().single { it.text.toString()=="Обрати телефон з контактів" }
    val name=views(screen).filterIsInstance<EditText>().single { it.contentDescription=="Ім’я *" }
    assertEquals(2,(contact.parent as ViewGroup).indexOfChild(contact)); assertTrue(contact.bottom<=name.top)
    assertTrue("Contact text must fit at ${widthDp}dp font $scale",contact.layout.height<=contact.height-contact.compoundPaddingTop-contact.compoundPaddingBottom)
    for(line in 0 until contact.layout.lineCount) assertEquals("Contact text must not truncate",0,contact.layout.getEllipsisCount(line))
    val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
    try {
     screen.draw(Canvas(bitmap))
     File(output,"${if(dark) "dark" else "light"}-${widthDp}dp-font$scale-${if(existing) "edit" else "create"}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
    } finally { bitmap.recycle() }
   } } finally { a.palette=originalTheme }
  }
 }
 override fun tearDown() {
  current?.let { a->
   onMain { a.finish() }
   if(snapshotTaken) SQLiteRepository(a).use { repo->repo.clients().filter { row->beforeClients.none { it.id==row.id } }.forEach { repo.deleteClient(it.id) } }
  }
  super.tearDown()
 }
}
