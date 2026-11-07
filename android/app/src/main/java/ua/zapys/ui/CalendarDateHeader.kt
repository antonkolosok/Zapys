package ua.zapys.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import ua.zapys.domain.CalendarGeometry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Independent date selection and week-column labels have different horizontal geometry. */
class CalendarDateHeader(context:Context,theme:Theme,date:LocalDate,mode:String,onDaySelected:(LocalDate)->Unit) {
 private val days=CalendarGeometry.weekDates(date)
 private val today=LocalDate.now()
 private val format=DateTimeFormatter.ofPattern("EE\nd",Locale.forLanguageTag("uk"))

 val selector=LinearLayout(context).apply {
  isBaselineAligned=false
  days.forEach { day->
   addView(theme.button(day.format(format)) { onDaySelected(day) }.apply {
    isSelected=day==date
    if(isSelected) {
     setBackgroundColor(theme.accent); setTextColor(theme.onAccent)
    } else if(day==today) {
     background=GradientDrawable().apply { setColor(theme.surface); setStroke(theme.dp(2),theme.accent) }
     setTextColor(theme.ink)
    }
    contentDescription=day.toString()+(if(isSelected) ", обрано" else "")+(if(day==today) ", сьогодні" else "")
    textSize=11f; setPadding(0,0,0,0)
   },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f))
  }
 }

 /** Compose immediately above CalendarView; only this row reserves the time-label gutter. */
 val gridHeadings:LinearLayout?=if(mode=="week") object:LinearLayout(context) {
  override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
   val available=MeasureSpec.getSize(widthMeasureSpec)
   val gutter=CalendarGeometry.GUTTER*resources.displayMetrics.density
   // Round each shared grid boundary, rather than accumulating weighted-cell truncation.
   getChildAt(0).layoutParams.width=gutter.roundToInt()
   for(index in 0..6) {
    val left=(gutter+index*(available-gutter)/7f).roundToInt()
    val right=(gutter+(index+1)*(available-gutter)/7f).roundToInt()
    getChildAt(index+1).layoutParams.width=right-left
   }
   super.onMeasure(widthMeasureSpec,heightMeasureSpec)
  }
 }.apply {
  isBaselineAligned=false
  addView(View(context),LinearLayout.LayoutParams(theme.dp(CalendarGeometry.GUTTER.toInt()),1))
  days.forEach { day->
   addView(TextView(context).apply {
    text=day.format(format); textSize=11f; gravity=Gravity.CENTER; setTextColor(theme.ink)
    setPadding(0,theme.dp(4),0,theme.dp(4))
    contentDescription=day.toString()+", колонка календаря"
   },LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT))
  }
 } else null
}
