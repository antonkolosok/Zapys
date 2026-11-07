package ua.zapys.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.app.TimePickerDialog
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import ua.zapys.MainActivity
import ua.zapys.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object CalendarScreen {
 fun create(a:MainActivity,records:List<Appointment>,date:LocalDate,mode:String,change:(LocalDate,String)->Unit):LinearLayout {
  val t=a.palette
  val controls=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
  var controlsScroll:ScrollView?=null
  var primary:Button?=null
  var headings:View?=null
  // Independently scrolling controls leave a usable timeline in small windows.
  val root=object:LinearLayout(a) {
   override fun onMeasure(w:Int,h:Int) {
    val available=(MeasureSpec.getSize(h)-paddingTop-paddingBottom).coerceAtLeast(0)
    val widthSpec=MeasureSpec.makeMeasureSpec((MeasureSpec.getSize(w)-paddingLeft-paddingRight).coerceAtLeast(0),MeasureSpec.EXACTLY)
    controls.measure(widthSpec,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
    headings?.measure(widthSpec,MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED))
    val limit=(available-(headings?.measuredHeight ?: 0)-t.dp(48)).coerceAtLeast(0)
    val preferred=maxOf((available*0.4f).toInt(),primary?.measuredHeight ?: 0)
    controlsScroll?.layoutParams?.height=minOf(controls.measuredHeight,preferred,limit)
    super.onMeasure(w,h)
   }
  }.apply { orientation=LinearLayout.VERTICAL; setPadding(t.dp(12),0,t.dp(12),0) }
  val dialogs=mutableSetOf<Dialog>()
  fun <T:Dialog> showDialog(dialog:T):T {
   dialog.setOwnerActivity(a); dialogs.add(dialog); dialog.setOnDismissListener { dialogs.remove(dialog) }; dialog.show(); return dialog
  }
  root.addOnAttachStateChangeListener(object:View.OnAttachStateChangeListener {
   override fun onViewAttachedToWindow(v:View) {}
   override fun onViewDetachedFromWindow(v:View) { dialogs.toList().forEach { it.dismiss() }; dialogs.clear() }
  })
  lateinit var scroll:ScrollView
  lateinit var grid:CalendarView
  primary=t.button("+ Новий запис") { a.openAppointment() }.also { controls.addView(it) }
  controls.addView(t.button("Список записів") { scroll.smoothScrollTo(0,grid.height) })
  controls.addView(t.text("zapys · "+date.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale.forLanguageTag("uk"))),22f))
  fun row(items:List<Pair<String,()->Unit>>) { controls.addView(LinearLayout(a).apply { items.forEach { (label,action)->addView(t.button(label,action),LinearLayout.LayoutParams(0,-2,1f)) } }) }
  row(listOf("‹ Тиждень" to { change(CalendarGeometry.navigate(date,-1),mode) },"Сьогодні" to { change(LocalDate.now(),mode) },"Тиждень ›" to { change(CalendarGeometry.navigate(date,1),mode) }))
  row(listOf((if(mode=="day") "✓ День" else "День") to { change(date,"day") },(if(mode=="week") "✓ Тиждень" else "Тиждень") to { change(date,"week") }))
  val header=CalendarDateHeader(a,t,date,mode) { change(it,"day") }
  controls.addView(header.selector)
  controls.addView(t.text(if(mode=="day") date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale.forLanguageTag("uk"))) else "Тиждень · ${CalendarGeometry.weekDates(date).first()} — ${CalendarGeometry.weekDates(date).last()}",14f))
  controlsScroll=t.scroll(controls).also { root.addView(it,LinearLayout.LayoutParams(-1,0)) }
  headings=header.gridHeadings
  headings?.let { root.addView(it) }
  val error=t.text("",14f).apply {
   setTextIsSelectable(false); accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE
   setBackgroundColor(t.surface); visibility=View.GONE
   addTextChangedListener(object:TextWatcher {
    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int) {}
    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) { visibility=if(s.isNullOrEmpty()) View.GONE else View.VISIBLE }
    override fun afterTextChanged(s:Editable?) {}
   })
  }
  val dates=if(mode=="week") CalendarGeometry.weekDates(date) else listOf(date)
  fun persist(candidate:Appointment,isDraft:Boolean,allow:Boolean=false,original:Appointment?=null) {
   a.save(error,{ if(original==null) it.saveAppointment(candidate,allow) else it.moveAppointment(original,candidate.date,candidate.startMinute,allow) },failure={ failure->
    if(failure is ConflictException) showDialog(AlertDialog.Builder(a).setTitle("Цей час перекривається з іншим записом")
     .setMessage(failure.appointments.joinToString("\n") { "${it.clientName.ifBlank { "Чернетка" }} · ${CalendarView.time(it.startMinute)}–${CalendarView.time(it.endMinute)}" })
     .setPositiveButton("Зберегти попри перетин") { _,_->persist(candidate,isDraft,true,original) }
     .setNegativeButton("Скасувати") { _,_->error.text="Зміни не збережено" }.create())
   }) { saved->
    val announcement="Збережено ${saved.date} · ${CalendarView.time(saved.startMinute)}–${CalendarView.time(saved.endMinute)}"
    error.announceForAccessibility(announcement); Toast.makeText(a,announcement,Toast.LENGTH_SHORT).show()
    if(isDraft) a.openCalendarDraft(saved) else change(date,mode)
   }
  }
  fun confirmResize(candidate:Appointment) { showDialog(AlertDialog.Builder(a).setTitle("Змінити час закінчення")
   .setMessage("${CalendarView.time(candidate.startMinute)}–${CalendarView.time(candidate.endMinute)}")
   .setPositiveButton("Зберегти") { _,_->persist(candidate,false) }.setNegativeButton("Скасувати",null).create()) }
  fun confirmMove(candidate:Appointment) {
   val original=records.firstOrNull { it.id==candidate.id } ?: return
   if(candidate==original) return
   showDialog(AlertDialog.Builder(a).setTitle("Перенести запис")
    .setMessage("${candidate.date} · ${CalendarView.time(candidate.startMinute)}–${CalendarView.time(candidate.endMinute)}")
    .setPositiveButton("Зберегти") { _,_->persist(candidate,false,original=original) }.setNegativeButton("Скасувати",null).create())
  }
  fun accessibleMove(original:Appointment) {
   val form=t.column()
   form.addView(t.text("Крок перенесення — 15 хв. Тривалість зберігається.",14f))
   val day=t.field(form,"Нова дата (РРРР-ММ-ДД)",original.date.toString())
   form.addView(t.button("Обрати дату") {
    val selected=runCatching { LocalDate.parse(day.text.toString()) }.getOrDefault(original.date)
    showDialog(DatePickerDialog(a,{ _,year,month,d->day.setText(LocalDate.of(year,month+1,d).toString()) },selected.year,selected.monthValue-1,selected.dayOfMonth))
   })
   val time=t.field(form,"Новий початок (ГГ:ХХ)",CalendarView.time(original.startMinute))
   form.addView(t.button("Обрати час") {
    val selected=runCatching { Rules.parseTime(time.text.toString()) }.getOrDefault(original.startMinute)
    showDialog(TimePickerDialog(a,{ _,hour,minute->time.setText(CalendarView.time(hour*60+minute)) },selected/60,selected%60,true))
   })
   val dialog=showDialog(AlertDialog.Builder(a).setTitle("Перенести запис").setView(t.scroll(form))
    .setPositiveButton("Далі",null).setNegativeButton("Скасувати",null).create())
   dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
    day.error=null; time.error=null
    val targetDate=try { LocalDate.parse(day.text.toString().trim()) } catch(_:java.time.DateTimeException) { day.error="Дата у форматі РРРР-ММ-ДД"; day.requestFocus(); return@setOnClickListener }
    val start=try { Rules.parseTime(time.text.toString()) } catch(e:ValidationException) { time.error=e.message; time.requestFocus(); return@setOnClickListener }
    val candidate=CalendarGeometry.move(original,targetDate,(start-original.startMinute).toFloat())
    dialog.dismiss(); confirmMove(candidate)
   }
  }
  val content=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
  grid=CalendarView(a,t,dates,records,{ a.openAppointment(it.id) },{ day,minute->persist(CalendarGeometry.draft(day,minute),true) },::confirmResize,
   previewTime={ candidate->error.text=candidate?.let { "Попередній перегляд: ${it.date} · ${CalendarView.time(it.startMinute)}–${CalendarView.time(it.endMinute)}" } ?: "" },move=::confirmMove)
  content.addView(grid)
  content.addView(t.text("Доступний список записів",20f))
  val visible=records.filter { it.date in dates }.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.startMinute })
  if(visible.isEmpty()) content.addView(t.text("Записів ще немає"))
  visible.forEach { value->
   val name=value.clientName.ifBlank { "Чернетка" }
   val label="$name · ${value.date} · ${CalendarView.time(value.startMinute)}–${CalendarView.time(value.endMinute)} · ${value.status.title} · ${value.services.size} послуг"
   content.addView(t.button(label) { a.openAppointment(value.id) })
   value.services.forEach { content.addView(t.text(it.name,14f)) }
   content.addView(t.button("Перенести запис: $name") { accessibleMove(value) })
   content.addView(t.button("Змінити час закінчення: $name") {
    showDialog(TimePickerDialog(a,{ _,hour,minute-> confirmResize(CalendarGeometry.resize(value,(hour*60+minute).toFloat())) },value.endMinute/60,value.endMinute%60,true))
   })
  }
  scroll=t.scroll(content)
  val timeline=FrameLayout(a).apply {
   addView(scroll,FrameLayout.LayoutParams(-1,-1))
   addView(error,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
  }
  root.addView(timeline,LinearLayout.LayoutParams(-1,0,1f))
  scroll.post { scroll.scrollTo(0,(CalendarGeometry.top(540)*a.resources.displayMetrics.density).toInt()) }
  return root
 }
}
