import Foundation
import Combine
import UserNotifications
import ZapysCore

final class ReminderCoordinator: ObservableObject {
 @Published var coverage = "Сповіщення ще не перевірено"
 @Published var authorization = "Не перевірено"
 private let center = UNUserNotificationCenter.current()
 private var tail: Task<Void, Never>?
 func requestPermission(completion: @escaping () -> Void) {
  Task { @MainActor in
   do { _ = try await center.requestAuthorization(options: [.alert, .sound]) }
   catch { coverage = "Не вдалося запитати дозвіл: \(error.localizedDescription)" }
   completion()
  }
 }
 func reconcile(rows: [Reminder], enabled: Bool) {
  let previous = tail
  tail = Task { @MainActor in
   await previous?.value
   let settings = await center.notificationSettings()
   let authorized = settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional || settings.authorizationStatus == .ephemeral
   let allowed = authorized && settings.alertSetting == .enabled
   authorization = allowed ? "Дозвіл надано" : (settings.authorizationStatus == .notDetermined ? "Дозвіл ще не запитано" : "Сповіщення заборонені")
   let pending = await center.pendingNotificationRequests()
   let delivered = await center.deliveredNotifications()
   let prefix = DevicePolicy.notificationPrefix
   center.removePendingNotificationRequests(withIdentifiers: pending.map(\.identifier).filter { $0.hasPrefix(prefix) })
   center.removeDeliveredNotifications(withIdentifiers: delivered.map { $0.request.identifier }.filter { $0.hasPrefix(prefix) })
   // Await a subsequent center query before installing this serialized generation.
   _ = await center.pendingNotificationRequests()
   let now = Date()
   let desired = DevicePolicy.notificationWindow(rows, now: now, enabled: enabled && allowed)
   var failures = 0
   for row in desired {
    let seconds = row.dueAt.timeIntervalSinceNow
    guard seconds > 0 else { continue }
    let content = UNMutableNotificationContent()
    content.title = "Zapys"; content.body = "Час підготувати нагадування в Zapys"; content.sound = .default
    let request = UNNotificationRequest(identifier: prefix + row.id.uuidString, content: content, trigger: UNTimeIntervalNotificationTrigger(timeInterval: max(1, seconds), repeats: false))
    do { try await center.add(request) } catch { failures += 1 }
   }
   let actual = await center.pendingNotificationRequests()
   let count = actual.filter { $0.identifier.hasPrefix(prefix) }.count
   coverage = "У системній черзі: \(count). Вікно — до 60 найближчих майбутніх нагадувань. Прострочені очікують ручної дії тут. Оновлення — при відкритті та зміні даних. Доставка системою може затриматись."
   if !enabled { coverage = "Локальні сповіщення вимкнено. Ручна черга доступна." }
   else if !allowed { coverage = "Сповіщення недоступні. Ручна черга доступна." }
   if failures > 0 { coverage += " Не вдалося запланувати: \(failures). Спробуйте оновити." }
  }
 }
}
