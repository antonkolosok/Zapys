package ua.zapys.domain

import java.time.LocalDate
import kotlin.math.roundToInt

data class CalendarLane(val appointment: Appointment, val lane: Int, val laneCount: Int)
data class CalendarStop(val color: Int, val center: Float)

/** Pure dp/minute rules shared by drawing, hit testing and accessible actions. */
object CalendarGeometry {
 const val SCALE=1.2f
 const val GUTTER=42f
 fun top(minute:Int)=minute*SCALE
 fun height(start:Int,end:Int)=(end-start)*SCALE
 fun weekDates(date:LocalDate):List<LocalDate> = date.minusDays((date.dayOfWeek.value-1).toLong()).let { monday->(0..6).map { monday.plusDays(it.toLong()) } }
 fun navigate(date:LocalDate,weeks:Int):LocalDate=date.plusWeeks(weeks.toLong())
 fun draft(date:LocalDate,minute:Float,id:String=newId()):Appointment {
  val start=((minute/15f).roundToInt()*15).coerceIn(0,1409)
  return Appointment(id,date,start,start+30)
 }
 fun resize(original:Appointment,minute:Float):Appointment=original.copy(endMinute=((minute/15f).roundToInt()*15).coerceIn(original.startMinute+1,1439),manualEnd=true)
 fun move(original:Appointment,date:LocalDate,deltaMinutes:Float):Appointment {
  require(deltaMinutes.isFinite()) { "Некоректне зміщення часу" }
  require(original.startMinute in 0..1438 && original.endMinute in 1..1439 && original.endMinute>original.startMinute) { "Некоректна тривалість запису" }
  val duration=original.endMinute-original.startMinute
  // Clamp before integer conversion, so even finite Float.MAX_VALUE cannot overflow.
  val steps=kotlin.math.round(deltaMinutes.toDouble()/15.0)
  val start=(original.startMinute+steps*15.0).coerceIn(0.0,(1439-duration).toDouble()).toInt()
  return original.copy(date=date,startMinute=start,endMinute=start+duration)
 }
 fun gradient(colors:List<Int?>,rowCenters:List<Float>):List<CalendarStop> {
  require(colors.size==rowCenters.size)
  return colors.mapIndexedNotNull { i,c->c?.let { CalendarStop(it,rowCenters[i]) } }
 }
 fun lanes(records:List<Appointment>):List<CalendarLane> {
  val result=mutableListOf<CalendarLane>()
  records.filter { it.status!=AppointmentStatus.CANCELLED }.groupBy { it.date }.toSortedMap().forEach { (_,day)->
   var group=mutableListOf<Pair<Appointment,Int>>()
   val ends=mutableListOf<Int>()
   fun flush() { group.forEach { (a,l)->result.add(CalendarLane(a,l,ends.size)) }; group=mutableListOf(); ends.clear() }
   day.sortedWith(compareBy<Appointment> { it.startMinute }.thenBy { it.endMinute }.thenBy { it.id }).forEach { a->
    if(ends.isNotEmpty() && ends.max()<=a.startMinute) flush()
    var lane=ends.indexOfFirst { it<=a.startMinute }
    if(lane<0) { lane=ends.size; ends.add(a.endMinute) } else ends[lane]=a.endMinute
    group.add(a to lane)
   }
   flush()
  }
  return result
 }
}
