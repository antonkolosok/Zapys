package ua.zapys.ui

import android.text.InputType
import android.view.View
import ua.zapys.MainActivity
import ua.zapys.domain.*

object SettingsScreen {
 fun form(a: MainActivity, original: LocalProfile): View {
  val t=a.palette; val p=a.form("Налаштування","calendar")
  p.addView(t.text("Локальний профіль",20f))
  p.addView(t.text("Не підтверджено. Це локальні дані, а не вхід у хмарний акаунт."))
  val name=a.field(p,"Ім’я (необов’язково)",original.name)
  val phone=a.field(p,"Телефон (необов’язково)",original.phone,InputType.TYPE_CLASS_PHONE)
  val email=a.field(p,"Email (необов’язково)",original.email,InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
  val error=a.errorLabel(p)
  val notifications=android.widget.Switch(a).apply { text="Локальні сповіщення для мене"; isChecked=original.notificationsEnabled; a.identify(this); setOnCheckedChangeListener { _,_->a.changed() } }; p.addView(notifications)
  p.addView(t.text("Збережіть вибір. Сповіщення можуть затримуватися через обмеження ОС. Без дозволу черга залишається доступною."))
  p.addView(t.button("Дозвіл / системні налаштування сповіщень") { a.requestNotifications() })
  p.addView(t.button("Черга нагадувань") { a.openReminders() })
  if(!ua.zapys.device.ReminderScheduler(a).allowed()) p.addView(t.text("Сповіщення вимкнено в системі. Черга доступна в застосунку."))
  p.addView(t.button("Зберегти профіль") {
   val invalid=listOf(name,phone,email).firstOrNull { it.length()>Limits.MAX_NOTE }
   if(invalid!=null) { invalid.error="Максимум ${Limits.MAX_NOTE} символів"; invalid.requestFocus(); return@button }
   val profile=original.copy(phone=phone.text.toString().trim(),email=email.text.toString().trim(),name=name.text.toString().trim(),notificationsEnabled=notifications.isChecked)
   a.save(error,{ it.saveProfile(profile) }) { error.text="Профіль збережено на пристрої. Дані не підтверджено." }
  })
  p.addView(t.text("Дані зберігаються на цьому пристрої. Хмарна синхронізація ще не підключена."))
  p.addView(t.text("Сповіщення та нагадування",20f)); p.addView(t.text("Автоматичне надсилання не підключено. Дозвіл на сповіщення та ручна черга нагадувань будуть доступні після підключення системної інтеграції."))
  p.addView(t.text("Технічні обмеження локального MVP",20f)); p.addView(t.text("До ${Limits.MAX_CLIENTS} клієнтів, ${Limits.MAX_APPOINTMENTS} записів; до ${Limits.MAX_NOTE} символів у нотатці або шаблоні. Це технічні обмеження цього локального MVP, не платний тариф. До ${Limits.MAX_SERVICES} послуг та ${Limits.MAX_REMINDERS} нагадування на один запис."))
  return t.scroll(p)
 }
}

