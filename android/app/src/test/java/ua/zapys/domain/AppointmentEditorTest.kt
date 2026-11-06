package ua.zapys.domain
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
class AppointmentEditorTest {
 @Test fun requiredEndAndDraftClient() { val e=AppointmentDraft.fresh("id",LocalDate.of(2026,1,1)); assertEquals("",e.end); try { e.build(emptyList()); fail() } catch(_:ValidationException) {}; e.end="10:00"; assertEquals(null,e.build(emptyList()).clientId) }
 @Test fun orderedSnapshotsAndManualEnd() { val e=AppointmentDraft.fresh("id",LocalDate.now()); e.end="12:00"; e.manualEnd=true; repeat(5) { e.add(Service(id="$it",name="$it",durationMinutes=30).snapshot()) }; assertEquals((0..4).map { "$it" },e.services.map { it.serviceId }); assertEquals("12:00",e.end); try { e.add(Service(name="six").snapshot()); fail() } catch(_:ValidationException) {} }
 @Test fun repeatResetsAndAsksTime() { val old=Appointment(date=LocalDate.now(),startMinute=600,endMinute=660,paid=true,receivedMinor=2,status=AppointmentStatus.COMPLETED); val e=AppointmentDraft.repeat(old); assertNotEquals(old.id,e.id); assertEquals("",e.date); assertEquals("",e.start); assertEquals("",e.end); assertFalse(e.paid); assertEquals(AppointmentStatus.DRAFT,e.status) }
}
