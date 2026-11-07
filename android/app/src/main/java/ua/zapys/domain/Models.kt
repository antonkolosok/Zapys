package ua.zapys.domain

import java.time.LocalDate
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
object Limits {
    const val MAX_CLIENTS = 1000
    const val MAX_APPOINTMENTS = 10000
    const val MAX_NOTE = 2000
    const val MAX_PERSON_NAME = 50
    const val MAX_SERVICES = 5
    const val MAX_REMINDERS = 1
}
enum class Channel(val title: String) { VIBER("Viber"), WHATSAPP("WhatsApp"), TELEGRAM("Telegram"), INSTAGRAM("Instagram") }
data class ReminderPreferences(val enabled: Boolean = false, val channel: Channel = Channel.VIBER, val recipient: String = "", val leadMinutes: Int = 1440, val template: String = "[ім'я], нагадуємо про запис [дата] о [час]. [послуги]")
data class Client(val id: String = newId(), val firstName: String, val phone: String, val surname: String = "", val patronymic: String = "", val note: String = "", val color: Int? = null, val reminder: ReminderPreferences = ReminderPreferences()) {
    val displayName: String get() = listOf(firstName, surname, patronymic).filter { it.isNotBlank() }.joinToString(" ")
}
data class Service(val id: String = newId(), val name: String, val durationMinutes: Int? = null, val priceMinor: Long? = null, val note: String = "", val color: Int? = null) {
    fun snapshot() = ServiceSnapshot(id, name, durationMinutes, priceMinor, note, color)
}
data class ServiceSnapshot(val serviceId: String, val name: String, val durationMinutes: Int? = null, val priceMinor: Long? = null, val note: String = "", val color: Int? = null)
enum class AppointmentStatus(val title: String) { DRAFT("Чернетка"), PLANNED("Заплановано"), COMPLETED("Завершено"), CANCELLED("Скасовано"), NO_SHOW("Не з'явився / не з'явилась") }
data class Appointment(val id: String = newId(), val date: LocalDate, val startMinute: Int, val endMinute: Int, val clientId: String? = null, val clientName: String = "", val clientPhone: String = "", val clientColor: Int? = null, val services: List<ServiceSnapshot> = emptyList(), val note: String = "", val status: AppointmentStatus = AppointmentStatus.DRAFT, val paid: Boolean = false, val receivedMinor: Long? = null, val manualEnd: Boolean = false)
enum class ReminderState(val title: String) { SCHEDULED("Заплановано"), NEEDS_ACTION("Потрібно надіслати вручну"), HANDED_OFF("Передано до месенджера"), ERROR("Помилка відкриття"), CANCELLED("Скасовано") }
data class Reminder(val id: String = newId(), val appointmentId: String, val scheduledAt: Long, val text: String, val channel: Channel, val recipient: String, val state: ReminderState = ReminderState.SCHEDULED)
data class LocalProfile(val phone: String = "", val email: String = "", val name: String = "", val notificationsEnabled: Boolean = false)
class ValidationException(message: String) : IllegalArgumentException(message)
class ConflictException(val appointments: List<Appointment>) : IllegalStateException("Є перетин з іншими записами")
interface Repository {
    fun clients(): List<Client>
    fun services(): List<Service>
    fun appointments(): List<Appointment>
    fun reminders(): List<Reminder>
    fun saveClient(client: Client)
    fun deleteClient(id: String)
    fun saveService(service: Service)
    fun deleteService(id: String)
    fun saveAppointment(appointment: Appointment, allowOverlap: Boolean = false): Appointment
    fun deleteAppointment(id: String)
    fun setReminderState(id: String, state: ReminderState)
    fun profile(): LocalProfile
    fun saveProfile(profile: LocalProfile)
}
