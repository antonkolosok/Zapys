package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarPresentationTest {
 @Test fun compactWeekTimeAndClientAnchorsPreserveAllServicePositions() {
  val shortStatuses=listOf("Черн.","План","Вик.","Скас.","Неяв.")
  AppointmentStatus.entries.forEachIndexed { index,status ->
   val a=appointment(status).copy(services=listOf(ServiceSnapshot("unset","Unset"),ServiceSnapshot("color","Color",color=0xffaa0000.toInt())))
   for(rowHeight in listOf(24f,48f)) {
    val day=CalendarPresentation.rows(a,36f,rowHeight)
    val week=CalendarPresentation.rows(a,36f,rowHeight,week=true)
    assertEquals("10:00",week.compactTime)
    assertEquals("${shortStatuses[index]} · ${a.clientName}",week.labels.first())
    assertEquals(day.labels.drop(1),week.labels.drop(1))
    assertEquals(listOf(4f+rowHeight*0.75f,4f+rowHeight*1.5f,4f+rowHeight*2.5f),week.labelCenters(rowHeight))
    assertEquals(day.labelCenters(rowHeight).drop(1),week.labelCenters(rowHeight).drop(1))
    assertNull(day.compactTime)
    val stops=CalendarGeometry.gradient(listOf(a.clientColor,null,a.services[1].color),week.labelCenters(rowHeight))
    assertEquals(listOf(4f+rowHeight*0.75f,4f+rowHeight*2.5f),stops.map { it.center })
    for(isWeek in listOf(false,true)) {
     val tall=CalendarPresentation.rows(a,400f,rowHeight,week=isWeek)
     assertNull(tall.compactTime); assertNotNull(tall.metadata)
     assertEquals(listOf(a.clientName,"Unset","Color"),tall.labels)
     assertEquals(listOf(4f+rowHeight*0.5f,4f+rowHeight*1.5f,4f+rowHeight*2.5f),tall.labelCenters(rowHeight))
    }
   }
  }
 }
 private fun appointment(status:AppointmentStatus)=Appointment("a",LocalDate.of(2026,11,3),600,900,clientName="Ірина",clientColor=0xff008800.toInt(),services=listOf(ServiceSnapshot("s","Послуга",color=0xffaa0000.toInt())),status=status)
 @Test fun tallColoredRecordsKeepLabelsAndShowStatusBeforeTime() {
  listOf(AppointmentStatus.DRAFT,AppointmentStatus.PLANNED,AppointmentStatus.COMPLETED,AppointmentStatus.NO_SHOW).forEach { status->
   val p=CalendarPresentation.rows(appointment(status),360f,24f)
   assertEquals(listOf("Ірина","Послуга"),p.labels)
   assertEquals("${status.title} · 10:00–15:00",p.metadata)
  }
 }
 @Test fun clippedCompactStatusDoesNotMoveServiceRows() {
  val a=appointment(AppointmentStatus.DRAFT)
  val p=CalendarPresentation.rows(a,12f,24f)
  assertEquals("${a.status.title} · Ірина",p.labels.first())
  assertEquals("Послуга",p.labels[1]); assertNull(p.metadata)
  assertEquals(2,p.labels.size)
  assertEquals(listOf(CalendarStop(a.clientColor!!,16f),CalendarStop(a.services[0].color!!,40f)),CalendarGeometry.gradient(listOf(a.clientColor,a.services[0].color),p.labels.indices.map { 4f+24f*(it+0.5f) }))
 }
 @Test fun metadataFitsOnlyWhenWholeRowFits() {
  val a=appointment(AppointmentStatus.COMPLETED)
  assertNull(CalendarPresentation.rows(a,75f,24f).metadata)
  assertNotNull(CalendarPresentation.rows(a,76f,24f).metadata)
 }
}
