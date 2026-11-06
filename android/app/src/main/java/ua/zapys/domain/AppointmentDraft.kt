package ua.zapys.domain
import java.time.LocalDate

/** Raw form state deliberately keeps invalid/blank input until successful validation. */
class AppointmentDraft(val id: String, var date: String, var start: String, var end: String,
 var clientId: String?=null, val services: MutableList<ServiceSnapshot> = mutableListOf(),
 var note: String="", var status: AppointmentStatus=AppointmentStatus.DRAFT, var paid: Boolean=false,
 var received: String="", var manualEnd: Boolean=false) {
 fun add(service: ServiceSnapshot) { if(services.size>=Limits.MAX_SERVICES) throw ValidationException("У записі вже 5 послуг"); services.add(service); suggest() }
 fun suggest() { if(manualEnd || services.isEmpty() || services.any { it.durationMinutes==null }) return
  val minute=try { Rules.parseTime(start) } catch(_:ValidationException) { return }
  end=Rules.time((minute+services.sumOf { it.durationMinutes!! }).coerceAtMost(1439))
 }
 fun build(clients: List<Client>, requestedStatus: AppointmentStatus=status, selectedClientId: String?=clientId): Appointment {
  val day=try { LocalDate.parse(date) } catch(_:Exception) { throw ValidationException("Вкажіть дату РРРР-ММ-ДД") }
  val result=Appointment(id=id,date=day,startMinute=Rules.parseTime(start),endMinute=Rules.parseTime(end),clientId=selectedClientId,services=services.toList(),note=note,status=requestedStatus,paid=paid,receivedMinor=Rules.parseMoney(received),manualEnd=manualEnd)
  Rules.validateAppointment(result,clients); return result
 }
 companion object {
  fun fresh(id: String=newId(), date: LocalDate=LocalDate.now(), start: Int=540) = AppointmentDraft(id,date.toString(),Rules.time(start),"")
  fun from(a: Appointment) = AppointmentDraft(a.id,a.date.toString(),Rules.time(a.startMinute),Rules.time(a.endMinute),a.clientId,a.services.toMutableList(),a.note,a.status,a.paid,Rules.formatMoney(a.receivedMinor),a.manualEnd)
  fun repeat(a: Appointment) = from(a).let { AppointmentDraft(newId(),"","","",it.clientId,it.services.toMutableList(),it.note) }
 }
}
