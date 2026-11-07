package ua.zapys.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class AppointmentSaveSessionTest {
 @Test fun successiveInlineCreationsKeepDistinctIdentityAndFirstClientUnchanged() {
  val d=form(); val s=AppointmentSaveSession(d,emptyList(),"first","First","0671234567")
  val first=s.prepare(AppointmentStatus.DRAFT).newClient!!
  assertEquals(first,s.prepare(AppointmentStatus.DRAFT).newClient)
  s.clientPersisted(first)
  assertNotEquals(first.id,s.inlineId)
  s.inlineName="Second"; s.inlinePhone="0501234567"
  d.clientId=null // Explicitly activate another inline creation.
  // Activity recreation restores the current unsaved identity and raw input.
  val restored=AppointmentSaveSession(d,s.clients,s.inlineId,s.inlineName,s.inlinePhone)
  val second=restored.prepare(AppointmentStatus.DRAFT).newClient!!
  assertNotEquals(first.id,second.id)
  assertEquals(second,restored.prepare(AppointmentStatus.DRAFT).newClient)
  restored.clientPersisted(second)
  assertEquals(first,restored.clients.single { it.id==first.id })
  assertEquals(second,restored.clients.single { it.id==second.id })
  assertNotEquals(second.id,restored.inlineId)
 }
 private fun form()=AppointmentDraft.fresh().apply { end="10:00" }
 @Test fun selectedClientWinsOverInvalidHiddenInlineInputForEverySaveAction() {
  val c=Client(id="selected",firstName="Existing",phone="legacy"); val d=form().apply { clientId=c.id }
  val s=AppointmentSaveSession(d,listOf(c),"unused","x".repeat(51),"not a phone")
  listOf(AppointmentStatus.DRAFT,AppointmentStatus.PLANNED).forEach { status ->
   val request=s.prepare(status)
   assertEquals(c.id,request.appointment.clientId)
   assertNull(request.newClient)
  }
  assertEquals(1,s.clients.size); assertEquals("x".repeat(51),s.inlineName); assertEquals("not a phone",s.inlinePhone)
 }
 @Test fun clearingSelectionActivatesInlineValidationWithoutLosingRawInput() {
  val c=Client(id="selected",firstName="Existing",phone="legacy"); val d=form().apply { clientId=c.id }
  val s=AppointmentSaveSession(d,listOf(c),"inline"," Raw name ","bad")
  assertNull(s.prepare(AppointmentStatus.PLANNED).newClient)
  d.clientId=null
  try { s.prepare(AppointmentStatus.DRAFT); fail("Active invalid inline phone accepted") } catch(_:ValidationException) {}
  assertEquals(" Raw name ",s.inlineName); assertEquals("bad",s.inlinePhone)
  s.inlinePhone=" +380 (67) 000-07-01 "
  val request=s.prepare(AppointmentStatus.PLANNED)
  assertEquals("inline",request.appointment.clientId); assertEquals("Raw name",request.newClient!!.firstName)
  assertEquals(" +380 (67) 000-07-01 ",request.newClient!!.phone)
 }
 @Test fun failedAppointmentSaveAndRecreationReusePersistedInlineIdentity() {
  val d=form(); val s=AppointmentSaveSession(d,emptyList(),"persisted","Synthetic","0670000702")
  val created=s.prepare(AppointmentStatus.PLANNED).newClient!!
  s.clientPersisted(created) // Appointment transaction fails after this boundary.
  val restored=AppointmentSaveSession(d,s.clients,s.inlineId,"hidden stale","invalid")
  val retry=restored.prepare(AppointmentStatus.PLANNED)
  assertEquals("persisted",retry.appointment.clientId); assertNull(retry.newClient)
  assertEquals(listOf(created),restored.clients)
 }
 @Test fun draftIncludesInlineClientAndInvalidInputIsRetained() {
  val s=AppointmentSaveSession(form(),emptyList(),"client","New","")
  try { s.prepare(AppointmentStatus.DRAFT); fail() } catch(_:ValidationException) {}
  assertEquals("New",s.inlineName); assertEquals("",s.inlinePhone)
  s.inlinePhone="0671234567"; val request=s.prepare(AppointmentStatus.DRAFT)
  assertEquals("client",request.appointment.clientId); assertEquals("New",request.newClient!!.firstName)
 }
 @Test fun failedOrDeclinedDraftRequestDoesNotChangeVisibleStatus() {
  val c=Client(id="c",firstName="Existing",phone="100"); val d=form().apply { clientId=c.id; status=AppointmentStatus.COMPLETED }
  val s=AppointmentSaveSession(d,listOf(c),"new","","")
  assertEquals(AppointmentStatus.DRAFT,s.prepare(AppointmentStatus.DRAFT).appointment.status)
  assertEquals(AppointmentStatus.COMPLETED,d.status)
  assertEquals(AppointmentStatus.COMPLETED,s.prepare(d.status).appointment.status)
 }
 @Test fun persistedInlineClientTransitionsToSelectionAndDoesNotOverrideLaterSelection() {
  val other=Client(id="other",firstName="Other",phone="200"); val d=form(); val s=AppointmentSaveSession(d,listOf(other),"new","New","0671234567")
  val request=s.prepare(AppointmentStatus.PLANNED); s.clientPersisted(request.newClient!!)
  assertEquals("new",d.clientId); assertEquals("",s.inlineName); assertEquals("",s.inlinePhone)
  d.clientId=other.id
  val retry=s.prepare(AppointmentStatus.DRAFT)
  assertEquals(other.id,retry.appointment.clientId); assertNull(retry.newClient)
 }
}
