package ua.zapys.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
class FormRulesTest {
 @Test fun duplicateDetectionExcludesSelfAndAllowsSeparateRecords() {
  val a = Client(id="a", firstName="Olena", phone="+380 (67) 123-45-67")
  assertTrue(FormRules.duplicates(a, listOf(a)).isEmpty())
  assertEquals(listOf(a), FormRules.duplicates(a.copy(id="b", phone="380671234567"), listOf(a)))
 }
 @Test fun durationRejectsMalformedRatherThanBecomingUnknown() {
  assertNull(FormRules.duration("")); assertEquals(45, FormRules.duration("45"))
  for (raw in listOf("abc", "0", "2.5", "1440")) {
   try { FormRules.duration(raw); fail(raw) } catch (_: ValidationException) { }
  }
 }
 @Test fun searchMatchesFullNameAndFormattedPhone() {
  val c = Client(firstName="Олена", surname="Коваль", phone="+380 67 123 45 67")
  assertTrue(FormRules.matches(c, "коваль")); assertTrue(FormRules.matches(c, "6712345")); assertFalse(FormRules.matches(c,"Марія"))
 }
 @Test fun historyUsesEndWallTimeWithExactEndInHistory() {
  val now = LocalDateTime.of(2026, 11, 3, 18, 0)
  val today = now.toLocalDate()
  fun visit(start: Int, end: Int) = Appointment(date=today, startMinute=start, endMinute=end)
  assertTrue(FormRules.isHistory(visit(540,600),now))
  assertFalse(FormRules.isHistory(visit(1140,1200),now))
  assertFalse(FormRules.isHistory(visit(1050,1110),now)) // in progress
  assertTrue(FormRules.isHistory(visit(1020,1080),now)) // exact end
  assertTrue(FormRules.isHistory(visit(1140,1200).copy(date=today.minusDays(1)),now))
  assertFalse(FormRules.isHistory(visit(540,600).copy(date=today.plusDays(1)),now))
  assertFalse(FormRules.isHistory(visit(1050,1110).copy(status=AppointmentStatus.COMPLETED),now))
 }}



