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
