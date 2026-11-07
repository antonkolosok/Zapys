package ua.zapys.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.*

/** Draft choices affect only the preview until the positive button is pressed. */
class ColorPaletteDialog(context: Context, initial: Int?, draft: Bundle?, confirm: (Int?) -> Unit) : AlertDialog(context,
 if (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) android.R.style.Theme_Material_Dialog_Alert else android.R.style.Theme_Material_Light_Dialog_Alert) {
 private val t = Theme(context)
 private var color = if (draft != null) { if (draft.containsKey("color")) draft.getInt("color") else null } else initial
 private val hsv = FloatArray(3).also { Color.colorToHSV(color ?: PRESETS[0].second, it) }
 private val choices = RadioGroup(context).apply { orientation = RadioGroup.VERTICAL }
 private val preview = TextView(context)
 private val custom = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
 private var updating = false
 private lateinit var hue: SeekBar
 private lateinit var saturation: SeekBar
 private lateinit var value: SeekBar
 private lateinit var area: SaturationValueView
 private val buttons = mutableListOf<Pair<RadioButton, Int?>>()
 private val indicatorTint = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(t.accent, t.ink))
 private lateinit var customButton: RadioButton
 private var customSelected = draft?.getBoolean("custom") ?: (color != null && PRESETS.none { it.second == color })
 init {
  draft?.getFloatArray("hsv")?.let { it.copyInto(hsv) }
  setTitle("Обрати колір")
  val p = t.column()
  preview.textSize = 18f; preview.setTextColor(t.ink); preview.setPadding(t.dp(12), t.dp(8), t.dp(12), t.dp(8))
  preview.contentDescription = "Попередній перегляд кольору"
  p.addView(preview)
  fun choice(label: String, choice: Int?) {
   val b = RadioButton(context).apply {
    id = View.generateViewId(); text = label; minHeight = t.dp(48); setTextColor(t.ink); buttonTintList = indicatorTint
    setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, ColorPickerView.swatch(context, choice), null)
    compoundDrawablePadding = t.dp(12)
    setOnClickListener { color = choice; customSelected = false; choice?.let { Color.colorToHSV(it, hsv) }; refresh() }
   }
   buttons.add(b to choice); choices.addView(b)
  }
  choice("Без кольору", null)
  PRESETS.forEach { choice(it.first, it.second) }
  customButton = RadioButton(context).apply {
   id = View.generateViewId(); text = "Власний колір"; minHeight = t.dp(48); setTextColor(t.ink); buttonTintList = indicatorTint
   setOnClickListener { customSelected = true; color = Color.HSVToColor(hsv); refresh() }
  }
  choices.addView(customButton); p.addView(choices)
  fun slider(label: String, maximum: Int, changed: (Int) -> Unit): SeekBar {
   custom.addView(t.text(label))
   return SeekBar(context).apply {
    max = maximum; minimumHeight = t.dp(48); contentDescription = label
    progressTintList = ColorStateList.valueOf(t.accent); progressBackgroundTintList = ColorStateList.valueOf(t.ink); thumbTintList = ColorStateList.valueOf(t.accent)
    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
     override fun onStartTrackingTouch(bar: SeekBar?) {}
     override fun onStopTrackingTouch(bar: SeekBar?) {}
     override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
      if (!updating) { changed(progress); color = Color.HSVToColor(hsv); refresh() }
     }
    }); custom.addView(this)
   }
  }
  hue = slider("Відтінок", 360) { hsv[0] = it.toFloat() }
  custom.addView(t.text("Насиченість і яскравість"))
  area = SaturationValueView(context, hsv) { s, v -> hsv[1] = s; hsv[2] = v; color = Color.HSVToColor(hsv); refresh() }
  custom.addView(area, LinearLayout.LayoutParams(-1, t.dp(160)))
  saturation = slider("Насиченість", 100) { hsv[1] = it / 100f }
  value = slider("Яскравість", 100) { hsv[2] = it / 100f }
  p.addView(custom)
  // Reserve action height outside the scrolling content. The platform's stacked
  // AlertDialog button panel can clip its second action with expanded HSV/large text.
  val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(t.background) }
  body.addView(t.scroll(p), LinearLayout.LayoutParams(-1, 0, 1f))
  val actions = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(t.dp(12), t.dp(4), t.dp(12), t.dp(8)) }
  fun action(label: String, click: () -> Unit) {
   actions.addView(Button(this@ColorPaletteDialog.context).apply {
    text = label; isAllCaps = false; minHeight = t.dp(48)
    setOnClickListener { click(); dismiss() }
   }, LinearLayout.LayoutParams(-1, -2))
  }
  action("Підтвердити") { confirm(color) }
  action("Скасувати") {}
  body.addView(actions, LinearLayout.LayoutParams(-1, -2))
  setView(body)
  refresh()
 }
 private fun refresh() {
  updating = true
  try {
   buttons.forEach { (b, c) -> b.isChecked = !customSelected && color == c; b.isSelected = b.isChecked }
   customButton.isChecked = customSelected; customButton.isSelected = customSelected
   custom.visibility = if (customSelected) View.VISIBLE else View.GONE
   hue.progress = hsv[0].toInt(); saturation.progress = (hsv[1] * 100).toInt(); value.progress = (hsv[2] * 100).toInt()
   area.updateColor()
   preview.text = if (customSelected) "Власний колір" else name(color)
   preview.setCompoundDrawablesRelativeWithIntrinsicBounds(ColorPickerView.swatch(context, color), null, null, null)
   preview.compoundDrawablePadding = t.dp(12)
  } finally { updating = false }
 }
 fun draftState() = Bundle().apply {
  color?.let { putInt("color", it) }; putFloatArray("hsv", hsv.copyOf()); putBoolean("custom", customSelected)
 }
 companion object {
  val PRESETS = listOf("Зелений" to Color.rgb(121,173,133), "Блакитний" to Color.rgb(145,183,221), "Золотистий" to Color.rgb(227,178,115), "Бузковий" to Color.rgb(198,151,195))
  fun name(color: Int?): String = if (color == null) "Без кольору" else PRESETS.firstOrNull { it.second == color }?.first ?: "Власний колір"
 }
}

