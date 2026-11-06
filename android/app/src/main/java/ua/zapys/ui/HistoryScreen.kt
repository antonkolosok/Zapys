package ua.zapys.ui
import android.view.View
import android.widget.LinearLayout
import ua.zapys.MainActivity
import ua.zapys.domain.*
import java.time.LocalDateTime
object HistoryScreen {
 fun section(a:MainActivity,p:LinearLayout,title:String,records:List<Appointment>) {
  val t=a.palette; p.addView(t.text(title,20f)); if(records.isEmpty()) p.addView(t.text("Записів ще немає"))
  records.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.startMinute }).forEach { record->
   p.addView(t.button("${record.clientName.ifBlank { "Чернетка" }} · ${record.date}\n${Rules.time(record.startMinute)}–${Rules.time(record.endMinute)} · ${record.status.title}") { a.openAppointment(record.id) })
   record.services.forEach { s->p.addView(t.text(s.name+" · "+(s.durationMinutes?.let { "$it хв" } ?: "тривалість невідома")+" · "+(s.priceMinor?.let { if(it==0L) "безкоштовно" else "${Rules.formatMoney(it)} грн" } ?: "ціна невідома"))); if(s.note.isNotBlank()) p.addView(t.text(s.note)) }
   if(record.note.isNotBlank()) p.addView(t.text(record.note))
  }
 }
 fun agenda(a:MainActivity,records:List<Appointment>,open:(String?)->Unit):View {
  val p=a.palette.column(); p.addView(a.palette.text("zapys · Записи",23f)); p.addView(a.palette.button("+ Новий запис") { open(null) })
  val now=LocalDateTime.now(); section(a,p,"Майбутні та поточні",records.filter { !FormRules.isHistory(it,now) }); section(a,p,"Історія",records.filter { FormRules.isHistory(it,now) }); return a.palette.scroll(p)
 }
}
