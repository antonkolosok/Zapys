package ua.zapys.ui

import android.text.InputType
import android.view.View
import ua.zapys.MainActivity
import ua.zapys.domain.*

object ServicesScreen {
 fun list(a: MainActivity, services: List<Service>, edit: (String?)->Unit): View {
  val t=a.palette; val p=t.column(); p.addView(t.text("Послуги",23f)); p.addView(t.button("+ Додати послугу") { edit(null) })
  if(services.isEmpty()) p.addView(t.text("Послуг ще немає"))
  services.forEach { s->
   val duration=s.durationMinutes?.let { "$it хв" } ?: "Тривалість не вказана"
   val price=when(s.priceMinor) { null->"Ціна не вказана"; 0L->"Безкоштовно"; else->"${Rules.formatMoney(s.priceMinor)} ₴" }
   p.addView(t.button("${s.name}\n$duration · $price") { edit(s.id) })
  }
  p.addView(t.text("Каталог не обмежений п’ятьма послугами. До одного запису можна додати до ${Limits.MAX_SERVICES} послуг.",12f)); return t.scroll(p)
 }
 fun form(a: MainActivity, original: Service, existing: Boolean): View {
  val t=a.palette; val p=a.form(if(existing) "Послуга" else "Нова послуга","services")
  val name=a.field(p,"Назва *",original.name)
  val duration=a.field(p,"Тривалість, хв (необов’язково)",original.durationMinutes?.toString() ?: "",InputType.TYPE_CLASS_NUMBER)
  val price=a.field(p,"Ціна, ₴ (порожня — невідома, 0 — безкоштовно)",Rules.formatMoney(original.priceMinor),InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
  val note=a.field(p,"Нотатка (текст, до ${Limits.MAX_NOTE} символів)",original.note,InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
  val count=t.text("${note.length()} / ${Limits.MAX_NOTE}",12f); p.addView(count); note.onChange { count.text="${note.length()} / ${Limits.MAX_NOTE}" }
  val color=t.colors(p,original.color) { a.changed() }; a.identify(p.getChildAt(p.childCount-1))
  val error=a.errorLabel(p)
  p.addView(t.button("Зберегти послугу") {
   listOf(name,duration,price,note).forEach { it.error=null }
   if(name.text.isBlank()) { name.error="Вкажіть назву"; name.requestFocus(); return@button }
   val minutes=try { FormRules.duration(duration.text.toString()) } catch(e:ValidationException) { duration.error=a.message(e); duration.requestFocus(); return@button }
   val money=try { Rules.parseMoney(price.text.toString()) } catch(e:ValidationException) { price.error=a.message(e); price.requestFocus(); return@button }
   if(note.length()>Limits.MAX_NOTE) { note.error="Максимум ${Limits.MAX_NOTE} символів"; note.requestFocus(); return@button }
   val service=original.copy(name=name.text.toString().trim(),durationMinutes=minutes,priceMinor=money,note=note.text.toString(),color=color())
   a.save(error,{ it.saveService(service) }) { a.finish("services") }
  })
  if(existing) p.addView(t.button("Видалити послугу") { a.confirm("Видалити ${original.name}?","Послуга зникне з каталогу. Її дані в історії записів залишаться.") { a.save(error,{ it.deleteService(original.id) }) { a.finish("services") } } })
  return t.scroll(p)
 }
}

