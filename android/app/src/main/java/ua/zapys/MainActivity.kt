package ua.zapys

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.SparseArray
import android.os.Parcelable
import android.view.View
import android.widget.*
import ua.zapys.data.SQLiteRepository
import ua.zapys.domain.*
import ua.zapys.ui.*
import ua.zapys.device.*
import android.content.Intent
import java.util.concurrent.Executors

/** All repository reads/writes are serialized on io; callbacks belong to the active screen. */
class MainActivity : Activity() {
 lateinit var palette: Theme
 private lateinit var repository: SQLiteRepository
 private val io = Executors.newSingleThreadExecutor()
 private val main = Handler(Looper.getMainLooper())
 private lateinit var body: FrameLayout
 private var generation=0
 private var route="calendar"
 private var editId: String?=null
 private var draftId: String?=null
 private var dirty=false
 private var busy=false
 private var resumed=false
 private var externalEpoch=0
 override fun onPause() { resumed=false; externalEpoch++; super.onPause() }
 fun <T> loadForExternal(action:(Repository)->T,success:(T)->Unit) {
  val epoch=++externalEpoch
  val token=generation
  io.execute { val result=runCatching { action(repository) }; main.post {
   if(!isDestroyed && token==generation && DeviceRules.canLaunch(resumed,epoch,externalEpoch)) result.fold(success) { error(it) }
  } }
 }
 var saveAppointmentDraft: (() -> Unit)?=null
 private var leave: (() -> Unit)?=null
 private var pendingHierarchy: SparseArray<Parcelable>?=null
 private var restoredDirty=false
 private var fieldId=100
 private var appointmentDraft: AppointmentDraft?=null
 private var overrides=Bundle(); var inlineClientName=""; var inlineClientPhone=""; var inlineClientId=newId()
 private var selectedDate=java.time.LocalDate.now().toString()
 private var calendarMode="day"
 private var contactOwner: String?=null
 private var pendingContact: Pair<String,String>?=null
 private var pendingContactUri: String?=null
 private var contactFields: Pair<EditText,EditText>?=null
 private var contactFormReady=false
 private fun formIdentity()="$route:${editId ?: draftId}"
 fun bindContactFields(name:EditText,phone:EditText) { contactFields=name to phone }
 fun pickContact() {
  contactOwner=formIdentity()
  try { startActivityForResult(ContactPicker.intent(),ContactPicker.REQUEST) }
  catch(_: Exception) { contactOwner=null; Toast.makeText(this,"Контакти недоступні. Введіть ім’я та телефон вручну.",Toast.LENGTH_LONG).show() }
 }
 private fun applyContact() {
  if(!contactFormReady || contactOwner!=formIdentity()) return
  val value=pendingContact ?: return; val fields=contactFields ?: return
  fields.first.setText(value.first); fields.second.setText(value.second); changed(); pendingContact=null; contactOwner=null
 }
 @Deprecated("Platform result") override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {
  super.onActivityResult(requestCode,resultCode,data)
  if(requestCode!=ContactPicker.REQUEST) return
  val owner=contactOwner
  if(resultCode!=RESULT_OK || data?.data==null) { contactOwner=null; return }
  pendingContactUri=data.data!!.toString()
  readContact(owner)
 }
 private fun readContact(owner:String?) {
  val uri=android.net.Uri.parse(pendingContactUri ?: return)
  io.execute { val result=runCatching { ContactPicker.read(applicationContext,uri) }; main.post {
   if(!isDestroyed && owner==contactOwner) {
    pendingContactUri=null; pendingContact=result.getOrNull(); applyContact()
    if(result.isFailure || pendingContact==null && contactOwner!=null) Toast.makeText(this,"Не вдалося прочитати контакт. Введіть дані вручну.",Toast.LENGTH_LONG).show()
   }
  } }
 }
 override fun onResume() {
  super.onResume()
  resumed=true
  if(::repository.isInitialized) {
   val token=generation
   io.execute { runCatching { ReminderScheduler(applicationContext).reconcile(repository) }; main.post { if(!isDestroyed && token==generation && route=="reminders" && !busy) render() } }
  }
 }
 fun openReminders() { navigate("reminders") }
 fun requestNotifications() {
  if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),411)
  else try { startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,packageName)) } catch(_: Exception) { Toast.makeText(this,"Налаштування недоступні",Toast.LENGTH_SHORT).show() }
 }
 override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray) {
  super.onRequestPermissionsResult(requestCode,permissions,grantResults)
  io.execute { runCatching { ReminderScheduler(applicationContext).reconcile(repository) } }
 }
 fun overrideValue(id:String,s:ServiceSnapshot,index:Int,key:String,initial:String):String = overrides.getString("$index:$key") ?: initial
 fun setOverride(index:Int,key:String,value:String) { overrides.putString("$index:$key",value) }
 fun removeOverrides(index:Int) { val next=Bundle(); overrides.keySet().forEach { key-> val n=key.substringBefore(':').toInt(); if(n!=index) next.putString("${if(n>index)n-1 else n}:${key.substringAfter(':')}",overrides.getString(key)) }; overrides=next }
 fun applyOverrides(d:AppointmentDraft) { val values=d.services.mapIndexed { i,s->s.copy(durationMinutes=FormRules.duration(overrides.getString("$i:duration") ?: s.durationMinutes?.toString() ?: ""),priceMinor=Rules.parseMoney(overrides.getString("$i:price") ?: Rules.formatMoney(s.priceMinor))) }; d.services.clear(); d.services.addAll(values) }
 fun openAppointment(id:String?=null) { navigate("appointmentForm",id) }
 fun openCalendarDraft(saved:Appointment) { dirty=false; route="appointmentForm"; editId=saved.id; draftId=saved.id; appointmentDraft=AppointmentDraft.from(saved); overrides=Bundle(); pendingHierarchy=null; render() }
 fun repeatAppointment(value:Appointment) { appointmentDraft=AppointmentDraft.repeat(value); overrides=Bundle(); dirty=false; route="appointmentForm"; editId=null; draftId=appointmentDraft!!.id; pendingHierarchy=null; render() }
 override fun onCreate(state: Bundle?) {
  super.onCreate(state)
  palette=Theme(this); repository=SQLiteRepository(applicationContext)
  route=state?.getString("route") ?: "calendar"; editId=state?.getString("editId"); draftId=state?.getString("draftId")
  if(state==null && intent.getBooleanExtra("openReminders",false)) route="reminders"
  contactOwner=state?.getString("contactOwner")
  pendingContactUri=state?.getString("contactUri")
  if(state?.containsKey("contactName")==true) pendingContact=state.getString("contactName","") to state.getString("contactPhone","")
  pendingHierarchy=state?.getSparseParcelableArray("form"); restoredDirty=state?.getBoolean("dirty") ?: false; dirty=restoredDirty
  inlineClientName=state?.getString("inlineName") ?: ""; inlineClientPhone=state?.getString("inlinePhone") ?: ""; inlineClientId=state?.getString("inlineId") ?: newId()
  appointmentDraft=state?.getBundle("appointmentDraft")?.let { AppointmentEditor.unpack(it) }; overrides=state?.getBundle("overrides") ?: Bundle()
  selectedDate=state?.getString("selectedDate") ?: selectedDate; calendarMode=state?.getString("calendarMode") ?: calendarMode
  render()
  if(pendingContactUri!=null) readContact(contactOwner)
 }
 override fun onSaveInstanceState(out: Bundle) {
  super.onSaveInstanceState(out); out.putString("inlineName",inlineClientName); out.putString("inlinePhone",inlineClientPhone); out.putString("inlineId",inlineClientId); appointmentDraft?.let { out.putBundle("appointmentDraft",AppointmentEditor.pack(it)) }; out.putBundle("overrides",overrides); out.putString("selectedDate",selectedDate); out.putString("calendarMode",calendarMode)
  out.putString("route",route); out.putString("editId",editId); out.putString("draftId",draftId); out.putBoolean("dirty",dirty)
  out.putString("contactOwner",contactOwner); pendingContact?.let { out.putString("contactName",it.first); out.putString("contactPhone",it.second) }
  out.putString("contactUri",pendingContactUri)
  val hierarchy=pendingHierarchy ?: SparseArray<Parcelable>().also { body.saveHierarchyState(it) }; out.putSparseParcelableArray("form",hierarchy)
 }
 override fun onDestroy() { super.onDestroy(); generation++; io.execute { repository.close() }; io.shutdown() }
 fun changed() { dirty=true }
 fun identify(view: View) { view.id=fieldId++ }
 fun completeForm() {
  pendingHierarchy?.let { body.restoreHierarchyState(it); pendingHierarchy=null; dirty=restoredDirty }
  contactFormReady=true; applyContact()
 }
 fun exitForm(destination: String) { navigate(destination) }
 private fun navigate(next: String, id: String?=null) {
  if (busy) return
  val action = { appointmentDraft=null; overrides=Bundle(); inlineClientName=""; inlineClientPhone=""; inlineClientId=newId(); dirty=false; route=next; editId=id; draftId=if(next.endsWith("Form")) id ?: newId() else null; pendingHierarchy=null; render() }
  if(dirty && route=="appointmentForm") AlertDialog.Builder(this).setTitle("Зберегти зміни?").setMessage("Можна зберегти чернетку, відкинути зміни або продовжити редагування.").setPositiveButton("Зберегти чернетку") { _,_->saveAppointmentDraft?.invoke() }.setNegativeButton("Відкинути") { _,_->action() }.setNeutralButton("Продовжити",null).show()
  else if (dirty) AlertDialog.Builder(this).setTitle("Зберегти зміни?").setMessage("Зміни ще не збережено. Продовжіть редагування та натисніть «Зберегти», або відкиньте їх.")
   .setPositiveButton("Відкинути") { _,_-> action() }.setNegativeButton("Продовжити редагування",null).show()
  else action()
 }
 @Deprecated("Platform back callback") override fun onBackPressed() { if(busy) return; if(leave != null) leave?.invoke() else if(route!="calendar") navigate("calendar") else super.onBackPressed() }
 fun confirm(title: String, message: String, action: () -> Unit) { AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Підтвердити") { _,_->action() }.setNegativeButton("Скасувати",null).show() }
 fun <T> load(action: (Repository)->T, success: (T)->Unit) {
  val token=generation
  io.execute { val result=runCatching { action(repository) }; main.post { if(!isDestroyed && token==generation) result.fold(success) { error(it) } } }
 }
 fun <T> save(errorView: TextView, action: (Repository)->T, failure: ((Throwable)->Unit)?=null, success: (T)->Unit) {
  if(busy) return
  busy=true; enableInputs(body,false); errorView.text="Збереження…"
  val token=generation
  io.execute { val result=runCatching { action(repository) }; if(result.isSuccess) runCatching { ReminderScheduler(applicationContext).reconcile(repository) }; main.post {
   if(!isDestroyed && token==generation) { busy=false; enableInputs(body,true); result.fold({ dirty=false; success(it) }, { errorView.text=message(it); errorView.requestFocus(); errorView.announceForAccessibility(errorView.text); failure?.invoke(it) }) }
  } }
 }
 private fun enableInputs(view: View, enabled: Boolean) {
  view.isEnabled=enabled; if(view is android.view.ViewGroup) for(i in 0 until view.childCount) enableInputs(view.getChildAt(i),enabled)
 }
 fun message(t: Throwable): String = when(t) {
  is ValidationException -> t.message ?: "Перевірте введені дані"
  is android.database.sqlite.SQLiteConstraintException -> "Неможливо видалити клієнта, що має записи. Дані збережено."
  else -> "Не вдалося зберегти або прочитати дані. Спробуйте ще раз."
 }
 private fun error(t: Throwable) { val p=palette.column(); p.addView(palette.text(message(t))); p.addView(palette.button("Спробувати ще") { render() }); body.removeAllViews(); body.addView(palette.scroll(p)) }
 fun show(view: View) { body.removeAllViews(); body.addView(view) }
 fun form(title: String, destination: String): LinearLayout {
  leave={ navigate(destination) }
  return palette.column().apply { addView(palette.button("‹ Назад") { navigate(destination) }); addView(palette.text(title,23f)) }
 }
 fun field(p: LinearLayout,label: String,value: String="",type: Int=android.text.InputType.TYPE_CLASS_TEXT): EditText = palette.field(p,label,value,type).apply { identify(this); onChange { changed() } }
 fun errorLabel(p: LinearLayout) = palette.text("").apply { accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE; isFocusable=true; p.addView(this) }
 fun finish(destination: String) { appointmentDraft=null; overrides=Bundle(); inlineClientName=""; inlineClientPhone=""; inlineClientId=newId(); dirty=false; route=destination; editId=null; draftId=null; render() }
 private fun render() {
  generation++; fieldId=100; leave=null; saveAppointmentDraft=null
  contactFields=null; contactFormReady=false
  val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(palette.background) }
  body=FrameLayout(this); root.addView(body,LinearLayout.LayoutParams(-1,0,1f))
  val nav=BottomNavigation.create(this,palette,route) { navigate(it) }
  root.addView(nav)
  root.setOnApplyWindowInsetsListener { view, insets -> view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom); insets }
  setContentView(root); root.requestApplyInsets()
  show(palette.text("Завантаження…"))
  when(route) {
   "reminders" -> load({ r-> ReminderScheduler(applicationContext).reconcile(r); r.reminders() }) { show(RemindersScreen.create(this,it)) }
   "appointmentForm" -> load({ r->Triple(r.clients(),r.services(),r.appointments()) }) { (clients,services,appointments)->
    val d=appointmentDraft ?: appointments.firstOrNull { it.id==editId }?.let { AppointmentDraft.from(it) } ?: AppointmentDraft.fresh(draftId ?: newId(),java.time.LocalDate.parse(selectedDate))
    appointmentDraft=d; show(AppointmentEditor.form(this,d,editId!=null,clients,services)); pendingHierarchy=null; dirty=restoredDirty || dirty
   }
   "clients" -> load({ it.clients() }) { show(ClientsScreen.list(this,it) { id->navigate("clientForm",id) }) }
   "clientForm" -> load({ r-> Pair(r.clients(),r.appointments()) }) { (clients, appointments)->
    val client=clients.firstOrNull { it.id==editId } ?: Client(id=draftId ?: newId(), firstName="",phone="")
    show(ClientsScreen.form(this,client,editId!=null,clients,appointments)); completeForm()
   }
   "services" -> load({ it.services() }) { show(ServicesScreen.list(this,it) { id->navigate("serviceForm",id) }) }
   "serviceForm" -> load({ it.services() }) { services-> val service=services.firstOrNull { it.id==editId } ?: Service(id=draftId ?: newId(),name=""); show(ServicesScreen.form(this,service,editId!=null)); completeForm() }
   "settings" -> load({ it.profile() }) { show(SettingsScreen.form(this,it)); completeForm() }
   else -> load({ it.appointments() }) { records-> show(CalendarScreen.create(this,records,java.time.LocalDate.parse(selectedDate),calendarMode) { date,mode-> selectedDate=date.toString(); calendarMode=mode; render() }) }
  }
 }
}





