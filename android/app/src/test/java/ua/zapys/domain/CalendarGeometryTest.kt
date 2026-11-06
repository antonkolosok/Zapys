package ua.zapys.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class CalendarGeometryTest {
 private val day=LocalDate.of(2026,12,31)
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