/** Touch plane; equivalent named native sliders above/below provide keyboard and TalkBack input. */
private class SaturationValueView @JvmOverloads constructor(context: Context, private val hsv: FloatArray = floatArrayOf(0f,1f,1f), private val change: (Float, Float) -> Unit = { _,_-> }) : View(context) {
 private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
 private var hueShader: Shader? = null
 private var valueShader: Shader? = null
 private var drawnHue = Float.NaN
 init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
 fun updateColor() {
  if (width > 0 && height > 0 && (drawnHue != hsv[0] || hueShader == null)) {
   hueShader = LinearGradient(0f,0f,width.toFloat(),0f,Color.WHITE,Color.HSVToColor(floatArrayOf(hsv[0],1f,1f)),Shader.TileMode.CLAMP)
   drawnHue = hsv[0]
  }
  invalidate()
 }
 override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
  super.onSizeChanged(w,h,oldw,oldh)
  hueShader = null
  valueShader = if(h>0) LinearGradient(0f,0f,0f,h.toFloat(),Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP) else null
  updateColor()
 }
 override fun onDraw(canvas: Canvas) {
  super.onDraw(canvas)
  paint.shader = hueShader
  canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint)
  paint.shader = valueShader
  canvas.drawRect(0f,0f,width.toFloat(),height.toFloat(),paint); paint.shader = null
  paint.style = Paint.Style.STROKE; paint.strokeWidth = 5f; paint.color = Color.BLACK
  val x = hsv[1]*width; val y = (1-hsv[2])*height
  canvas.drawCircle(x,y,10f,paint); paint.strokeWidth=2f; paint.color=Color.WHITE; canvas.drawCircle(x,y,10f,paint); paint.style=Paint.Style.FILL
 }
 override fun onTouchEvent(event: MotionEvent): Boolean {
  if (event.actionMasked !in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL)) return super.onTouchEvent(event)
  if (event.actionMasked == MotionEvent.ACTION_CANCEL) { parent.requestDisallowInterceptTouchEvent(false); return true }
  parent.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP)
  change((event.x/width.coerceAtLeast(1)).coerceIn(0f,1f), (1-event.y/height.coerceAtLeast(1)).coerceIn(0f,1f))
  if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
  return true
 }
 override fun performClick(): Boolean { super.performClick(); return true }
}
