package ua.zapys.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CalendarGeometryTest {
 private val day=LocalDate.of(2026,12,31)
 @Test fun moveSnapsDeltaAndPreservesEveryOtherField() {
  val original=Appointment("move",day,600,660,"client","Historical","legacy",123,listOf(ServiceSnapshot("s","Snapshot",60,123,"service note",456)),"note",AppointmentStatus.COMPLETED,true,999,true)
  assertEquals(original.copy(startMinute=630,endMinute=690),CalendarGeometry.move(original,day,30f))
  assertEquals(original,CalendarGeometry.move(original,day,7f))
  assertEquals(original.copy(startMinute=615,endMinute=675),CalendarGeometry.move(original,day,8f))
  assertEquals(original.copy(startMinute=585,endMinute=645),CalendarGeometry.move(original,day,-8f))
  assertEquals(600,original.startMinute)
 }
 @Test fun horizontalMovePreservesNonQuarterMinutesAndCrossesYear() {
  val original=Appointment("move",day,607,667)
  assertEquals(original.copy(date=LocalDate.of(2027,1,1)),CalendarGeometry.move(original,LocalDate.of(2027,1,1),0f))
 }
 @Test fun moveClampsWithoutShorteningDuration() {
  val original=Appointment("move",day,607,667)
  assertEquals(original.copy(startMinute=0,endMinute=60),CalendarGeometry.move(original,day,-10000f))
  assertEquals(original.copy(startMinute=1379,endMinute=1439),CalendarGeometry.move(original,day,10000f))
 }
 @Test fun moveRejectsInvalidCoordinates() {
  val original=Appointment("move",day,600,660)
  for(delta in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY)) {
   try { CalendarGeometry.move(original,day,delta); fail("Non-finite delta accepted") } catch(_:IllegalArgumentException) {}
  }
  for(value in listOf(original.copy(endMinute=600),original.copy(startMinute=-1),original.copy(endMinute=1440),original.copy(endMinute=599))) {
   try { CalendarGeometry.move(value,day,0f); fail("Invalid interval accepted") } catch(_:IllegalArgumentException) {}
  }
 }
 @Test fun finalDraftAndNavigation() {
  val d=CalendarGeometry.draft(day,1439f,"d")
  assertEquals(1409,d.startMinute); assertEquals(1439,d.endMinute)
  assertEquals(36f,CalendarGeometry.height(d.startMinute,d.endMinute),0.001f)
  assertEquals(LocalDate.of(2027,1,7),CalendarGeometry.navigate(day,1))
  assertEquals(LocalDate.of(2026,12,28),CalendarGeometry.weekDates(day).first())
 }
 @Test fun connectedGroupUsesPeakAndAdjacentReuse() {
  fun a(id:String,s:Int,e:Int)=Appointment(id,day,s,e)
  val lanes=CalendarGeometry.lanes(listOf(a("a",0,60),a("b",15,30),a("c",30,90),a("d",90,100),a("x",0,100).copy(status=AppointmentStatus.CANCELLED)))
  assertEquals(listOf(2,2,2,1),lanes.map { it.laneCount })
  assertEquals(listOf(0,1,1,0),lanes.map { it.lane })
 }
 @Test fun resizeDoesNotMutateOriginalAndClamps() {
  val a=Appointment("a",day,1409,1439)
  assertEquals(1410,CalendarGeometry.resize(a,1400f).endMinute)
  assertEquals(1439,CalendarGeometry.resize(a,1500f).endMinute)
  assertEquals(1439,a.endMinute)
 }
 @Test fun gradientKeepsUnsetRowAndClippedCenters() {
  assertEquals(listOf(CalendarStop(1,16f),CalendarStop(3,64f)),CalendarGeometry.gradient(listOf(1,null,3),listOf(16f,40f,64f)))
  assertTrue(CalendarGeometry.gradient(listOf(null),listOf(16f)).isEmpty())
  assertEquals(1,CalendarGeometry.gradient(listOf(1),listOf(16f)).size)
 }
}
