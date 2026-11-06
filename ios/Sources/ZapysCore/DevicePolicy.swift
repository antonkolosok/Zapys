import Foundation

public enum ShareOutcome: Equatable { case retry, handoff, error }
public enum DevicePolicy {
 public static let notificationLimit = 60
 public static let notificationPrefix = "ua.zapys.reminder."
 public static func actionable(_ row: Reminder, now: Date) -> Bool {
  (row.state == .needsManualAction || row.state == .scheduled || row.state == .error) && row.dueAt <= now && !row.recipient.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
 }
 public static func notificationWindow(_ rows: [Reminder], now: Date, enabled: Bool) -> [Reminder] {
  guard enabled else { return [] }
  return Array(rows.filter { ($0.state == .scheduled || $0.state == .needsManualAction || $0.state == .error) && $0.dueAt > now }.sorted { $0.dueAt == $1.dueAt ? $0.id.uuidString < $1.id.uuidString : $0.dueAt < $1.dueAt }.prefix(notificationLimit))
 }
 public static func shareOutcome(completed: Bool, activity: String?, failed: Bool) -> ShareOutcome {
  if failed { return .error }
  guard completed, let activity else { return .retry }
  // An unknown extension or clipboard completion does not prove messenger handoff.
  let supported = ["net.whatsapp.WhatsApp.ShareExtension", "ph.telegra.Telegraph.Share", "com.viber.Viber.ShareExtension", "com.burbn.instagram.shareextension"]
  return supported.contains(activity) ? .handoff : .retry
 }
}
