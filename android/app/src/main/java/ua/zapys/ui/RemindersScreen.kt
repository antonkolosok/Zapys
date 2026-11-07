package ua.zapys.ui
import android.app.AlertDialog
import android.view.View
import ua.zapys.MainActivity
import ua.zapys.domain.*
import ua.zapys.device.MessengerShare

object RemindersScreen {
 fun create(a:MainActivity,rows:List<Reminder>):View {
  val t=a.palette; val p=a.form("Нагадування","settings"); val error=a.errorLabel(p)
  p.addView(t.text("До 1 нагадування на запис. Автоматичне надсилання не підключено. Системне меню не підтверджує надсилання або доставку."))
  p.addView(t.button("Оновити чергу") { a.openReminders() })
  if(rows.isEmpty()) p.addView(t.text("Нагадувань немає"))
  rows.forEach { row ->
   if(row.scheduledAt>System.currentTimeMillis() && row.state in setOf(ReminderState.NEEDS_ACTION,ReminderState.ERROR)) p.addView(t.text("Очікує часу підготовки після зміни годинника. Попередній результат збережено."))
   val label=when(row.state) {
    ReminderState.SCHEDULED->"Заплановано — очікує часу підготовки"
    ReminderState.NEEDS_ACTION->"Потрібна ручна дія"
    ReminderState.HANDED_OFF->"Передано до застосунку. Надсилання не підтверджено; результат або скасування невідомі."
    ReminderState.ERROR->"Не вдалося відкрити повідомлення. Можна повторити."
    ReminderState.CANCELLED->"Нагадування скасовано"
   }
   p.addView(t.text("${row.channel.title}\n$label"))
   if(DeviceRules.actionableAt(row.id,rows,System.currentTimeMillis())!=null) {
    p.addView(t.button("Підготувати повідомлення") {
     a.loadForExternal({ DeviceRules.actionableAt(row.id,it.reminders(),System.currentTimeMillis()) }) preview@{ current ->
      if(current==null) { error.text="Нагадування вже не актуальне. Оновіть чергу."; return@preview }
      AlertDialog.Builder(a).setTitle(current.channel.title)
       .setNeutralButton("Копіювати текст") { _,_-> a.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Zapys",current.text)) }
       .setMessage("${current.text}\n\nОберіть одержувача вручну в месенджері. Повернення або скасування в іншому застосунку не підтверджує надсилання.")
       .setNegativeButton("Назад",null).setPositiveButton("Відкрити месенджер") { _,_->
        a.loadForExternal({ r -> DeviceRules.actionableAt(current.id,r.reminders(),System.currentTimeMillis()) to r.appointments().firstOrNull { it.id==current.appointmentId } }) open@{ (latest,appointment) ->
         val starts=appointment?.let { it.date.atStartOfDay().plusMinutes(it.startMinute.toLong()).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() } ?: 0
         if(latest==null || latest.scheduledAt>System.currentTimeMillis() || starts<=System.currentTimeMillis()) { error.text="Нагадування вже не актуальне."; return@open }
         val outcome=try { a.startActivity(MessengerShare.intent(latest)); ReminderState.HANDED_OFF } catch(_: Exception) { ReminderState.ERROR }
         a.save(error,{ it.setReminderState(latest.id,outcome) }) { a.openReminders() }
        }
       }.show()
     }
    })
    p.addView(t.button("Скасувати нагадування") { a.save(error,{ it.setReminderState(row.id,ReminderState.CANCELLED) }) { a.openReminders() } })
   }
  }
  return t.scroll(p)
 }
}
