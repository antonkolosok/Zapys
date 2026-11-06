package ua.zapys.domain

object DeviceRules {
 fun actionableAt(id:String,rows:List<Reminder>,now:Long)=actionable(id,rows)?.takeIf { it.scheduledAt<=now }
 fun futureAlarmIds(rows:List<Reminder>,enabled:Boolean,now:Long)=rows.filter { enabled && it.scheduledAt>now && it.state in setOf(ReminderState.SCHEDULED,ReminderState.NEEDS_ACTION,ReminderState.ERROR) }.map { it.id }.toSet()
 fun dueIds(rows:List<Reminder>,enabled:Boolean,now:Long)=rows.filter { enabled && actionableAt(it.id,listOf(it),now)!=null }.map { it.id }.toSet()
 fun displayedLedger(shown:Set<String>,boot:Boolean)=if(boot) emptySet() else shown
 fun rebaseTerminal(row:Reminder,scheduledAt:Long)=if(row.state in setOf(ReminderState.HANDED_OFF,ReminderState.CANCELLED)) row.copy(scheduledAt=scheduledAt) else row
 fun canLaunch(resumed:Boolean,requestedEpoch:Int,currentEpoch:Int)=resumed && requestedEpoch==currentEpoch
 fun reconcile(reminder: Reminder, now: Long): Reminder = if(reminder.state==ReminderState.SCHEDULED && reminder.scheduledAt<=now) reminder.copy(state=ReminderState.NEEDS_ACTION) else reminder
 fun actionable(id: String, rows: List<Reminder>): Reminder? = rows.firstOrNull { it.id==id && it.recipient.isNotBlank() && it.state in setOf(ReminderState.NEEDS_ACTION,ReminderState.ERROR) }
 fun alarmIds(rows: List<Reminder>, notificationsEnabled: Boolean): Set<String> = if(notificationsEnabled) rows.filter { it.state==ReminderState.SCHEDULED }.map { it.id }.toSet() else emptySet()
 fun staleIds(retained: Set<String>, desired: Set<String>): Set<String> = retained-desired
}
