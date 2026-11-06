package ua.zapys.device
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ua.zapys.data.SQLiteRepository
import java.util.concurrent.Executors
class ReminderReceiver: BroadcastReceiver() {
 companion object { private val io=Executors.newSingleThreadExecutor() }
 override fun onReceive(context:Context,intent:Intent) {
  val pending=goAsync()
  io.execute { try { runCatching { SQLiteRepository(context).use { ReminderScheduler(context).reconcile(it,boot=intent.action==Intent.ACTION_BOOT_COMPLETED) } } } finally { pending.finish() } }
 }
}
