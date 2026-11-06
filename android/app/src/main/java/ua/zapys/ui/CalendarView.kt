package ua.zapys.ui

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import ua.zapys.domain.*
import java.time.LocalDate
import kotlin.math.abs

/** Canvas uses dp geometry; the companion agenda supplies native accessible controls. */
class CalendarView(context:Context, private val theme:Theme, private val dates:List<LocalDate>, private val records:List<Appointment>,
 private val open:(Appointment)->Unit, private val empty:(LocalDate,Float)->Unit, private val resize:(Appointment)->Unit, private val previewTime:(Appointment?)->Unit):View(context) {
 private val density=resources.displayMetrics.density
 private val fontScale=resources.configuration.fontScale
 private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
 private var selected:String?=null
 private var preview:Appointment?=null
 private var dragging:Appointment?=null
 private var possibleHandle:Appointment?=null
 private val touchSlop=ViewConfiguration.get(context).scaledTouchSlop/density
 private var downX=0f; private var downY=0f; private var moved=false
 private var hit:Appointment?=null
 private val boxes=mutableListOf<Pair<Appointment,RectF>>()
 init { minimumHeight=(CalendarGeometry.top(1440)*density).toInt(); importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO; contentDescription="Календар; доступний список нижче" }
 override fun onMeasure(w:Int,h:Int) { setMeasuredDimension(MeasureSpec.getSize(w),minimumHeight) }
 override fun onDraw(canvas:Canvas) {
  super.onDraw(canvas); canvas.save(); canvas.scale(density,density)
  val width=width/density; val column=(width-CalendarGeometry.GUTTER)/dates.size
  paint.shader=null; paint.strokeWidth=0.5f
  for(minute in 0..1439 step 30) {
   val y=CalendarGeometry.top(minute); paint.color=theme.ink; paint.alpha=if(minute%60==0) 70 else 25
   canvas.drawLine(CalendarGeometry.GUTTER,y,width,y,paint)
   if(minute%60==0) { paint.alpha=255; paint.textSize=10f; canvas.drawText("%02d:00".format(minute/60),0f,y+11f,paint) }
  }
  paint.color=theme.ink; paint.alpha=40
  for(i in 0..dates.size) canvas.drawLine(CalendarGeometry.GUTTER+i*column,0f,CalendarGeometry.GUTTER+i*column,CalendarGeometry.top(1440),paint)
  paint.alpha=255; boxes.clear()
  val values=records.map { if(it.id==preview?.id) preview!! else it }
  CalendarGeometry.lanes(values.filter { it.date in dates }).forEach { lane->
   val a=lane.appointment; val day=dates.indexOf(a.date)
   val left=CalendarGeometry.GUTTER+day*column+lane.lane*column/lane.laneCount
   val rect=RectF(left+0.5f,CalendarGeometry.top(a.startMinute),left+(column/lane.laneCount)-0.5f,CalendarGeometry.top(a.endMinute))
   boxes.add(a to rect)
   paint.textSize=12f*fontScale
   val metrics=paint.fontMetrics; val rowHeight=metrics.descent-metrics.ascent+6f
   val presentation=CalendarPresentation.rows(a,rect.height(),rowHeight,week=dates.size>1)
   val labels=presentation.labels
   val centers=presentation.labelCenters(rowHeight)
   val stops=CalendarGeometry.gradient(listOf(a.clientColor)+a.services.map { it.color },centers)
   paint.color=if(a.status==AppointmentStatus.DRAFT) theme.draftFill else theme.appointmentFill
   if(stops.size==1) paint.color=stops[0].color
   if(stops.size>1) {
    val first=stops.first().center; val last=stops.last().center
    paint.shader=LinearGradient(0f,rect.top+first,0f,rect.top+last,stops.map { it.color }.toIntArray(),stops.map { (it.center-first)/(last-first) }.toFloatArray(),Shader.TileMode.CLAMP)
   }
   canvas.drawRoundRect(rect,4f,4f,paint); paint.shader=null
   canvas.save(); canvas.clipRect(rect)
   labels.forEachIndexed { i,label->
    if(i==0 && presentation.compactTime!=null) {
     val half=rowHeight/2
     // Fit the two font-metric frames inside the existing first logical row.
     paint.textSize=12f*fontScale*(half/(metrics.descent-metrics.ascent)).coerceAtMost(1f)
     val compactMetrics=paint.fontMetrics
     listOf(presentation.compactTime,label).forEachIndexed { line,value ->
      val top=rect.top+4f+line*half
      paint.color=theme.surface; canvas.drawRect(rect.left+4f,top,rect.right-2f,top+half,paint)
      paint.color=theme.ink
      val available=(rect.width()-9f).coerceAtLeast(0f)
      var text=value
      if(paint.measureText(text)>available) { while(text.isNotEmpty() && paint.measureText(text+".")>available) text=text.dropLast(1); text+="." }
      canvas.drawText(text,rect.left+5f,top+half/2-(compactMetrics.ascent+compactMetrics.descent)/2,paint)
     }
     paint.textSize=12f*fontScale
     return@forEachIndexed
    }
    val y=rect.top+centers[i]
    if(y-rowHeight/2<rect.bottom) {
     paint.color=theme.surface
     canvas.drawRect(rect.left+4f,y-rowHeight/2,rect.right-2f,y+rowHeight/2,paint)
     paint.color=theme.ink
     val available=(rect.width()-9f).coerceAtLeast(0f)
     var text=label
     if(paint.measureText(text)>available) { while(text.isNotEmpty() && paint.measureText(text+"…")>available) text=text.dropLast(1); text+="…" }
     canvas.drawText(text,rect.left+5f,y-(metrics.ascent+metrics.descent)/2,paint)
    }
   }
   presentation.metadata?.let { metadata-> paint.color=theme.surface; val y=rect.top+4f+rowHeight*labels.size; canvas.drawRect(rect.left+4,y,rect.right-2,y+rowHeight,paint); paint.color=theme.ink; canvas.drawText(metadata,rect.left+5,y-metrics.ascent,paint) }
   canvas.restore()
   if(a.id==selected) { paint.color=theme.accent; paint.style=Paint.Style.STROKE; paint.strokeWidth=2f; canvas.drawRoundRect(rect,4f,4f,paint); paint.style=Paint.Style.FILL }
   paint.color=theme.ink; canvas.drawRect(rect.centerX()-minOf(8f,rect.width()/4),rect.bottom-3f,rect.centerX()+minOf(8f,rect.width()/4),rect.bottom-1f,paint)
  }
  canvas.restore()
 }
 override fun onTouchEvent(event:MotionEvent):Boolean {
  if(!isEnabled) return false
  val x=event.x/density; val y=event.y/density
  when(event.actionMasked) {
   MotionEvent.ACTION_DOWN->{ downX=x; downY=y; moved=false; dragging=null; preview=null; hit=boxes.lastOrNull { it.second.contains(x,y) }?.first
    val box=boxes.lastOrNull { it.first.id==hit?.id }
    possibleHandle=if(box!=null && y>=box.second.bottom-minOf(10f,box.second.height()/3)) hit else null
    // Reserve the potential grip stream from the ScrollView, but do not resize yet.
    if(possibleHandle!=null) parent?.requestDisallowInterceptTouchEvent(true)
    return true
   }
   MotionEvent.ACTION_MOVE->{ moved=moved || abs(x-downX)>touchSlop || abs(y-downY)>touchSlop
    if(dragging==null && possibleHandle!=null && abs(y-downY)>touchSlop) { dragging=possibleHandle; selected=dragging!!.id }
    dragging?.let { preview=CalendarGeometry.resize(it,y/CalendarGeometry.SCALE); previewTime(preview); invalidate() }; return true }
   MotionEvent.ACTION_UP->{ moved=moved || abs(x-downX)>touchSlop || abs(y-downY)>touchSlop
    val candidate=preview; val original=dragging; dragging=null; possibleHandle=null; preview=null; previewTime(null); parent?.requestDisallowInterceptTouchEvent(false); invalidate()
    if(candidate!=null && original!=null && candidate.endMinute!=original.endMinute) resize(candidate)
    else if(!moved && original==null) { performClick(); hit?.let(open) ?: run { if(x>=CalendarGeometry.GUTTER) { val col=((x-CalendarGeometry.GUTTER)/((width/density-CalendarGeometry.GUTTER)/dates.size)).toInt().coerceIn(0,dates.lastIndex); empty(dates[col],y/CalendarGeometry.SCALE) } } }
    return true }
   MotionEvent.ACTION_CANCEL->{ dragging=null; possibleHandle=null; hit=null; preview=null; previewTime(null); parent?.requestDisallowInterceptTouchEvent(false); invalidate(); return true }
  }
  return super.onTouchEvent(event)
 }
 override fun performClick():Boolean { super.performClick(); return true }
 companion object { fun time(minute:Int)="%02d:%02d".format(minute/60,minute%60) }
}
