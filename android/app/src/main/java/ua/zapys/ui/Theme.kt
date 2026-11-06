package ua.zapys.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*

class Theme(val context: Context) {
 private val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
 val background = Color.parseColor(if (dark) "#141B17" else "#F5F6F2")
 val surface = Color.parseColor(if (dark) "#1C2520" else "#FFFEFA")
 val ink = Color.parseColor(if (dark) "#E8F1E9" else "#233A2C")
 val accent = Color.parseColor(if (dark) "#B3DBC0" else "#2F6047")
 val appointmentFill = Color.parseColor(if (dark) "#2A3D30" else "#E8F1E5")
 val draftFill = Color.parseColor(if (dark) "#443A22" else "#FBEFCF")
 val onAccent = Color.parseColor(if (dark) "#172D20" else "#FFFEFA")
 fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
 fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(20)); setBackgroundColor(this@Theme.background) }
 fun text(value: String, size: Float = 16f) = TextView(context).apply { text = value; textSize = size; setTextColor(ink); setPadding(0, dp(8), 0, dp(8)); setTextIsSelectable(true) }
 fun button(value: String, action: () -> Unit) = Button(context).apply { text = value; isAllCaps = false; minHeight = dp(48); setOnClickListener { action() } }
 fun field(parent: LinearLayout, label: String, value: String = "", type: Int = android.text.InputType.TYPE_CLASS_TEXT): EditText {
  parent.addView(text(label))
  return EditText(context).apply {
   setText(value); inputType = type; setTextColor(ink); setHintTextColor(ink); minHeight = dp(48)
   background = GradientDrawable().apply { setColor(surface); cornerRadius = dp(10).toFloat(); setStroke(dp(1), accent) }
   setPadding(dp(12), dp(8), dp(12), dp(8)); contentDescription = label
   parent.addView(this, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
  }
 }
 fun scroll(content: View) = ScrollView(context).apply { isFillViewport = true; addView(content) }
 fun colors(parent: LinearLayout, initial: Int?, onChange: (Int?) -> Unit): () -> Int? {
  val values = listOf(null, Color.rgb(121,173,133), Color.rgb(145,183,221), Color.rgb(227,178,115), Color.rgb(198,151,195))
  var selected = initial
  val options = if (initial != null && initial !in values) values + initial else values
  parent.addView(text("Колір"))
  val picker = Spinner(context)
  picker.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options.map { when(it) { null -> "Без кольору"; else -> "#%06X".format(it and 0xFFFFFF) } })
  picker.setSelection(options.indexOf(initial)); picker.minimumHeight = dp(48); picker.contentDescription="Колір, можна прибрати"
  picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
   override fun onNothingSelected(p: AdapterView<*>?) {}
   override fun onItemSelected(p: AdapterView<*>?, v: View?, position: Int, id: Long) { val next=options[position]; if(next != selected) { selected=next; onChange(next) } }
  }
  parent.addView(picker); return { selected }
 }
}
fun EditText.onChange(action: () -> Unit) { addTextChangedListener(object : TextWatcher {
 override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
 override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { action() }
 override fun afterTextChanged(s: Editable?) {}
}) }
