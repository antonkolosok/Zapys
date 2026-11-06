import Foundation

/// Retains raw input independently of persisted snapshots; preparing a save never mutates it.
public struct AppointmentDraft: Equatable {
 public var appointment: Appointment
 public var dateProvided = true
 public var startProvided = true
 public var endProvided: Bool
 public var durationText: [UUID: String] = [:]
 public var priceText: [UUID: String] = [:]
 public var receivedText: String
 public var inlineName = ""
 public var inlinePhone = ""
 public private(set) var inlineClientID = UUID()
 public init(appointment: Appointment? = nil, now: Date = Date()) {
  self.appointment = appointment ?? Appointment(day: Rules.day(now), startMinute: 540, endMinute: 570)
  endProvided = appointment != nil
  receivedText = FormValues.priceText(self.appointment.receivedMinor)
  for s in self.appointment.services { durationText[s.id] = s.durationMinutes.map(String.init) ?? ""; priceText[s.id] = FormValues.priceText(s.priceMinor) }
 }
 public mutating func selectClient(_ client: Client?) {
  appointment.clientID = client?.id; appointment.clientNameSnapshot = client?.displayName; appointment.clientColorSnapshot = client?.colorHex
  inlineName = ""; inlinePhone = ""
 }
 public mutating func addService(_ service: Service) throws {
  guard appointment.services.count < Limits.servicesPerAppointment else { throw ZapysError.validation("У записі вже 5 послуг") }
  let s = ServiceSnapshot(service: service); appointment.services.append(s)
  durationText[s.id] = s.durationMinutes.map(String.init) ?? ""; priceText[s.id] = FormValues.priceText(s.priceMinor); applySuggestion()
 }
 public mutating func removeService(at index: Int) { let s = appointment.services.remove(at: index); durationText[s.id] = nil; priceText[s.id] = nil; applySuggestion() }
 private func snapshots() throws -> [ServiceSnapshot] {
  try appointment.services.map { s in var copy = s; copy.durationMinutes = try FormValues.duration(durationText[s.id] ?? ""); copy.priceMinor = try FormValues.price(priceText[s.id] ?? ""); return copy }
 }
 public var suggestion: Int? { guard let values = try? snapshots() else { return nil }; return Rules.suggestedEnd(startMinute: appointment.startMinute, services: values) }
 public mutating func applySuggestion(force: Bool = false) {
  guard force || !appointment.endManuallySet, let minute = suggestion else { return }
  appointment.endMinute = minute; endProvided = true
  if force { appointment.endManuallySet = false }
 }
 public var totalText: String {
  guard let values = try? snapshots() else { return "Перевірте тривалість і ціну" }
  // Decimal is exact for the sum of five nonnegative Int64 minor-unit values.
  let total = values.reduce(Decimal(0)) { $0 + Decimal($1.priceMinor ?? 0) }
  let amount = NSDecimalNumber(decimal: total / 100).stringValue
  let parts = amount.split(separator: ".", omittingEmptySubsequences: false)
  let formatted = String(parts[0]) + "." + (parts.count == 1 ? "00" : String(parts[1]).padding(toLength: 2, withPad: "0", startingAt: 0))
  return formatted + " ₴" + (values.contains { $0.priceMinor == nil } ? " + невідома ціна" : "")
 }
 public func prepareInlineClient() throws -> Client? {
  guard !inlineName.isEmpty || !inlinePhone.isEmpty else { return nil }
  var client = try FormValues.client(name: inlineName, phone: inlinePhone); client.id = inlineClientID; try Rules.validate(client); return client
 }
 public mutating func inlineClientSaved(_ client: Client) { selectClient(client); inlineClientID = UUID() }
 public enum TimeField: String, Equatable, Hashable { case date, start, end }
 public struct TimeValidationIssue: Equatable {
  public let field: TimeField
  public let message: String
 }
 /// Matches the date/start/end order in the native form, before client and text inputs.
 public var timeValidationIssue: TimeValidationIssue? {
  if !dateProvided || Rules.date(day: appointment.day, minute: 0) == nil { return TimeValidationIssue(field: .date, message: "Укажіть коректну дату") }
  if !startProvided || !(0...1438).contains(appointment.startMinute) { return TimeValidationIssue(field: .start, message: "Укажіть початок від 00:00 до 23:58") }
  if !endProvided { return TimeValidationIssue(field: .end, message: "Укажіть час закінчення") }
  if !(1...1439).contains(appointment.endMinute) || appointment.endMinute <= appointment.startMinute { return TimeValidationIssue(field: .end, message: "Закінчення має бути після початку, не пізніше 23:59") }
  return nil
 }
 public func prepared(isDraft: Bool) throws -> Appointment {
  if let issue = timeValidationIssue { throw ZapysError.validation(issue.message) }
  var result = appointment; result.services = try snapshots(); result.receivedMinor = try FormValues.price(receivedText); result.isDraft = isDraft
  if let client = try prepareInlineClient() { result.clientID = client.id; result.clientNameSnapshot = client.displayName; result.clientColorSnapshot = client.colorHex }
  try Rules.validate(result); return result
 }
 public static func repeated(_ original: Appointment, now: Date = Date()) -> AppointmentDraft {
  var d = AppointmentDraft(appointment: Rules.repeated(original, day: Rules.day(now), startMinute: 540)); d.dateProvided = false; d.startProvided = false; d.endProvided = false; return d
 }
}
public enum AppointmentHistory {
 public static func split(_ appointments: [Appointment], now: Date) -> (past: [Appointment], upcoming: [Appointment]) {
  let sorted = appointments.sorted { ($0.day, $0.startMinute) < ($1.day, $1.startMinute) }
  return (sorted.filter { (Rules.date(day: $0.day, minute: $0.endMinute) ?? .distantFuture) <= now }.reversed().map { $0 }, sorted.filter { (Rules.date(day: $0.day, minute: $0.endMinute) ?? .distantFuture) > now })
 }
}




