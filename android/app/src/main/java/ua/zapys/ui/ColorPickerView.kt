package ua.zapys.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Parcelable
import android.widget.Button

/** One identified view owns the committed selection and the open dialog's draft. */
class ColorPickerView @JvmOverloads constructor(context: Context, initial: Int? = null, private val onChange: (Int?) -> Unit = {}) : Button(context) {
 var selectedColor: Int? = initial
  private set
 private var dialog: ColorPaletteDialog? = null
 private var restoredDraft: Bundle? = null
 private var reopen = false
 init {
  isAllCaps = false
  minHeight = Theme(context).dp(48)
  val t = Theme(context)
  setTextColor(t.ink)
  background = GradientDrawable().apply { setColor(t.surface); cornerRadius = t.dp(10).toFloat(); setStroke(t.dp(1), t.accent) }
  setPadding(t.dp(12), t.dp(8), t.dp(12), t.dp(8))
  setOnClickListener { openPalette() }
  render()
 }
 private fun render() {
  val name = ColorPaletteDialog.name(selectedColor)
  text = name
  contentDescription = "Колір: $name. Обрати колір"
  setCompoundDrawablesRelativeWithIntrinsicBounds(swatch(context, selectedColor), null, null, null)
  compoundDrawablePadding = Theme(context).dp(12)
 }
 private fun openPalette() {
  if (dialog != null || !isAttachedToWindow) return
  reopen = false
  val next = ColorPaletteDialog(context, selectedColor, restoredDraft) { color ->
   selectedColor = color
   render()
   onChange(color)
  }
  restoredDraft = null
  dialog = next
  next.setOnDismissListener { dialog = null }
  next.show()
 }
 override fun onSaveInstanceState(): Parcelable = Bundle().apply {
  putParcelable("super", super.onSaveInstanceState())
  selectedColor?.let { putInt("color", it) }
  putBoolean("open", dialog?.isShowing == true || reopen)
  putBundle("draft", dialog?.draftState() ?: restoredDraft)
 }
 override fun onRestoreInstanceState(state: Parcelable?) {
  if (state !is Bundle) { super.onRestoreInstanceState(state); return }
  super.onRestoreInstanceState(state.getParcelable("super"))
  selectedColor = if (state.containsKey("color")) state.getInt("color") else null
  restoredDraft = state.getBundle("draft")
  reopen = state.getBoolean("open")
  render()
  if (reopen && isAttachedToWindow) post { if (reopen) openPalette() }
 }
 override fun onAttachedToWindow() {
  super.onAttachedToWindow()
  if (reopen) post { if (reopen) openPalette() }
 }
 override fun onDetachedFromWindow() {
  dialog?.dismiss()
  dialog = null
  super.onDetachedFromWindow()
 }
 companion object {
  internal fun swatch(context: Context, color: Int?) = GradientDrawable().apply {
   val t = Theme(context)
   shape = GradientDrawable.OVAL
   setSize(t.dp(28), t.dp(28))
   setColor(color ?: t.surface)
   setStroke(t.dp(2), t.ink)
   // Null is also named in text, rather than relying on the empty swatch alone.
  }
 }
}
