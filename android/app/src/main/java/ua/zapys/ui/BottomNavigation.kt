package ua.zapys.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.LinearLayout

/** Equal navigation targets grow together when a full destination label wraps. */
object BottomNavigation {
 fun create(context:Context,theme:Theme,route:String,onNavigate:(String)->Unit)=LinearLayout(context).apply {
  orientation=LinearLayout.HORIZONTAL
  isBaselineAligned=false
  gravity=Gravity.CENTER_VERTICAL
  listOf("calendar" to "Календар", "clients" to "Клієнти", "services" to "Послуги", "settings" to "Налаштування").forEach { (key,label)->
   val selected=route==key || (key=="clients" && route=="clientForm") || (key=="services" && route=="serviceForm")
   val button=theme.button(label) { onNavigate(key) }.apply {
    isSelected=selected
    contentDescription=label+if(selected) ", обрано" else ""
    gravity=Gravity.CENTER
    // Keep the platform's scalable text size; reserve more of each narrow cell for its full label.
    setPadding(theme.dp(4),theme.dp(8),theme.dp(4),theme.dp(8))
    setTextColor(if(selected) theme.onAccent else theme.ink)
    val fill=GradientDrawable().apply {
     setColor(if(selected) theme.accent else theme.surface)
     cornerRadius=theme.dp(10).toFloat()
     setStroke(theme.dp(1),theme.accent)
    }
    background=RippleDrawable(ColorStateList.valueOf((theme.accent and 0x00ffffff) or 0x33000000),fill,null)
   }
   // The row first measures its tallest wrapped label, then gives that height to every item.
   addView(button,LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,1f))
  }
 }
}
