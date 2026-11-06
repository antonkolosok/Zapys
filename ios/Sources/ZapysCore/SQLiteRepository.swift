import Foundation
import CSQLite

/// Call from one serial executor. SQLite owns durable records; Codable preserves
/// UUIDs, ordered snapshots and optional integer money without coercion.
public final class SQLiteRepository {
    private var db: OpaquePointer?
    private let timeZone: () -> TimeZone
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    // Internal count observer for deterministic scaling regressions; no timing claims.
    var didDecodeRecord: ((String) -> Void)?
    private let transient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    public init(path: String, timeZone: @escaping () -> TimeZone = { .current }) throws {
        self.timeZone = timeZone
        guard sqlite3_open(path, &db) == SQLITE_OK else {
            let message = db.map { String(cString: sqlite3_errmsg($0)) } ?? "SQLite open failed"
            if let db { sqlite3_close(db) }; db = nil
            throw ZapysError.database(message)
        }
        do {
            try execute("PRAGMA foreign_keys = ON")
            let version = try statement("PRAGMA user_version") { s -> Int32 in
                guard sqlite3_step(s) == SQLITE_ROW else { throw failure() }
                return sqlite3_column_int(s, 0)
            }
            guard version <= 1 else { throw ZapysError.database("Непідтримувана версія бази даних") }
            if version == 0 {
                try transaction {
                    for table in ["clients", "services", "appointments", "reminders", "settings"] {
                        try execute("CREATE TABLE \(table) (id TEXT PRIMARY KEY NOT NULL, payload BLOB NOT NULL)")
                    }
                    try execute("PRAGMA user_version = 1")
                }
            }
            try execute("CREATE TABLE IF NOT EXISTS metadata (id TEXT PRIMARY KEY NOT NULL, payload BLOB NOT NULL)")
        } catch { if let db { sqlite3_close(db) }; db = nil; throw error }
    }
    deinit { if let db { sqlite3_close(db) } }
    private func failure() -> ZapysError { .database(db.map { String(cString: sqlite3_errmsg($0)) } ?? "SQLite closed") }
    private func statement<T>(_ sql: String, _ body: (OpaquePointer) throws -> T) throws -> T {
        var prepared: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &prepared, nil) == SQLITE_OK, let prepared else { throw failure() }
        defer { sqlite3_finalize(prepared) }
        return try body(prepared)
    }
    private func execute(_ sql: String) throws {
        try statement(sql) { guard sqlite3_step($0) == SQLITE_DONE else { throw failure() } }
    }
    private func transaction<T>(_ body: () throws -> T) throws -> T {
        try execute("BEGIN IMMEDIATE")
        do { let value = try body(); try execute("COMMIT"); return value }
        catch { try? execute("ROLLBACK"); throw error }
    }
    private func bind(_ value: String, to s: OpaquePointer, at index: Int32) throws {
        guard sqlite3_bind_text(s, index, value, -1, transient) == SQLITE_OK else { throw failure() }
    }
    private func records<T: Decodable>(_ table: String, as type: T.Type) throws -> [T] {
        try statement("SELECT payload FROM \(table) ORDER BY rowid") { s in
            var result: [T] = []
            while true {
                let code = sqlite3_step(s)
                if code == SQLITE_DONE { return result }
                guard code == SQLITE_ROW, let pointer = sqlite3_column_blob(s, 0) else { throw failure() }
                let data = Data(bytes: pointer, count: Int(sqlite3_column_bytes(s, 0)))
                result.append(try decoder.decode(type, from: data))
                didDecodeRecord?(table)
            }
        }
    }
    private func put<T: Encodable>(_ value: T, id: String, table: String) throws {
        let data = try encoder.encode(value)
        try statement("INSERT INTO \(table)(id,payload) VALUES(?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload") { s in
            try bind(id, to: s, at: 1)
            let bound = data.withUnsafeBytes { sqlite3_bind_blob(s, 2, $0.baseAddress, Int32($0.count), transient) }
            guard bound == SQLITE_OK, sqlite3_step(s) == SQLITE_DONE else { throw failure() }
        }
    }
    private func remove(_ id: UUID, table: String) throws {
        try statement("DELETE FROM \(table) WHERE id=?") { s in
            try bind(id.uuidString, to: s, at: 1)
            guard sqlite3_step(s) == SQLITE_DONE else { throw failure() }
        }
    }
    public func clients() throws -> [Client] { try records("clients", as: Client.self) }
    public func services() throws -> [Service] { try records("services", as: Service.self) }
    public func appointments() throws -> [Appointment] { try records("appointments", as: Appointment.self) }
    public func profile() throws -> LocalProfile { try records("settings", as: LocalProfile.self).first ?? LocalProfile() }
    public func saveProfile(_ profile: LocalProfile) throws {
        try Rules.note(profile.name); try Rules.note(profile.phone); try Rules.note(profile.email)
        try transaction { try put(profile, id: "local", table: "settings") }
    }
    public func saveClient(_ client: Client, now: Date = Date()) throws {
        try Rules.validate(client)
        try transaction {
            var snapshot = try reconciledSnapshot()
            let all = Array(snapshot.clients.values)
            let restoring = all.first(where: { $0.id == client.id })?.reminder.enabled == false && client.reminder.enabled
            guard all.contains(where: { $0.id == client.id }) || all.count < Limits.clients else { throw ZapysError.limit("До 1000 клієнтів") }
            try put(client, id: client.id.uuidString, table: "clients")
            snapshot.clients[client.id] = client
            let affected = snapshot.appointments.filter { $0.clientID == client.id }
            for a in affected { try refresh(a, snapshot: &snapshot, now: now, restoringEligibility: restoring) }
        }
    }
    public func saveService(_ service: Service) throws {
        try Rules.validate(service)
        try transaction { try put(service, id: service.id.uuidString, table: "services") }
    }
    public func saveAppointment(_ appointment: Appointment, allowOverlap: Bool = false, now: Date = Date()) throws {
        try Rules.validate(appointment)
        try transaction {
            var snapshot = try reconciledSnapshot()
            let all = snapshot.appointments
            guard all.contains(where: { $0.id == appointment.id }) || all.count < Limits.appointments else { throw ZapysError.limit("До 10000 записів") }
            if let id = appointment.clientID {
                guard snapshot.clients[id] != nil else { throw ZapysError.notFound }
            }
            let conflicts = all.filter { Rules.overlaps(appointment, $0) }.map(\.id)
            guard allowOverlap || conflicts.isEmpty else { throw ZapysError.overlap(conflicts) }
            var saved = appointment
            let previous = all.first { $0.id == saved.id }
            if let client = saved.clientID.flatMap({ snapshot.clients[$0] }) {
                if saved.clientNameSnapshot == nil || previous?.clientID != saved.clientID { saved.clientNameSnapshot = client.displayName; saved.clientColorSnapshot = client.colorHex }
            } else if saved.clientID == nil {
                saved.clientNameSnapshot = nil; saved.clientColorSnapshot = nil
            }
            try put(saved, id: saved.id.uuidString, table: "appointments")
            let restoring = previous.map { ($0.status != .planned || $0.isDraft) && saved.status == .planned && !saved.isDraft } ?? false
            try refresh(saved, snapshot: &snapshot, now: now, restoringEligibility: restoring)
        }
    }
    private func refresh(_ appointment: Appointment, snapshot: inout Snapshot, now: Date, restoringEligibility: Bool = false) throws {
        let old = snapshot.reminders[appointment.id]
        let client = appointment.clientID.flatMap { snapshot.clients[$0] }
        if var fresh = Rules.makeReminder(appointment: appointment, client: client, now: now, timeZone: snapshot.zone) {
            if let old {
                let restoreAutomaticCancellation = restoringEligibility && old.state == .cancelled && old.cancellationReason == .eligibilityLost
                if !restoreAutomaticCancellation && old.dueAt == fresh.dueAt && old.message == fresh.message && old.recipient == fresh.recipient && old.channel == fresh.channel {
                    fresh.id = old.id
                    fresh.state = old.state == .scheduled && fresh.dueAt <= now ? .needsManualAction : old.state
                    fresh.error = old.error
                    fresh.cancellationReason = old.cancellationReason
                }
            }
            // Appointment UUID is the SQLite primary key: one reminder per appointment.
            try storeReminder(fresh, snapshot: &snapshot)
        } else if var old = old, old.state != .handedToMessenger && old.state != .cancelled {
            old.state = .cancelled; old.error = nil; old.cancellationReason = .eligibilityLost
            try storeReminder(old, snapshot: &snapshot)
        }
    }
    public func reminders(now: Date = Date()) throws -> [Reminder] {
        try transaction {
            var snapshot = try reconciledSnapshot()
            try refreshAll(&snapshot, now: now)
            return snapshot.orderedReminders
        }
    }
    public func actionableReminder(_ id: UUID, now: Date = Date()) throws -> Reminder {
        let row = try transaction { () throws -> Reminder? in
            var snapshot = try reconciledSnapshot()
            try refreshAll(&snapshot, now: now)
            return snapshot.reminders.values.first { $0.id == id && DevicePolicy.actionable($0, now: now) }
        }
        guard let row else { throw ZapysError.notFound }; return row
    }
    public func setReminderState(_ id: UUID, state: ReminderState, error: String? = nil, now: Date = Date()) throws {
        guard state == .handedToMessenger || state == .error || state == .cancelled else { throw ZapysError.validation("Недопустимий стан") }
        let accepted = try transaction { () throws -> Bool in
            var snapshot = try reconciledSnapshot()
            try refreshAll(&snapshot, now: now)
            let current = snapshot.reminders.values
            guard var reminder = current.first(where: { $0.id == id }), DevicePolicy.actionable(reminder, now: now) else { return false }
            reminder.state = state; reminder.error = state == .error ? error : nil
            reminder.cancellationReason = state == .cancelled ? .user : nil
            try storeReminder(reminder, snapshot: &snapshot)
            return true
        }
        // A domain rejection must not roll back newly committed expiry changes.
        if !accepted { throw ZapysError.notFound }
    }
    public func markHandedToMessenger(_ id: UUID, now: Date = Date()) throws {
        try setReminderState(id, state: .handedToMessenger, now: now)
    }
    private struct Snapshot {
        let zone: TimeZone
        let appointments: [Appointment]
        var clients: [UUID: Client]
        var reminders: [UUID: Reminder]
        var reminderOrder: [UUID]
        var orderedReminders: [Reminder] { reminderOrder.compactMap { reminders[$0] } }
    }
    private func storeReminder(_ row: Reminder, snapshot: inout Snapshot) throws {
        guard snapshot.reminders[row.appointmentID] != row else { return }
        try put(row, id: row.appointmentID.uuidString, table: "reminders")
        if snapshot.reminders[row.appointmentID] == nil { snapshot.reminderOrder.append(row.appointmentID) }
        snapshot.reminders[row.appointmentID] = row
    }
    private func refreshAll(_ snapshot: inout Snapshot, now: Date) throws {
        let appointments = snapshot.appointments
        for appointment in appointments { try refresh(appointment, snapshot: &snapshot, now: now) }
    }
    /// One decode per stored row per transaction; lookups in the loop are keyed.
    private func reconciledSnapshot() throws -> Snapshot {
        let zone = timeZone()
        let rows = try records("reminders", as: Reminder.self)
        var snapshot = Snapshot(zone: zone, appointments: try appointments(),
            clients: Dictionary(uniqueKeysWithValues: try clients().map { ($0.id, $0) }),
            reminders: Dictionary(uniqueKeysWithValues: rows.map { ($0.appointmentID, $0) }),
            reminderOrder: rows.map(\.appointmentID))
        let previous = try records("metadata", as: String.self).first
        if let previous, previous != zone.identifier, let oldZone = TimeZone(identifier: previous) {
            let appointments = snapshot.appointments
            for appointment in appointments {
                guard let client = appointment.clientID.flatMap({ snapshot.clients[$0] }),
                      var row = snapshot.reminders[appointment.id],
                      let oldStart = Rules.date(day: appointment.day, minute: appointment.startMinute, timeZone: oldZone),
                      let newStart = Rules.date(day: appointment.day, minute: appointment.startMinute, timeZone: zone),
                      row.dueAt == oldStart.addingTimeInterval(-Double(client.reminder.leadMinutes) * 60) else { continue }
                row.dueAt = newStart.addingTimeInterval(-Double(client.reminder.leadMinutes) * 60)
                try storeReminder(row, snapshot: &snapshot)
            }
        }
        if previous != zone.identifier { try put(zone.identifier, id: "timezone", table: "metadata") }
        return snapshot
    }
    public func deleteService(_ id: UUID) throws { try transaction { try remove(id, table: "services") } }
    public func deleteClient(_ id: UUID) throws {
        try transaction {
            let all = try appointments()
            guard !all.contains(where: { $0.clientID == id }) else {
                throw ZapysError.validation("Неможливо видалити клієнта, який має записи. Спочатку видаліть або змініть ці записи.")
            }
            try remove(id, table: "clients")
        }
    }
    public func deleteAppointment(_ id: UUID) throws {
        try transaction {
            try remove(id, table: "appointments")
            // Removed appointments have inert cancelled outbox records.
            if var reminder = try records("reminders", as: Reminder.self).first(where: { $0.appointmentID == id }) {
                reminder.state = .cancelled; reminder.error = nil
                try put(reminder, id: id.uuidString, table: "reminders")
            }
        }
    }
    public func repeatAppointment(_ id: UUID, on day: String, startMinute: Int) throws -> Appointment {
        guard let source = try appointments().first(where: { $0.id == id }) else { throw ZapysError.notFound }
        let repeated = Rules.repeated(source, day: day, startMinute: startMinute)
        try saveAppointment(repeated)
        return repeated
    }
}
