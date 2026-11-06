package ua.zapys.ui

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.widget.*
import ua.zapys.MainActivity
import ua.zapys.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object CalendarScreen {
 fun create(a:MainActivity,records:List<Appointment>,date:LocalDate,mode:String,change:(LocalDate,String)->Unit):LinearLayout {
  val t=a.palette
  val root=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL; setPadding(t.dp(12),0,t.dp(12),0) }
  root.addView(t.text("zapys · "+date.format(DateTimeFormatter.ofPattern("LLLL yyyy",Locale.forLanguageTag("uk"))),22f))
  fun row(items:List<Pair<String,()->Unit>>) { root.addView(LinearLayout(a).apply { items.forEach { (label,action)->addView(t.button(label,action),LinearLayout.LayoutParams(0,-2,1f)) } }) }
  row(listOf("‹ Тиждень" to { change(CalendarGeometry.navigate(date,-1),mode) },"Сьогодні" to { change(LocalDate.now(),mode) },"Тиждень ›" to { change(CalendarGeometry.navigate(date,1),mode) }))
  row(listOf((if(mode=="day") "✓ День" else "День") to { change(date,"day") },(if(mode=="week") "✓ Тиждень" else "Тиждень") to { change(date,"week") }))
  val header=LinearLayout(a)
  header.addView(TextView(a),LinearLayout.LayoutParams(t.dp(42),1))
  CalendarGeometry.weekDates(date).forEach { day-> header.addView(t.button(day.format(DateTimeFormatter.ofPattern("EE\nd",Locale.forLanguageTag("uk")))) { change(day,"day") }.apply { isSelected=day==date; if(day==date) { setBackgroundColor(t.accent); setTextColor(t.onAccent) } else if(day==LocalDate.now()) { background=android.graphics.drawable.GradientDrawable().apply { setColor(t.surface); setStroke(t.dp(2),t.accent) }; setTextColor(t.ink) }; contentDescription=day.toString()+(if(day==date) ", обрано" else "")+(if(day==LocalDate.now()) ", сьогодні" else ""); textSize=11f; setPadding(0,0,0,0) },LinearLayout.LayoutParams(0,-2,1f)) }
  root.addView(header)
  root.addView(t.text(if(mode=="day") date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Locale.forLanguageTag("uk"))) else "Тиждень · ${CalendarGeometry.weekDates(date).first()} — ${CalendarGeometry.weekDates(date).last()}",14f))
  val error=a.errorLabel(root)
  val dates=if(mode=="week") CalendarGeometry.weekDates(date) else listOf(date)
  fun persist(candidate:Appointment,isDraft:Boolean,allow:Boolean=false) {
   a.save(error,{ it.saveAppointment(candidate,allow) },failure={ failure->
    if(failure is ConflictException) AlertDialog.Builder(a).setTitle("Цей час перекривається з іншим записом")
     .setMessage(failure.appointments.joinToString("\n") { "${it.clientName.ifBlank { "Чернетка" }} · ${CalendarView.time(it.startMinute)}–${CalendarView.time(it.endMinute)}" })
     .setPositiveButton("Зберегти попри перетин") { _,_->persist(candidate,isDraft,true) }
     .setNegativeButton("Скасувати") { _,_->error.text="Зміни не збережено" }.show()
   }) { saved->
    val announcement="Збережено ${CalendarView.time(saved.startMinute)}–${CalendarView.time(saved.endMinute)}"
    error.announceForAccessibility(announcement); Toast.makeText(a,announcement,Toast.LENGTH_SHORT).show()
    if(isDraft) a.openCalendarDraft(saved) else change(date,mode)
   }
  }
  fun confirmResize(candidate:Appointment) { AlertDialog.Builder(a).setTitle("Змінити час закінчення")
   .setMessage("${CalendarView.time(candidate.startMinute)}–${CalendarView.time(candidate.endMinute)}")
   .setPositiveButton("Зберегти") { _,_->persist(candidate,false) }.setNegativeButton("Скасувати",null).show() }
  val content=LinearLayout(a).apply { orientation=LinearLayout.VERTICAL }
  val grid=CalendarView(a,t,dates,records,{ a.openAppointment(it.id) },{ day,minute->persist(CalendarGeometry.draft(day,minute),true) },::confirmResize) { candidate->error.text=candidate?.let { "Попередній перегляд: ${CalendarView.time(it.startMinute)}–${CalendarView.time(it.endMinute)}" } ?: "" }
  content.addView(grid)
  content.addView(t.text("Доступний список записів",20f))
  val visible=records.filter { it.date in dates }.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.startMinute })
  if(visible.isEmpty()) content.addView(t.text("Записів ще немає"))
  visible.forEach { value->
   val label="${value.clientName.ifBlank { "Чернетка" }} · ${value.date} · ${CalendarView.time(value.startMinute)}–${CalendarView.time(value.endMinute)} · ${value.status.title} · ${value.services.size} послуг"
   content.addView(t.button(label) { a.openAppointment(value.id) })
   value.services.forEach { content.addView(t.text(it.name,14f)) }
   content.addView(t.button("Змінити час закінчення: ${value.clientName.ifBlank { "Чернетка" }}") {
    TimePickerDialog(a,{ _,hour,minute-> confirmResize(CalendarGeometry.resize(value,(hour*60+minute).toFloat())) },value.endMinute/60,value.endMinute%60,true).show()
   })
  }
  val scroll=t.scroll(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); scroll.post { scroll.scrollTo(0,(CalendarGeometry.top(540)*a.resources.displayMetrics.density).toInt()) }
  // Native shortcut makes the agenda reachable without traversing 24 hours of canvas.
  root.addView(t.button("Список записів") { scroll.smoothScrollTo(0,grid.height) })
  root.addView(t.button("+ Новий запис") { a.openAppointment() })
  return root
 }
}
