package ua.zapys.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class AppointmentEditorTest {
 @Test fun freshPrimarySaveIsPlannedAndRequiresClientAfterEndIsProvided() {
  val form=AppointmentDraft.fresh("fresh-planned",LocalDate.of(2031,4,5),600)
  assertEquals(AppointmentStatus.PLANNED,form.status)
  assertEquals("",form.end)
  try { form.build(emptyList()); fail("Blank end must be rejected") } catch(_:ValidationException) {}
  form.end="10:30"
  try { form.build(emptyList()); fail("Primary save requires a client") } catch(_:ValidationException) {}
  val client=Client(id="planned-client",firstName="Synthetic",phone="0670000801")
  form.clientId=client.id
  val saved=form.build(listOf(client))
  assertEquals(AppointmentStatus.PLANNED,saved.status); assertEquals(client.id,saved.clientId)
  assertEquals(630,saved.endMinute); assertEquals("fresh-planned",saved.id)
 }
 @Test fun requiredEndAndDraftClient() { val e=AppointmentDraft.fresh("id",LocalDate.of(2026,1,1)); assertEquals("",e.end); try { e.build(emptyList(),AppointmentStatus.DRAFT); fail() } catch(_:ValidationException) {}; e.end="10:00"; assertEquals(null,e.build(emptyList(),AppointmentStatus.DRAFT).clientId) }
 @Test fun orderedSnapshotsAndManualEnd() { val e=AppointmentDraft.fresh("id",LocalDate.now()); e.end="12:00"; e.manualEnd=true; repeat(5) { e.add(Service(id="$it",name="$it",durationMinutes=30).snapshot()) }; assertEquals((0..4).map { "$it" },e.services.map { it.serviceId }); assertEquals("12:00",e.end); try { e.add(Service(name="six").snapshot()); fail() } catch(_:ValidationException) {} }
 @Test fun repeatResetsAndAsksTime() { val old=Appointment(date=LocalDate.now(),startMinute=600,endMinute=660,paid=true,receivedMinor=2,status=AppointmentStatus.COMPLETED); val e=AppointmentDraft.repeat(old); assertNotEquals(old.id,e.id); assertEquals("",e.date); assertEquals("",e.start); assertEquals("",e.end); assertFalse(e.paid); assertEquals(AppointmentStatus.DRAFT,e.status) }
}
