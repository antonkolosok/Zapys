package ua.zapys.ui

import android.text.InputType
import android.view.View
import android.widget.*
import ua.zapys.MainActivity
import ua.zapys.domain.*
import java.time.LocalDateTime

object ClientsScreen {
 fun list(a: MainActivity, clients: List<Client>, edit: (String?)->Unit): View {
  val t=a.palette; val p=t.column(); p.addView(t.text("Клієнти",23f)); p.addView(t.button("+ Додати клієнта") { edit(null) })
  val search=t.field(p,"Пошук за ім’ям або телефоном"); val rows=t.column(); p.addView(rows)
  fun refresh() { rows.removeAllViews(); val found=clients.filter { FormRules.matches(it,search.text.toString()) }; if(found.isEmpty()) rows.addView(t.text("Клієнтів не знайдено")); found.forEach { c->rows.addView(t.button("${c.displayName}\n${c.phone}") { edit(c.id) }) } }
  search.onChange { refresh() }; refresh(); return t.scroll(p)
 }
 fun form(a: MainActivity, original: Client, existing: Boolean, all: List<Client>, appointments: List<Appointment>): View {
  val t=a.palette; val p=a.form(if(existing) "Клієнт" else "Новий клієнт","clients")
  p.addView(t.button("Обрати телефон з контактів") { a.pickContact() })
  val name=a.field(p,"Ім’я *",original.firstName)
  p.addView(t.text("Ім’я: до ${Limits.MAX_PERSON_NAME} символів без пробілів по краях",12f))
  val phone=a.field(p,"Телефон *",original.phone,InputType.TYPE_CLASS_PHONE)
  val surname=a.field(p,"Прізвище (необов’язково)",original.surname)
  p.addView(t.text("Прізвище: до ${Limits.MAX_PERSON_NAME} символів без пробілів по краях",12f))
  val patronymic=a.field(p,"По батькові (необов’язково)",original.patronymic)
  p.addView(t.text("По батькові: до ${Limits.MAX_PERSON_NAME} символів без пробілів по краях",12f))
  val note=a.field(p,"Нотатка (текст, до ${Limits.MAX_NOTE} символів)",original.note,InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
  val count=t.text("${note.length()} / ${Limits.MAX_NOTE}",12f); p.addView(count); note.onChange { count.text="${note.length()} / ${Limits.MAX_NOTE}" }
  val color=t.colors(p,original.color) { a.changed() }; a.identify(p.getChildAt(p.childCount-1))
  val error=a.errorLabel(p)
  a.bindContactFields(name,phone)
  p.addView(t.text("Нагадування: до 1 на запис. Надсилання вручну.",20f))
  val enabled=Switch(a).apply { text="Готувати нагадування"; isChecked=original.reminder.enabled; a.identify(this); setOnCheckedChangeListener { _,_->a.changed() } }; p.addView(enabled)
  val channel=Spinner(a).apply { adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,Channel.values().map { it.title }); setSelection(original.reminder.channel.ordinal); a.identify(this) }; p.addView(channel)
  channel.onItemSelectedListener=object:AdapterView.OnItemSelectedListener { override fun onNothingSelected(parent:AdapterView<*>?) {} ; override fun onItemSelected(parent:AdapterView<*>?,view:View?,position:Int,id:Long) { if(position!=original.reminder.channel.ordinal) a.changed() } }
  p.addView(t.text("Одержувача ви обираєте в месенджері під час ручного надсилання."))
  val lead=a.field(p,"За скільки хвилин (1–525600 для нагадувань)",original.reminder.leadMinutes.toString(),InputType.TYPE_CLASS_NUMBER)
  val template=a.field(p,"Шаблон: [ім’я], [дата], [час], [послуги]",original.reminder.template,InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
  p.addView(t.button("Зберегти клієнта") {
   listOf(name,phone,surname,patronymic,note).forEach { it.error=null }
   val invalid = PersonNameRules.firstNameError(name.text.toString())?.let { name to it }
    ?: if(PhoneRules.normalize(phone.text.toString())==null) phone to PhoneRules.INVALID_MESSAGE
    else PersonNameRules.surnameError(surname.text.toString())?.let { surname to it }
     ?: PersonNameRules.patronymicError(patronymic.text.toString())?.let { patronymic to it }
     ?: if(note.length()>Limits.MAX_NOTE) note to "Максимум ${Limits.MAX_NOTE} символів" else null
   if(invalid!=null) { invalid.first.error=invalid.second; invalid.first.requestFocus(); return@button }
   val minutes=lead.text.toString().toIntOrNull()
   if(minutes==null || minutes !in 0..525600 || enabled.isChecked && minutes==0) { lead.error="Для увімкненого нагадування вкажіть 1–525600 хвилин"; lead.requestFocus(); return@button }
   val client=original.copy(firstName=name.text.toString().trim(),phone=phone.text.toString(),surname=surname.text.toString().trim(),patronymic=patronymic.text.toString().trim(),note=note.text.toString(),color=color(),reminder=original.reminder.copy(enabled=enabled.isChecked,channel=Channel.values()[channel.selectedItemPosition],leadMinutes=minutes,template=template.text.toString()))
   try { Rules.validateClient(client) } catch(e: ValidationException) { error.text=a.message(e); return@button }
   val commit = { a.save(error,{ r->r.saveClient(client) }) { a.finish("clients") } }
   // Duplicate phone never acts as an upsert key: this form retains its own UUID.
   if(FormRules.duplicates(client,all).isNotEmpty()) a.confirm("Цей телефон уже є", "Зберегти окремого клієнта з цим номером? Інших клієнтів не буде змінено.",commit) else commit()
  })
  if(existing) {
   p.addView(t.button("Видалити клієнта") { a.confirm("Видалити ${original.displayName}?","Дію не можна скасувати. Клієнта з записами видалити неможливо.") { a.save(error,{ it.deleteClient(original.id) }) { a.finish("clients") } } })
   val history=appointments.filter { it.clientId==original.id }
   fun section(title: String, records: List<Appointment>) {
    p.addView(t.text(title,20f)); if(records.isEmpty()) p.addView(t.text("Записів ще немає"))
    records.forEach { record ->
     p.addView(t.button("${record.date} · ${Rules.time(record.startMinute)}–${Rules.time(record.endMinute)} · ${record.status.title}") { a.openAppointment(record.id) })
     record.services.forEach { p.addView(t.text(it.name)) }
     if(record.note.isNotEmpty()) p.addView(t.text(record.note))
    }
   }
   val now=LocalDateTime.now(); section("Майбутні записи",history.filter { !FormRules.isHistory(it,now) }); section("Історія записів",history.filter { FormRules.isHistory(it,now) })
  }
  return t.scroll(p)
 }
}


