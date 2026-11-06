import SwiftUI
import ZapysCore

final class AppModel: ObservableObject {
 let reminderCoordinator = ReminderCoordinator()
 @Published var reminders: [Reminder] = []
 @Published var clients: [Client] = []
 @Published var services: [Service] = []
 @Published var appointments: [Appointment] = []
 @Published var profile = LocalProfile()
 @Published var error: String?
 @Published var ready = false
 private let queue = DispatchQueue(label: "ua.zapys.database", qos: .userInitiated)
 // This property is accessed exclusively on queue, including construction.
 private var repository: SQLiteRepository?
 init() {
  let testing = ProcessInfo.processInfo.arguments.contains("--ui-testing-temporary-database")
  queue.async {
   do {
    let folder = testing ? FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString) : try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true).appendingPathComponent("Zapys")
    try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
    self.repository = try SQLiteRepository(path: folder.appendingPathComponent("zapys.sqlite").path)
    if testing && ProcessInfo.processInfo.arguments.contains("--ui-testing-reminder-actions"), let repository = self.repository {
     // Synthetic fixture only in a newly allocated isolated UI-test database.
     // Profile notifications remain disabled; no permission or share action occurs.
     let now = Date()
     var client = Client(name: "Synthetic reminder fixture", phone: "000")
     client.reminder.enabled = true; client.reminder.recipient = "synthetic"; client.reminder.leadMinutes = 2880
     try repository.saveClient(client, now: now)
     let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: now)!
     for minute in [720, 780] {
      var appointment = Appointment(day: Rules.day(tomorrow), startMinute: minute, endMinute: minute + 30)
      appointment.clientID = client.id; appointment.isDraft = false
      try repository.saveAppointment(appointment, now: now)
     }
    }
    let snapshot = try self.read()
    DispatchQueue.main.async { self.apply(snapshot); self.ready = true }
   } catch { DispatchQueue.main.async { self.error = "Не вдалося відкрити дані. Перезапустіть програму."; } }
  }
 }
 private typealias Snapshot = ([Client], [Service], LocalProfile, [Appointment], [Reminder])
 private func read() throws -> Snapshot {
  guard let repository else { throw ZapysError.notFound }
  return try (repository.clients(), repository.services(), repository.profile(), repository.appointments(), repository.reminders())
 }
 private func apply(_ snapshot: Snapshot) { error = nil; clients = snapshot.0; services = snapshot.1; profile = snapshot.2; appointments = snapshot.3; reminders = snapshot.4; reminderCoordinator.reconcile(rows: reminders, enabled: profile.notificationsEnabled) }
 func writeTyped(_ action: @escaping (SQLiteRepository) throws -> Void, completion: @escaping (Result<Void, Error>) -> Void) {
  queue.async {
   do {
    guard let repository = self.repository else { throw ZapysError.notFound }
    try action(repository); let snapshot = try self.read()
    DispatchQueue.main.async { self.apply(snapshot); completion(.success(())) }
   } catch { DispatchQueue.main.async { completion(.failure(error)); self.refreshReminders() } }
  }
 }
 func refreshReminders() {
  queue.async {
   do { let snapshot = try self.read(); DispatchQueue.main.async { self.apply(snapshot) } }
   catch { DispatchQueue.main.async { self.error = error.localizedDescription; self.reminderCoordinator.reconcile(rows: [], enabled: false) } }
  }
 }
 func prepareReminder(_ id: UUID, completion: @escaping (Result<Reminder, Error>) -> Void) {
  queue.async {
   do {
    guard let repository = self.repository else { throw ZapysError.notFound }
    let row = try repository.actionableReminder(id)
    DispatchQueue.main.async { completion(.success(row)) }
   } catch { DispatchQueue.main.async { completion(.failure(error)); self.refreshReminders() } }
  }
 }
 func write(_ action: @escaping (SQLiteRepository) throws -> Void, completion: @escaping (String?) -> Void) {
  writeTyped(action) { result in
   switch result { case .success: completion(nil); case .failure(let error): completion("Не вдалося зберегти дані. " + error.localizedDescription) }
  }
 }
}
