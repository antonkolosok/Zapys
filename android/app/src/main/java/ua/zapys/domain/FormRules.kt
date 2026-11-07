package ua.zapys.domain
object FormRules {
 // Chronological partition: in-progress visits remain upcoming; exact end is history.
 fun isHistory(appointment: Appointment, now: java.time.LocalDateTime): Boolean =
  !appointment.date.atStartOfDay().plusMinutes(appointment.endMinute.toLong()).isAfter(now)
 private fun digits(raw: String) = raw.filter(Char::isDigit)
 fun duplicates(client: Client, all: List<Client>): List<Client> {
  val phone=PhoneRules.normalize(client.phone) ?: return emptyList()
  return all.filter { it.id != client.id && PhoneRules.normalize(it.phone) == phone }
 }
 fun duration(raw: String): Int? {
  if (raw.isBlank()) return null
  val value = raw.trim().toIntOrNull()
  if (value == null || value !in 1..1439) throw ValidationException("Тривалість: ціле число від 1 до 1439 хв")
  return value
 }
 fun matches(client: Client, query: String): Boolean {
  val q = query.trim()
  if (q.isEmpty() || client.displayName.contains(q, ignoreCase=true) || client.phone.contains(q, ignoreCase=true)) return true
  val numeric = digits(q)
  return numeric.isNotEmpty() && q.all { it.isDigit() || it in "+()- " } && digits(client.phone).contains(numeric)
 }
}


