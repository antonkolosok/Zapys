package ua.zapys.domain

/** Ordinary full Ukrainian client numbers. Canonical output is for comparison only. */
object PhoneRules {
 const val INVALID_MESSAGE = "Вкажіть український номер: 0671234567 або +380671234567"

 fun normalize(raw: String): String? {
  val text=raw.trim(' ', '\u00a0')
  val compact=StringBuilder()
  var parentheses=0
  for((index,char) in text.withIndex()) {
   when(char) {
    in '0'..'9' -> compact.append(char)
    '+' -> if(index==0) compact.append(char) else return null
    ' ', '\u00a0', '-' -> Unit
    '(' -> parentheses++
    ')' -> { parentheses--; if(parentheses<0) return null }
    else -> return null
   }
  }
  if(parentheses!=0) return null
  val number=compact.toString()
  val national=when {
   number.startsWith("+380") && number.length==13 -> number.substring(4)
   number.startsWith("00380") && number.length==14 -> number.substring(5)
   number.startsWith("380") && number.length==12 -> number.substring(3)
   number.startsWith("0") && number.length==10 -> number.substring(1)
   else -> return null
  }
  if(national.first() !in '1'..'9') return null
  return "+380$national"
 }
}
