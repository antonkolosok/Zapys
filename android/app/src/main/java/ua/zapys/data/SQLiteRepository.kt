package ua.zapys.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import ua.zapys.domain.*
import java.io.Closeable
import java.time.LocalDate
import java.time.ZoneId

/** Device-local SQLite store. Mutations and outbox replacement commit together. */
class SQLiteRepository(context: Context, databaseName: String = "zapys.db", private val now: () -> Long = { System.currentTimeMillis() }, private val zone: ZoneId? = null) : Repository, Closeable {
    private val helper = object : SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
        override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE clients (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE services (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE appointments (id TEXT PRIMARY KEY NOT NULL, client_id TEXT REFERENCES clients(id) ON DELETE RESTRICT, payload TEXT NOT NULL)")
            db.execSQL("CREATE INDEX appointments_client ON appointments(client_id)")
            db.execSQL("CREATE TABLE appointment_services (appointment_id TEXT NOT NULL REFERENCES appointments(id) ON DELETE CASCADE, position INTEGER NOT NULL CHECK(position BETWEEN 0 AND 4), payload TEXT NOT NULL, PRIMARY KEY(appointment_id, position))")
            db.execSQL("CREATE TABLE reminders (id TEXT PRIMARY KEY NOT NULL, appointment_id TEXT NOT NULL UNIQUE REFERENCES appointments(id) ON DELETE CASCADE, payload TEXT NOT NULL)")
            db.execSQL("CREATE TABLE settings (id TEXT PRIMARY KEY NOT NULL, payload TEXT NOT NULL)")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Непідтримувана версія бази: $oldVersion → $newVersion") }
    }
    private val db get() = helper.writableDatabase
    private inline fun <T> transaction(action: () -> T): T {
        val database = db; database.beginTransaction()
        try { val result = action(); database.setTransactionSuccessful(); return result } finally { database.endTransaction() }
    }
    private fun rows(table: String, where: String? = null, args: Array<String>? = null, order: String = "rowid"): List<JSONObject> =
        db.query(table, arrayOf("payload"), where, args, null, null, order).use { cursor -> buildList { while (cursor.moveToNext()) add(JSONObject(cursor.getString(0))) } }
    private fun put(table: String, id: String, payload: JSONObject, extra: ContentValues = ContentValues()) {
        extra.put("payload", payload.toString())
        if (db.update(table, extra, "id=?", arrayOf(id)) == 0) { extra.put("id", id); db.insertOrThrow(table, null, extra) }
    }
    override fun clients() = rows("clients").map(::readClient)
    override fun services() = rows("services").map { readService(it) }
    override fun appointments(): List<Appointment> = rows("appointments").map { j ->
        Appointment(j.getString("id"), LocalDate.parse(j.getString("date")), j.getInt("start"), j.getInt("end"), j.stringOrNull("clientId"), j.getString("clientName"), j.getString("clientPhone"), j.intOrNull("clientColor"),
            rows("appointment_services", "appointment_id=?", arrayOf(j.getString("id")), "position").map { readService(it).snapshot() }, j.getString("note"), AppointmentStatus.valueOf(j.getString("status")), j.getBoolean("paid"), j.longOrNull("received"), j.getBoolean("manualEnd"))
    }.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.startMinute }.thenBy { it.id })
    private fun storedReminders() = rows("reminders").map { j -> Reminder(j.getString("id"), j.getString("appointmentId"), j.getLong("scheduledAt"), j.getString("text"), Channel.valueOf(j.getString("channel")), j.getString("recipient"), ReminderState.valueOf(j.getString("state"))) }
    private fun eligible(reminder: Reminder, currentAppointments: List<Appointment>, currentClients: List<Client>, currentTime: Long): Boolean {
        val appointment = currentAppointments.firstOrNull { it.id == reminder.appointmentId } ?: return false
        val client = currentClients.firstOrNull { it.id == appointment.clientId }
        val expected = Rules.reminder(appointment, client, currentTime, zone ?: ZoneId.systemDefault()) ?: return false
        return reminder.scheduledAt == expected.scheduledAt && reminder.text == expected.text && reminder.channel == expected.channel && reminder.recipient == expected.recipient
    }
    override fun reminders(): List<Reminder> = transaction {
        reconcileTimeZone()
        val currentAppointments = appointments(); val currentClients = clients(); val currentTime = now()
        storedReminders().filter { eligible(it, currentAppointments, currentClients, currentTime) }.map { previous ->
            DeviceRules.reconcile(previous,currentTime).also { if(it!=previous) put("reminders",it.id,reminderJson(it)) }
        }
    }
    override fun saveClient(client: Client) = transaction {
        reconcileTimeZone()
        Rules.validateClient(client)
        if (clients().none { it.id == client.id } && clients().size >= Limits.MAX_CLIENTS) throw ValidationException("Досягнуто технічної межі 1000 клієнтів")
        put("clients", client.id, clientJson(client))
        appointments().filter { it.clientId == client.id }.forEach { a ->
            replaceReminder(a, client)
        }
    }
    override fun deleteClient(id: String) = transaction { db.delete("clients", "id=?", arrayOf(id)); Unit }
    override fun saveService(service: Service) = transaction { Rules.validateService(service); put("services", service.id, serviceJson(service)); Unit }
    override fun deleteService(id: String) = transaction { db.delete("services", "id=?", arrayOf(id)); Unit }
    override fun saveAppointment(appointment: Appointment, allowOverlap: Boolean): Appointment = transaction {
        reconcileTimeZone()
        val clients = clients(); Rules.validateAppointment(appointment, clients)
        val all = appointments()
        if (all.none { it.id == appointment.id } && all.size >= Limits.MAX_APPOINTMENTS) throw ValidationException("Досягнуто технічної межі 10000 записів")
        val conflicts = Rules.conflicts(appointment, all)
        if (!allowOverlap && conflicts.isNotEmpty()) throw ConflictException(conflicts)
        val client = clients.firstOrNull { it.id == appointment.clientId }
        val saved = appointment.copy(clientName = client?.displayName ?: "", clientPhone = client?.phone ?: "", clientColor = client?.color, services = appointment.services.toList())
        val j = JSONObject().apply {
            put("id", saved.id); put("date", saved.date.toString()); put("start", saved.startMinute); put("end", saved.endMinute)
            nullable("clientId", saved.clientId); put("clientName", saved.clientName); put("clientPhone", saved.clientPhone); nullable("clientColor", saved.clientColor)
            put("note", saved.note); put("status", saved.status.name); put("paid", saved.paid); nullable("received", saved.receivedMinor); put("manualEnd", saved.manualEnd)
        }
        put("appointments", saved.id, j, ContentValues().apply { put("client_id", saved.clientId) })
        db.delete("appointment_services", "appointment_id=?", arrayOf(saved.id))
        saved.services.forEachIndexed { index, s -> db.insertOrThrow("appointment_services", null, ContentValues().apply {
            put("appointment_id", saved.id); put("position", index); put("payload", serviceJson(Service(s.serviceId, s.name, s.durationMinutes, s.priceMinor, s.note, s.color)).toString())
        }) }
        replaceReminder(saved, client)
        saved
    }
    private fun replaceReminder(a: Appointment, c: Client?) {
        val existing = storedReminders().firstOrNull { it.appointmentId == a.id }
        val next = Rules.reminder(a, c, now(), zone ?: ZoneId.systemDefault())
        if (existing != null && next != null && existing.scheduledAt == next.scheduledAt && existing.text == next.text && existing.channel == next.channel && existing.recipient == next.recipient) return
        db.delete("reminders", "appointment_id=?", arrayOf(a.id))
        next?.let { r -> put("reminders", r.id, reminderJson(r), ContentValues().apply { put("appointment_id", a.id) }) }
    }
    override fun deleteAppointment(id: String) = transaction { db.delete("appointments", "id=?", arrayOf(id)); Unit }
    /** Rebase pending wall-clock reminders on an OS timezone change, preserving terminal outcomes. */
    fun reconcileTimeZone() = transaction {
        val currentZone=zone ?: ZoneId.systemDefault()
        if(rows("settings","id=?",arrayOf("reminderZone")).firstOrNull()?.optString("zone")==currentZone.id) return@transaction
        val stored=storedReminders().associateBy { it.appointmentId }
        val currentClients=clients()
        appointments().forEach { a ->
            val previous=stored[a.id] ?: return@forEach
            val client=currentClients.firstOrNull { it.id==a.clientId }
            val expected=Rules.reminder(a,client,now(),currentZone)
            if(previous.state in setOf(ReminderState.HANDED_OFF,ReminderState.CANCELLED)) {
                if(expected!=null) put("reminders",previous.id,reminderJson(DeviceRules.rebaseTerminal(previous,expected.scheduledAt)))
            } else replaceReminder(a,client)
        }
        put("settings","reminderZone",JSONObject().apply { put("zone",currentZone.id) })
    }
    override fun setReminderState(id: String, state: ReminderState) = transaction {
        reconcileTimeZone()
        val reminder = storedReminders().firstOrNull { it.id == id }
        if (reminder != null && !eligible(reminder, appointments(), clients(), now())) {
            db.delete("reminders", "id=?", arrayOf(id))
            return@transaction
        }
        if (reminder != null && reminder.state != ReminderState.CANCELLED && reminder.state != ReminderState.HANDED_OFF) {
            require(state != ReminderState.SCHEDULED) { "Не можна відновити застаріле нагадування" }
            put("reminders", id, reminderJson(reminder.copy(state = state)))
        }
    }
    override fun profile(): LocalProfile = rows("settings", "id=?", arrayOf("profile")).firstOrNull()?.let { LocalProfile(it.getString("phone"), it.getString("email"), it.getString("name"),it.optBoolean("notificationsEnabled",false)) } ?: LocalProfile()
    override fun saveProfile(profile: LocalProfile) = transaction {
        Rules.note(profile.phone); Rules.note(profile.email); Rules.note(profile.name)
        put("settings", "profile", JSONObject().apply { put("phone", profile.phone); put("email", profile.email); put("name", profile.name); put("notificationsEnabled",profile.notificationsEnabled) }); Unit
    }
    override fun close() { helper.close() }
    private fun clientJson(c: Client) = JSONObject().apply {
        put("id", c.id); put("firstName", c.firstName); put("phone", c.phone); put("surname", c.surname); put("patronymic", c.patronymic); put("note", c.note); nullable("color", c.color)
        put("reminder", JSONObject().apply { put("enabled", c.reminder.enabled); put("channel", c.reminder.channel.name); put("recipient", c.reminder.recipient); put("lead", c.reminder.leadMinutes); put("template", c.reminder.template) })
    }
    private fun readClient(j: JSONObject): Client {
        val r = j.getJSONObject("reminder")
        return Client(j.getString("id"), j.getString("firstName"), j.getString("phone"), j.getString("surname"), j.getString("patronymic"), j.getString("note"), j.intOrNull("color"), ReminderPreferences(r.getBoolean("enabled"), Channel.valueOf(r.getString("channel")), r.getString("recipient"), r.getInt("lead"), r.getString("template")))
    }
    private fun serviceJson(s: Service) = JSONObject().apply { put("id", s.id); put("name", s.name); nullable("duration", s.durationMinutes); nullable("price", s.priceMinor); put("note", s.note); nullable("color", s.color) }
    private fun readService(j: JSONObject) = Service(j.getString("id"), j.getString("name"), j.intOrNull("duration"), j.longOrNull("price"), j.getString("note"), j.intOrNull("color"))
    private fun reminderJson(r: Reminder) = JSONObject().apply { put("id", r.id); put("appointmentId", r.appointmentId); put("scheduledAt", r.scheduledAt); put("text", r.text); put("channel", r.channel.name); put("recipient", r.recipient); put("state", r.state.name) }
    private fun JSONObject.nullable(key: String, value: Any?) { put(key, value ?: JSONObject.NULL) }
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key)) null else getString(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
}
