package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ClientPhoneValidationTest {
 @Test fun clientWriteRejectsShortPhone() {
  assertThrows(ValidationException::class.java) {
   Rules.validateClient(Client(firstName="Synthetic", phone="123"))
  }
 }
 @Test fun equivalentUkrainianFormatsAreDuplicatesButSelfIsExcluded() {
  val old=Client(id="old",firstName="First",phone="+380 (67) 123-45-67")
  assertEquals(listOf(old),FormRules.duplicates(old.copy(id="new",phone="0671234567"),listOf(old)))
  assertTrue(FormRules.duplicates(old,listOf(old)).isEmpty())
 }
 @Test fun invalidLegacyPhonesAreNotTreatedAsCanonicalDuplicates() {
  val old=Client(id="old",firstName="First",phone="123")
  assertTrue(FormRules.duplicates(old.copy(id="new"),listOf(old)).isEmpty())
 }
 @Test fun appointmentWithLegacyClientDoesNotValidateClientPhone() {
  val old=Client(id="old",firstName="First",phone="123")
  Rules.validateAppointment(Appointment(date=LocalDate.of(2030,1,2),startMinute=540,endMinute=570,clientId=old.id,status=AppointmentStatus.PLANNED),listOf(old))
 }
 @Test fun invalidInlinePhonePreservesRawInputAndDoesNotSelectClient() {
  val draft=AppointmentDraft.fresh().apply { end="10:00" }
  val session=AppointmentSaveSession(draft,emptyList(),"new","Synthetic","123")
  assertThrows(ValidationException::class.java) { session.prepare(AppointmentStatus.PLANNED) }
  assertEquals("123",session.inlinePhone)
  assertNull(draft.clientId)
  assertTrue(session.clients.isEmpty())
 }
 @Test fun inlineSaveRetainsPresentationSpacesAndNbsp() {
  val draft=AppointmentDraft.fresh().apply { end="10:00" }
  val raw=" \u00a0+380 (67) 123-45-67\u00a0 "
  val request=AppointmentSaveSession(draft,emptyList(),"new","Synthetic",raw).prepare(AppointmentStatus.DRAFT)
  assertEquals(raw,request.newClient!!.phone)
 }
}
