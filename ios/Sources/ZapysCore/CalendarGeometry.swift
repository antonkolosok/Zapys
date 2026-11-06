import Foundation

public struct CalendarLane {
 public let appointment: Appointment
 public let lane: Int
 public let laneCount: Int
}
public struct CalendarStop: Equatable {
 public let colorHex: String
 /// Logical row center, in points, even below a clipped event.
 public let center: Double
}
public enum CalendarGeometry {
 public static let scale = 1.2
 public static let gutter = 42.0
 public static func top(_ minute: Int) -> Double { Double(minute) * scale }
 public static func height(start: Int, end: Int) -> Double { Double(end - start) * scale }
 private static var calendar: Calendar {
  var c = Calendar(identifier: .gregorian); c.timeZone = TimeZone(secondsFromGMT: 0)!; return c
 }
 private static func date(_ day: String) -> Date? { Rules.date(day: day, minute: 720, timeZone: calendar.timeZone) }
 private static func key(_ date: Date) -> String {
  let f = DateFormatter(); f.calendar = calendar; f.timeZone = calendar.timeZone; f.locale = Locale(identifier: "en_US_POSIX"); f.dateFormat = "yyyy-MM-dd"; return f.string(from: date)
 }
 public static func weekDays(_ day: String) -> [String] {
  guard let date = date(day) else { return [] }
  let offset = (calendar.component(.weekday, from: date) + 5) % 7
  return (0..<7).map { key(calendar.date(byAdding: .day, value: $0 - offset, to: date)!) }
 }
 public static func navigate(_ day: String, weeks: Int) -> String {
  guard let date = date(day), let next = calendar.date(byAdding: .day, value: weeks * 7, to: date) else { return day }; return key(next)
 }
 public static func draft(day: String, minute: Double, id: UUID = UUID()) -> Appointment {
  let start = min(1409, max(0, Int((minute / 15).rounded()) * 15))
  return Appointment(id: id, day: day, startMinute: start, endMinute: start + 30)
 }
 public static func resize(_ original: Appointment, minute: Double) -> Appointment {
  var value = original; value.endMinute = min(1439, max(original.startMinute + 1, Int((minute / 15).rounded()) * 15)); value.endManuallySet = true; return value
 }
 public static func gradient(colors: [String?], rowCenters: [Double]) -> [CalendarStop] {
  precondition(colors.count == rowCenters.count)
  return colors.enumerated().compactMap { index, color in color.map { CalendarStop(colorHex: $0, center: rowCenters[index]) } }
 }
 public static func lanes(_ records: [Appointment]) -> [CalendarLane] {
  var result: [CalendarLane] = []
  let days = Dictionary(grouping: records.filter { $0.status != .cancelled }, by: \.day)
  for day in days.keys.sorted() {
   var group: [(Appointment, Int)] = []; var ends: [Int] = []
   func flush() {
    result += group.map { CalendarLane(appointment: $0.0, lane: $0.1, laneCount: ends.count) }
    group.removeAll(); ends.removeAll()
   }
   for a in days[day]!.sorted(by: { ($0.startMinute, $0.endMinute, $0.id.uuidString) < ($1.startMinute, $1.endMinute, $1.id.uuidString) }) {
    if let end = ends.max(), end <= a.startMinute { flush() }
    let lane = ends.firstIndex(where: { $0 <= a.startMinute }) ?? ends.count
    if lane == ends.count { ends.append(a.endMinute) } else { ends[lane] = a.endMinute }
    group.append((a,lane))
   }
   flush()
  }
  return result
 }
}
