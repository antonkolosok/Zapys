package ua.zapys.device
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import ua.zapys.MainActivity
import ua.zapys.domain.*

class ReminderScheduler(private val context: Context) {
 companion object { const val CHANNEL="manual_reminders"; private val lock=Any() }
 private val alarms=context.getSystemService(AlarmManager::class.java)
 private val notifications=context.getSystemService(NotificationManager::class.java)
 private val ledger=context.getSharedPreferences("reminder_schedule",Context.MODE_PRIVATE)
 fun allowed(): Boolean {
  notifications.createNotificationChannel(NotificationChannel(CHANNEL,"Нагадування Zapys",NotificationManager.IMPORTANCE_DEFAULT))
  return (Build.VERSION.SDK_INT<33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && notifications.areNotificationsEnabled() && notifications.getNotificationChannel(CHANNEL).importance!=NotificationManager.IMPORTANCE_NONE
 }
 private fun pending(id:String)=PendingIntent.getBroadcast(context,0,Intent(context,ReminderReceiver::class.java).apply { action="ua.zapys.REMINDER"; data=Uri.parse("zapys://reminder/$id") },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 /** Call off UI thread after writes/foreground/clock changes. Inexact RTC alarms may be delayed. */
 fun reconcile(repository: Repository, boot:Boolean=false)=synchronized(lock) {
  val zone=java.time.ZoneId.systemDefault().id
  if(ledger.getString("zone",null)!=zone && repository is ua.zapys.data.SQLiteRepository) repository.reconcileTimeZone()
  ledger.edit().putString("zone",zone).commit()
  val rows=repository.reminders(); val enabled=repository.profile().notificationsEnabled && allowed()
  val now=System.currentTimeMillis()
  val scheduled=DeviceRules.futureAlarmIds(rows,enabled,now)
  val due=DeviceRules.dueIds(rows,enabled,now)
  val appointments=repository.appointments().associateBy { it.id }
  val expiry=rows.filter { it.id in due }.mapNotNull { row -> appointments[row.appointmentId]?.let { a -> row.id to a.date.atStartOfDay().plusMinutes(a.startMinute.toLong()).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() } }.toMap()
  val desired=scheduled+expiry.keys
  val old=ledger.getStringSet("ids",emptySet())!!.toSet()
  DeviceRules.staleIds(old,desired).forEach { alarms.cancel(pending(it)) }
  val shown=DeviceRules.displayedLedger(ledger.getStringSet("shown",emptySet())!!.toSet(),boot)
  (shown-due).forEach { notifications.cancel(it,0) }
  // Retain IDs before scheduling, so a process interruption still permits future cancellation.
  val successfullyShown=(shown intersect due).toMutableSet()
  ledger.edit().putStringSet("ids",desired).putStringSet("shown",successfullyShown).commit()
  rows.filter { it.id in scheduled }.forEach { alarms.set(AlarmManager.RTC_WAKEUP,it.scheduledAt,pending(it.id)) }
  expiry.forEach { (id,at)-> alarms.set(AlarmManager.RTC_WAKEUP,at,pending(id)) }
  (due-shown).forEach { id ->
   val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java).apply { putExtra("openReminders",true) },PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
   try { notifications.notify(id,0,Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Zapys").setContentText("Час підготувати нагадування в Zapys").setVisibility(Notification.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).build()); successfullyShown.add(id); ledger.edit().putStringSet("shown",successfullyShown.toSet()).commit() }
   catch(_: RuntimeException) { /* Keep this ID retryable on the next reconciliation. */ }
  }
 }
}
