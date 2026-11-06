import Foundation

public enum ZapysError: Error, LocalizedError, Equatable {
    case validation(String), overlap([UUID]), database(String), limit(String), notFound
    public var errorDescription: String? {
        switch self { case .validation(let s), .database(let s), .limit(let s): return s; case .overlap: return "Цей час перетинається з іншим записом. Зберегти попри накладення?"; case .notFound: return "Запис не знайдено" }
    }
}
public enum Rules {
    public static func validate(_ client: Client) throws {
        guard !client.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, !client.phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw ZapysError.validation("Вкажіть ім’я та телефон клієнта") }
        try note(client.note); try note(client.reminder.template)
        guard ((client.reminder.enabled ? 1 : 0)...525600).contains(client.reminder.leadMinutes) else { throw ZapysError.validation("Час нагадування: від 0 до 525600 хвилин") }
        if client.reminder.enabled && client.reminder.recipient.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { throw ZapysError.validation("Вкажіть одержувача нагадування") }
        try color(client.colorHex)
    }
    public static func validate(_ service: Service) throws {
        guard !service.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { throw ZapysError.validation("Вкажіть назву послуги") }
        if let duration = service.durationMinutes, !(1...1439).contains(duration) { throw ZapysError.validation("Тривалість: від 1 до 1439 хвилин") }
        try money(service.priceMinor); try note(service.note); try color(service.colorHex)
    }
    public static func validate(_ appointment: Appointment) throws {
        guard date(day: appointment.day, minute: 0) != nil else { throw ZapysError.validation("Некоректна дата") }
        guard (0...1438).contains(appointment.startMinute), (1...1439).contains(appointment.endMinute), appointment.endMinute > appointment.startMinute else { throw ZapysError.validation("Кінець має бути після початку та не пізніше 23:59") }
        guard appointment.services.count <= Limits.servicesPerAppointment else { throw ZapysError.validation("До п’яти послуг у записі") }
        guard appointment.isDraft || appointment.clientID != nil else { throw ZapysError.validation("Оберіть клієнта") }
        try money(appointment.receivedMinor); try note(appointment.note)
        for snapshot in appointment.services {
            var service = Service(id: snapshot.sourceID, name: snapshot.name, durationMinutes: snapshot.durationMinutes, priceMinor: snapshot.priceMinor)
            service.note = snapshot.note; service.colorHex = snapshot.colorHex; try validate(service)
        }
    }
    public static func note(_ value: String) throws { guard value.count <= Limits.noteCharacters else { throw ZapysError.validation("До 2000 символів у примітці або шаблоні") } }
    public static func money(_ value: Int64?) throws { if let value, value < 0 { throw ZapysError.validation("Сума не може бути від’ємною") } }
    public static func color(_ value: String?) throws { if let value, value.range(of: "^#[0-9a-fA-F]{6}$", options: .regularExpression) == nil { throw ZapysError.validation("Колір має формат #RRGGBB") } }
    public static func overlaps(_ a: Appointment, _ b: Appointment) -> Bool { a.id != b.id && a.day == b.day && a.status != .cancelled && b.status != .cancelled && a.startMinute < b.endMinute && b.startMinute < a.endMinute }
    public static func time(_ minute: Int) -> String { String(format: "%02d:%02d", minute / 60, minute % 60) }
    public static func day(_ date: Date) -> String { let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.calendar = Calendar(identifier: .gregorian); f.dateFormat = "yyyy-MM-dd"; return f.string(from: date) }
    public static func date(day: String, minute: Int, timeZone: TimeZone = .current) -> Date? {
        let f = DateFormatter(); f.locale = Locale(identifier: "en_US_POSIX"); f.calendar = Calendar(identifier: .gregorian); f.dateFormat = "yyyy-MM-dd"; f.isLenient = false
        f.timeZone = timeZone
        guard let date = f.date(from: day), f.string(from: date) == day, (0...1439).contains(minute) else { return nil }
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        // Match wall-clock components, never elapsed minutes from midnight.
        // A nonexistent time advances by the gap; a repeated time uses its first occurrence.
        return calendar.date(bySettingHour: minute / 60, minute: minute % 60, second: 0, of: date, matchingPolicy: .nextTimePreservingSmallerComponents, repeatedTimePolicy: .first, direction: .forward)
    }
    public static func suggestedEnd(startMinute: Int, services: [ServiceSnapshot]) -> Int? {
        guard !services.isEmpty, services.allSatisfy({ $0.durationMinutes != nil }) else { return nil }
        let result = startMinute + services.reduce(0) { $0 + ($1.durationMinutes ?? 0) }
        return result <= 1439 ? result : nil
    }
    public static func repeated(_ original: Appointment, day: String, startMinute: Int) -> Appointment {
        var result = original; result.id = UUID(); result.day = day; result.startMinute = min(1438, max(0, startMinute)); result.endMinute = min(1439, result.startMinute + max(1, original.endMinute - original.startMinute)); result.isDraft = true; result.status = .planned; result.paid = false; result.receivedMinor = nil; result.endManuallySet = false
        return result
    }
    public static func render(template: String, appointment: Appointment, clientName: String, serviceNames: [String]) -> String {
        let values = ["[ім’я]": clientName, "[ім'я]": clientName, "[дата]": appointment.day, "[час]": time(appointment.startMinute), "[послуги]": serviceNames.joined(separator: ", ")]
        let pattern = "\\[(?:ім’я|ім'я|дата|час|послуги)\\]"
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return template }
        var output = template
        for match in regex.matches(in: template, range: NSRange(template.startIndex..., in: template)).reversed() {
            guard let range = Range(match.range, in: output), let sourceRange = Range(match.range, in: template) else { continue }
            output.replaceSubrange(range, with: values[String(template[sourceRange])] ?? "")
        }
        return output
    }
    public static func makeReminder(appointment: Appointment, client: Client?, now: Date = Date(), timeZone: TimeZone = .current) -> Reminder? {
        guard !appointment.isDraft, appointment.status == .planned, let client, client.reminder.enabled, client.reminder.leadMinutes > 0, let start = date(day: appointment.day, minute: appointment.startMinute, timeZone: timeZone), start > now else { return nil }
        let due = start.addingTimeInterval(-Double(client.reminder.leadMinutes) * 60)
        return Reminder(appointmentID: appointment.id, dueAt: due, channel: client.reminder.channel, recipient: client.reminder.recipient, message: render(template: client.reminder.template, appointment: appointment, clientName: client.displayName, serviceNames: appointment.services.map(\.name)), state: due <= now ? .needsManualAction : .scheduled)
    }
}
