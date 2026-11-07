package ua.zapys.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Rules {
    fun validateClient(c: Client) {
        requireValid(!c.reminder.enabled || c.reminder.leadMinutes>0,"Увімкнене нагадування потребує щонайменше 1 хвилини до запису")
        PersonNameRules.firstNameError(c.firstName)?.let { throw ValidationException(it) }
        PersonNameRules.surnameError(c.surname)?.let { throw ValidationException(it) }
        PersonNameRules.patronymicError(c.patronymic)?.let { throw ValidationException(it) }
        requireValid(PhoneRules.normalize(c.phone) != null, PhoneRules.INVALID_MESSAGE)
        note(c.note)
        requireValid(c.reminder.leadMinutes in 0..525600, "Час нагадування: від 0 до 525600 хв")
        note(c.reminder.template)
    }
    fun validateService(s: Service) {
        requireValid(s.name.isNotBlank(), "Вкажіть назву послуги")
        requireValid(s.durationMinutes == null || s.durationMinutes in 1..1439, "Тривалість: від 1 до 1439 хв")
        requireValid(s.priceMinor == null || s.priceMinor >= 0, "Ціна не може бути від'ємною")
        note(s.note)
    }
    fun validateAppointment(a: Appointment, clients: List<Client>) {
        requireValid(a.startMinute in 0..1438 && a.endMinute in 1..1439 && a.endMinute > a.startMinute, "Кінець має бути пізніше початку, до 23:59 цього дня")
        requireValid(a.services.size <= Limits.MAX_SERVICES, "Максимум 5 послуг у записі")
        if (a.status != AppointmentStatus.DRAFT) requireValid(a.clientId != null && clients.any { it.id == a.clientId }, "Оберіть клієнта")
        if (a.clientId != null) requireValid(clients.any { it.id == a.clientId }, "Клієнта не знайдено")
        a.services.forEach { validateService(Service(it.serviceId, it.name, it.durationMinutes, it.priceMinor, it.note, it.color)) }
        requireValid(a.receivedMinor == null || a.receivedMinor >= 0, "Отримана сума не може бути від'ємною")
        note(a.note)
    }
    private fun requireValid(ok: Boolean, text: String) { if (!ok) throw ValidationException(text) }
    fun note(value: String) { requireValid(value.length <= Limits.MAX_NOTE, "Максимум 2000 символів") }
    fun overlaps(a: Appointment, b: Appointment): Boolean = a.date == b.date && a.startMinute < b.endMinute && b.startMinute < a.endMinute
    fun conflicts(a: Appointment, all: List<Appointment>): List<Appointment> = if (a.status == AppointmentStatus.CANCELLED) emptyList() else all.filter { it.id != a.id && it.status != AppointmentStatus.CANCELLED && overlaps(a, it) }
    fun calendarDraft(date: LocalDate, minute: Int): Appointment {
        val start = minute.coerceIn(0, 1409)
        return Appointment(date = date, startMinute = start, endMinute = start + 30)
    }
    fun snapEnd(start: Int, minute: Int): Int = ((minute + 7) / 15 * 15).coerceIn(start + 1, 1439)
    fun suggestEnd(a: Appointment): Int {
        if (a.manualEnd || a.services.isEmpty() || a.services.any { it.durationMinutes == null }) return a.endMinute
        return (a.startMinute + a.services.sumOf { it.durationMinutes!! }).coerceAtMost(1439)
    }
    fun repeat(a: Appointment, date: LocalDate, startMinute: Int): Appointment {
        val duration = a.endMinute - a.startMinute
        val start = startMinute.coerceIn(0, 1438)
        return a.copy(id = newId(), date = date, startMinute = start, endMinute = (start + duration).coerceAtMost(1439), status = AppointmentStatus.DRAFT, paid = false, receivedMinor = null, manualEnd = false)
    }
    fun parseMoney(raw: String): Long? {
        if (raw.isBlank()) return null
        return try {
            val decimal = BigDecimal(raw.trim().replace(',', '.'))
            requireValid(decimal.signum() >= 0 && decimal.scale() <= 2, "Сума: невід'ємне число з двома знаками після коми")
            decimal.movePointRight(2).longValueExact()
        } catch (e: NumberFormatException) { throw ValidationException("Некоректна сума") }
        catch (e: ArithmeticException) { throw ValidationException("Некоректна сума") }
    }
    fun formatMoney(minor: Long?): String = minor?.let { BigDecimal.valueOf(it, 2).toPlainString() } ?: ""
    fun time(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
    fun parseTime(value: String): Int {
        val parts = value.trim().split(':')
        val h = parts.getOrNull(0)?.toIntOrNull()
        val m = parts.getOrNull(1)?.toIntOrNull()
        requireValid(parts.size == 2 && h != null && h in 0..23 && m != null && m in 0..59, "Час у форматі ГГ:ХХ, до 23:59")
        return h!! * 60 + m!!
    }
    fun renderTemplate(template: String, a: Appointment, c: Client): String {
        val values = mapOf("ім'я" to c.firstName, "ім’я" to c.firstName, "дата" to a.date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")), "час" to time(a.startMinute), "послуги" to a.services.joinToString(", ") { it.name })
        return Regex("\\[(ім'я|ім’я|дата|час|послуги)]").replace(template) { values[it.groupValues[1]] ?: it.value }
    }
    fun reminder(a: Appointment, client: Client?, now: Long, zone: ZoneId = ZoneId.systemDefault()): Reminder? {
        if (a.status != AppointmentStatus.PLANNED || client == null || !client.reminder.enabled) return null
        val starts = a.date.atStartOfDay().plusMinutes(a.startMinute.toLong()).atZone(zone).toInstant().toEpochMilli()
        if (starts <= now) return null
        val scheduled = starts - client.reminder.leadMinutes * 60000L
        return Reminder(appointmentId = a.id, scheduledAt = scheduled, text = renderTemplate(client.reminder.template, a, client), channel = client.reminder.channel, recipient = client.reminder.recipient, state = if (scheduled <= now) ReminderState.NEEDS_ACTION else ReminderState.SCHEDULED)
    }
}
