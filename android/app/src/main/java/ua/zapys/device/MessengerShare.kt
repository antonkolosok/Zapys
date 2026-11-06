package ua.zapys.device
import android.content.Intent
import ua.zapys.domain.Reminder
import ua.zapys.domain.Channel
object MessengerShare {
 // Direct handoff to the selected app; recipient selection stays manual.
 fun intent(reminder: Reminder)=Intent(Intent.ACTION_SEND).apply {
  type="text/plain"; putExtra(Intent.EXTRA_TEXT,reminder.text)
  setPackage(when(reminder.channel) {
   Channel.VIBER->"com.viber.voip"; Channel.WHATSAPP->"com.whatsapp"
   Channel.TELEGRAM->"org.telegram.messenger"; Channel.INSTAGRAM->"com.instagram.android"
  })
 }
}
