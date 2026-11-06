import Foundation

public struct CalendarRows {
 public let labels: [String]
 public let metadata: String?
 /// Week start time shares the first logical label's existing height.
 public let compactTime: String?
 public init(labels: [String], metadata: String?, compactTime: String? = nil) {
  self.labels = labels; self.metadata = metadata; self.compactTime = compactTime
 }
 /// Centers of actual client/service text frames, including labels below the clip.
 public func labelCenters(rowHeight: Double, topPadding: Double = 2) -> [Double] {
  labels.indices.map { index in
   let fraction = index == 0 && compactTime != nil ? 0.75 : Double(index) + 0.5
   return topPadding + rowHeight * fraction
  }
 }
}
public enum CalendarPresentation {
 public static func appointmentLabel(_ a: Appointment) -> String {
  let parts = [a.clientNameSnapshot.flatMap { $0.isEmpty ? nil : $0 } ?? "Без клієнта",
   a.day, Rules.time(a.startMinute) + "-" + Rules.time(a.endMinute), status(a),
   "Послуг: \(a.services.count)"] + a.services.map(\.name)
  return parts.joined(separator: ", ")
 }
 public static func dayHeaderLabel(_ day: String, selected: Bool, today: Bool) -> String {
  (["Відкрити день " + day] + (selected ? ["Вибрано"] : []) + (today ? ["Сьогодні"] : [])).joined(separator: ", ")
 }
 public static func status(_ a: Appointment) -> String {
  let status: String
  switch a.status { case .planned: status = "Заплановано"; case .completed: status = "Виконано"; case .cancelled: status = "Скасовано"; case .noShow: status = "Не з’явився" }
  return a.isDraft ? "Чернетка · " + status : status
 }
 public static func rows(_ a: Appointment, height: Double, rowHeight: Double, week: Bool = false) -> CalendarRows {
  let labels = [a.clientNameSnapshot.flatMap { $0.isEmpty ? nil : $0 } ?? "Без клієнта"] + a.services.map(\.name)
  if height >= 4 + rowHeight * Double(labels.count + 1) {
   return CalendarRows(labels: labels, metadata: "\(status(a)) · \(Rules.time(a.startMinute))–\(Rules.time(a.endMinute))")
  }
  if week {
   let shortStatus: String
   if a.isDraft { shortStatus = "Черн." }
   else { switch a.status { case .planned: shortStatus = "План"; case .completed: shortStatus = "Вик."; case .cancelled: shortStatus = "Скас."; case .noShow: shortStatus = "Неяв." } }
   return CalendarRows(labels: ["\(shortStatus) · \(labels[0])"] + labels.dropFirst(), metadata: nil, compactTime: Rules.time(a.startMinute))
  }
  return CalendarRows(labels: ["\(status(a)) · \(labels[0])"] + labels.dropFirst(), metadata: nil)
 }
}
