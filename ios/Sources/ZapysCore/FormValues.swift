import Foundation
/// Strict locale-independent form conversion; unknown is different from zero.
public enum FormValues {
 public static func duration(_ text: String) throws -> Int? {
  let s = text.trimmingCharacters(in: .whitespacesAndNewlines)
  if s.isEmpty { return nil }
  guard s.allSatisfy({ $0.isASCII && $0.isNumber }), let n = Int(s), (1...1439).contains(n) else { throw ZapysError.validation("Тривалість: від 1 до 1439 цілих хвилин") }
  return n
 }
 public static func price(_ text: String) throws -> Int64? {
  let s = text.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
  if s.isEmpty { return nil }
  guard s.range(of: "^[0-9]+(?:[.][0-9]{1,2})?$", options: .regularExpression) != nil else { throw ZapysError.validation("Вкажіть невід’ємну ціну, до двох десяткових знаків") }
  let parts = s.split(separator: ".")
  guard let whole = Int64(parts[0]) else { throw ZapysError.validation("Ціна завелика") }
  let fraction = parts.count == 2 ? Int64(parts[1].count == 1 ? String(parts[1]) + "0" : String(parts[1]))! : 0
  let (base, overflow) = whole.multipliedReportingOverflow(by: 100)
  let (result, overflow2) = base.addingReportingOverflow(fraction)
  guard !overflow && !overflow2 else { throw ZapysError.validation("Ціна завелика") }
  return result
 }
 public static func priceText(_ value: Int64?) -> String {
  guard let value else { return "" }
  return String(format: "%lld.%02lld", value / 100, value % 100)
 }
 public static func client(name: String, phone: String) throws -> Client {
  let c = Client(name: name.trimmingCharacters(in: .whitespacesAndNewlines), phone: phone.trimmingCharacters(in: .whitespacesAndNewlines))
  guard !c.name.isEmpty else { throw ZapysError.validation("Вкажіть ім’я") }
  guard !c.phone.isEmpty else { throw ZapysError.validation("Вкажіть номер телефону") }
  return c
 }
 public static func phoneKey(_ value: String) -> String { value.filter { $0.isNumber } }
}
