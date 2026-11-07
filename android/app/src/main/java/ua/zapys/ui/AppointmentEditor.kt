package ua.zapys.ui
import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.app.AlertDialog
import android.view.View
import android.widget.*
import ua.zapys.MainActivity
import ua.zapys.domain.*
import java.time.LocalDate

object AppointmentEditor {
 fun pack(d: AppointmentDraft): Bundle = Bundle().apply {
  putString("id",d.id); putString("date",d.date); putString("start",d.start); putString("end",d.end); putString("client",d.clientId); putString("note",d.note); putString("status",d.status.name); putBoolean("paid",d.paid); putString("received",d.received); putBoolean("manual",d.manualEnd)
  putParcelableArrayList("services",ArrayList(d.services.map { s-> Bundle().apply { putString("id",s.serviceId); putString("name",s.name); s.durationMinutes?.let { putInt("duration",it) }; s.priceMinor?.let { putLong("price",it) }; putString("note",s.note); s.color?.let { putInt("color",it) } } }))
 }
 fun unpack(b: Bundle): AppointmentDraft = AppointmentDraft(b.getString("id")!!,b.getString("date") ?: "",b.getString("start") ?: "",b.getString("end") ?: "",b.getString("client"),
  (b.getParcelableArrayList<Bundle>("services") ?: arrayListOf()).map { s-> ServiceSnapshot(s.getString("id")!!,s.getString("name")!!,if(s.containsKey("duration")) s.getInt("duration") else null,if(s.containsKey("price")) s.getLong("price") else null,s.getString("note") ?: "",if(s.containsKey("color")) s.getInt("color") else null) }.toMutableList(), b.getString("note") ?: "",AppointmentStatus.valueOf(b.getString("status") ?: "DRAFT"),b.getBoolean("paid"),b.getString("received") ?: "",b.getBoolean("manual"))
 fun form(a: MainActivity,d: AppointmentDraft,existing: Boolean,clients: List<Client>,catalogue: List<Service>): View {
  val session=AppointmentSaveSession(d,clients,a.inlineClientId,a.inlineClientName,a.inlineClientPhone); val availableClients=session.clients; val serviceFields=mutableListOf<Pair<EditText,EditText>>()
  val t=a.palette; val p=a.form(if(existing) "Запис" else "Новий запис","calendar")
  fun raw(label:String,value:String,set:(String)->Unit): EditText = a.field(p,label,value).apply { onChange { set(text.toString()) } }
  val date=raw("Дата * (РРРР-ММ-ДД)",d.date) { d.date=it }
  p.addView(t.button("Обрати дату") { val day=runCatching { LocalDate.parse(d.date) }.getOrDefault(LocalDate.now()); DatePickerDialog(a,{ _,y,m,v->date.setText(LocalDate.of(y,m+1,v).toString()) },day.year,day.monthValue-1,day.dayOfMonth).show() })
  val start=raw("Початок * (ГГ:ХХ)",d.start) { d.start=it }
  val end=raw("Закінчення * (ГГ:ХХ)",d.end) { d.end=it; d.manualEnd=true }
  fun timePicker(label:String,field:EditText) { p.addView(t.button(label) { val minute=runCatching { Rules.parseTime(field.text.toString()) }.getOrDefault(540); TimePickerDialog(a,{ _,h,m->field.setText(Rules.time(h*60+m)) },minute/60,minute%60,true).show() }) }
  timePicker("Обрати початок",start); timePicker("Обрати закінчення",end)
  fun showSuggestion() { d.suggest(); if(end.text.toString()!=d.end) { val manual=d.manualEnd; end.setText(d.end); d.manualEnd=manual } }
  start.onChange { showSuggestion() }
  p.addView(t.button("За тривалістю послуг") { d.manualEnd=false; showSuggestion(); a.changed() })
  p.addView(t.text("Клієнт * (для чернетки необов’язково)"))
  val client=Spinner(a); client.adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,listOf("Без клієнта")+availableClients.map { "${it.displayName} · ${it.phone}" }); client.setSelection(availableClients.indexOfFirst { it.id==d.clientId }+1); p.addView(client)
  client.onItemSelectedListener=object:AdapterView.OnItemSelectedListener { override fun onNothingSelected(p:AdapterView<*>?) {} ; override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long) { val next=availableClients.getOrNull(position-1)?.id; if(next!=d.clientId) { d.clientId=next; a.changed() } } }
    val inlineName=raw("Новий клієнт: ім’я (необов’язково)",a.inlineClientName) { a.inlineClientName=it; session.inlineName=it }
  val inlinePhone=raw("Новий клієнт: телефон",a.inlineClientPhone) { a.inlineClientPhone=it; session.inlinePhone=it }.apply { inputType=android.text.InputType.TYPE_CLASS_PHONE }
  val rows=t.column(); p.addView(rows); val error=a.errorLabel(p)
  val totalLabel=t.text(""); p.addView(totalLabel)
  fun updateTotal() { val total=d.services.fold(java.math.BigInteger.ZERO) { sum,s->sum.add(java.math.BigInteger.valueOf(s.priceMinor ?: 0)) }; totalLabel.text="Сума: ${java.math.BigDecimal(total,2).toPlainString()} грн"+if(d.services.any { it.priceMinor==null }) " + невідома ціна" else "" }
  val add=t.button("Додати послугу") {}; p.addView(add)
  fun refresh() {
   rows.removeAllViews(); serviceFields.clear(); rows.addView(t.text("Послуги ${d.services.size} / 5",20f)); add.isEnabled=d.services.size<Limits.MAX_SERVICES
   if(d.services.size==5) rows.addView(t.text("У записі вже 5 послуг"))
   d.services.toList().forEachIndexed { index,s->
    rows.addView(t.text("${index+1}. ${s.name}",20f))
    val duration=t.field(rows,"Тривалість цього візиту, хв (порожньо — невідомо)",s.durationMinutes?.toString() ?: "",android.text.InputType.TYPE_CLASS_NUMBER)
    val price=t.field(rows,"Ціна цього візиту, грн (0 — безкоштовно)",Rules.formatMoney(s.priceMinor),android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL)
    serviceFields.add(duration to price)
    // Raw override strings live separately so invalid values survive rotation and failed saves.
    duration.setText(a.overrideValue(d.id,s,index,"duration",duration.text.toString())); price.setText(a.overrideValue(d.id,s,index,"price",price.text.toString()))
    duration.onChange { a.setOverride(index,"duration",duration.text.toString()); a.changed(); try { a.applyOverrides(d); showSuggestion(); updateTotal() } catch(_:ValidationException) {} }; price.onChange { a.setOverride(index,"price",price.text.toString()); a.changed(); try { a.applyOverrides(d); updateTotal() } catch(_:ValidationException) {} }
    if(s.note.isNotEmpty()) rows.addView(t.text(s.note))
    rows.addView(t.button("Прибрати ${s.name}") { d.services.removeAt(index); a.removeOverrides(index); a.changed(); refresh(); showSuggestion() })
   }
   updateTotal()
  }
  add.setOnClickListener { if(catalogue.isEmpty()) { error.text="Спершу додайте послугу в каталозі" } else AlertDialog.Builder(a).setTitle("Обрати послугу").setItems(catalogue.map { it.name }.toTypedArray()) { _,i-> if(d.services.size<5) { d.add(catalogue[i].snapshot()); a.changed(); refresh(); showSuggestion() } }.show() }; refresh()
  val note=raw("Нотатка (до 2000 символів)",d.note) { d.note=it }
  val noteCount=t.text("${d.note.length} / 2000",12f); p.addView(noteCount); note.onChange { noteCount.text="${note.length()} / 2000" }
  p.addView(t.text("Статус")); val statuses=AppointmentStatus.values(); val status=Spinner(a); status.adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,listOf("Чернетка","Заплановано","Завершено","Скасовано","Не з’явився / не з’явилась")); status.setSelection(d.status.ordinal); p.addView(status)
  status.onItemSelectedListener=object:AdapterView.OnItemSelectedListener { override fun onNothingSelected(p:AdapterView<*>?) {} ; override fun onItemSelected(p:AdapterView<*>?,v:View?,position:Int,id:Long) { if(d.status!=statuses[position]) { d.status=statuses[position]; a.changed() } } }
  p.addView(CheckBox(a).apply { text="Оплачено"; isChecked=d.paid; setOnCheckedChangeListener { _,yes->d.paid=yes; a.changed() } })
  val received=raw("Фактично отримано, грн (необов’язково)",d.received) { d.received=it }
  received.inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
  fun candidate(): Appointment { a.applyOverrides(d); return d.build(availableClients) }
  fun invalid(e:ValidationException) {
   error.text=e.message
   val field=when { runCatching { LocalDate.parse(d.date) }.isFailure->date; runCatching { Rules.parseTime(d.start) }.isFailure->start; runCatching { Rules.parseTime(d.end) }.isFailure->end; runCatching { Rules.parseTime(d.end)>Rules.parseTime(d.start) }.getOrDefault(false).not()->end; d.note.length>Limits.MAX_NOTE->note; runCatching { Rules.parseMoney(d.received) }.isFailure->received; else->serviceFields.firstNotNullOfOrNull { (duration,price)->when { runCatching { FormRules.duration(duration.text.toString()) }.isFailure->duration; runCatching { Rules.parseMoney(price.text.toString()) }.isFailure->price; else->null } } }
   field?.let { it.error=e.message; it.requestFocus() } ?: run { if(d.status!=AppointmentStatus.DRAFT && d.clientId==null) { client.requestFocus(); client.announceForAccessibility("Оберіть клієнта") } else error.requestFocus() }
  }
  fun commit(value:Appointment,allow:Boolean=false) { a.save(error,{ it.saveAppointment(value,allow) },{ failure-> if(failure is ConflictException) a.confirm("Час перетинається з іншим записом",failure.appointments.joinToString("\n") { "${it.clientName} ${Rules.time(it.startMinute)}–${Rules.time(it.endMinute)}" }) { commit(value,true) } }) { a.finish("calendar") } }
  fun submit(requestedStatus:AppointmentStatus) {
   val request=try { a.applyOverrides(d); session.prepare(requestedStatus) } catch(e:ValidationException) {
    if(session.inlineName.isNotBlank() || session.inlinePhone.isNotBlank()) {
     val c=Client(id=session.inlineId,firstName=session.inlineName.trim(),phone=session.inlinePhone)
     if(c.firstName.isBlank()) { inlineName.error=e.message; inlineName.requestFocus(); return }
     if(PhoneRules.normalize(c.phone)==null) { inlinePhone.error=PhoneRules.INVALID_MESSAGE; inlinePhone.requestFocus(); return }
    }
    invalid(e); return
   }
   val created=request.newClient
   if(created==null) commit(request.appointment)
   else a.save(error,{ it.saveClient(created) }) {
    session.clientPersisted(created); a.inlineClientId=session.inlineId; a.changed()
    inlineName.setText(""); inlinePhone.setText("")
    client.adapter=ArrayAdapter(a,android.R.layout.simple_spinner_dropdown_item,listOf("Без клієнта")+availableClients.map { "${it.displayName} · ${it.phone}" })
    client.setSelection(availableClients.indexOfFirst { it.id==created.id }+1)
    commit(request.appointment)
   }
  }
  p.addView(t.button("Зберегти запис") { submit(if(d.status==AppointmentStatus.DRAFT) AppointmentStatus.PLANNED else d.status) })
  a.saveAppointmentDraft={ submit(AppointmentStatus.DRAFT) }
  p.addView(t.button("Зберегти чернетку") { submit(AppointmentStatus.DRAFT) })
  if(existing) {
   p.addView(t.button("Повторити запис") { try { a.repeatAppointment(candidate()) } catch(e:ValidationException) { error.text=e.message } })
   p.addView(t.button("Скасувати запис") { a.confirm("Скасувати запис?","Нагадування буде скасовано.") { try { commit(candidate().copy(status=AppointmentStatus.CANCELLED)) } catch(e:ValidationException) { error.text=e.message } } })
   p.addView(t.button("Видалити запис") { a.confirm("Видалити запис?","Цю дію неможливо скасувати.") { a.save(error,{ it.deleteAppointment(d.id) }) { a.finish("calendar") } } })
  }
  p.addView(t.text("Автоматичне надсилання не підключено. Налаштування нагадувань — у клієнта."))
  p.addView(t.button("Черга нагадувань · надсилання вручну") { a.openReminders() })
  return t.scroll(p)
 }
}
