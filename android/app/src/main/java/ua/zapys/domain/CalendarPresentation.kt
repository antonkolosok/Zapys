package ua.zapys.domain

data class CalendarRows(val labels:List<String>, val metadata:String?, val compactTime:String?=null) {
 fun labelCenters(rowHeight:Float,topPadding:Float=4f)=labels.indices.map { index ->
  topPadding+rowHeight*(if(index==0 && compactTime!=null) 0.75f else index+0.5f)
 }
}

object CalendarPresentation {
 /** Keep the client/service row count and positions fixed, even when clipped. */
 fun rows(appointment:Appointment,height:Float,rowHeight:Float,week:Boolean=false):CalendarRows {
  val labels=listOf(appointment.clientName.ifBlank { "Чернетка" })+appointment.services.map { it.name }
  val fits=height>=4f+rowHeight*(labels.size+1)
  val status=appointment.status.title
  fun time(minute:Int)="%02d:%02d".format(minute/60,minute%60)
  if(!fits && week) {
   val shortStatus=when(appointment.status) {
    AppointmentStatus.DRAFT->"Черн."
    AppointmentStatus.PLANNED->"План"
    AppointmentStatus.COMPLETED->"Вик."
    AppointmentStatus.CANCELLED->"Скас."
    AppointmentStatus.NO_SHOW->"Неяв."
   }
   return CalendarRows(listOf("$shortStatus · ${labels.first()}")+labels.drop(1),null,time(appointment.startMinute))
  }
  return if(fits) CalendarRows(labels,"$status · ${time(appointment.startMinute)}–${time(appointment.endMinute)}")
  else CalendarRows(listOf("$status · ${labels.first()}")+labels.drop(1),null)
 }
}
