import Foundation

public enum Limits {
    public static let clients = 1000
    public static let appointments = 10000
    public static let noteCharacters = 2000
    public static let servicesPerAppointment = 5
    public static let freeReminders = 1
}

public enum MessengerChannel: String, Codable, CaseIterable, Identifiable {
    case viber, whatsapp, telegram, instagram
    public var id: String { rawValue }
    public var title: String { switch self { case .viber: return "Viber"; case .whatsapp: return "WhatsApp"; case .telegram: return "Telegram"; case .instagram: return "Instagram" } }
}
public struct ReminderPreferences: Codable, Equatable {
    public var enabled = false
    public var channel: MessengerChannel = .viber
    public var recipient = ""
    public var leadMinutes = 1440
    public var template = "[ім’я], нагадуємо про запис [дата] о [час]. Послуги: [послуги]."
    public init() {}
}
public struct Client: Codable, Identifiable, Equatable {
    public var id: UUID
    public var name: String
    public var surname = ""
    public var patronymic = ""
    public var phone: String
    public var note = ""
    public var colorHex: String?
    public var reminder = ReminderPreferences()
    public var displayName: String { [name, surname, patronymic].filter { !$0.isEmpty }.joined(separator: " ") }
    public init(id: UUID = UUID(), name: String, phone: String) { self.id = id; self.name = name; self.phone = phone }
}
public struct Service: Codable, Identifiable, Equatable {
    public var id: UUID
    public var name: String
    public var durationMinutes: Int?
    public var priceMinor: Int64?
    public var note = ""
    public var colorHex: String?
    public init(id: UUID = UUID(), name: String, durationMinutes: Int? = nil, priceMinor: Int64? = nil) { self.id = id; self.name = name; self.durationMinutes = durationMinutes; self.priceMinor = priceMinor }
}
public struct ServiceSnapshot: Codable, Identifiable, Equatable {
    public var id: UUID
    public var sourceID: UUID
    public var name: String
    public var durationMinutes: Int?
    public var priceMinor: Int64?
    public var note: String
    public var colorHex: String?
    public init(service: Service) { id = UUID(); sourceID = service.id; name = service.name; durationMinutes = service.durationMinutes; priceMinor = service.priceMinor; note = service.note; colorHex = service.colorHex }
}
public enum AppointmentStatus: String, Codable, CaseIterable, Identifiable {
    case planned, completed, cancelled, noShow
    public var id: String { rawValue }
    public var title: String { switch self { case .planned: return "Заплановано"; case .completed: return "Завершено"; case .cancelled: return "Скасовано"; case .noShow: return "Не з’явився" } }
}
public struct Appointment: Codable, Identifiable, Equatable {
    public var id: UUID
    public var clientID: UUID?
    public var clientNameSnapshot: String?
    public var clientColorSnapshot: String?
    public var day: String
    public var startMinute: Int
    public var endMinute: Int
    public var endManuallySet = false
    public var services: [ServiceSnapshot] = []
    public var isDraft = true
    public var status: AppointmentStatus = .planned
    public var paid = false
    public var receivedMinor: Int64?
    public var note = ""
    public init(id: UUID = UUID(), day: String, startMinute: Int, endMinute: Int) { self.id = id; self.day = day; self.startMinute = startMinute; self.endMinute = endMinute }
}
public enum ReminderState: String, Codable, CaseIterable {
    case scheduled, needsManualAction, handedToMessenger, error, cancelled
    public var title: String { switch self { case .scheduled: return "Заплановано"; case .needsManualAction: return "Потрібно надіслати вручну"; case .handedToMessenger: return "Передано до месенджера"; case .error: return "Помилка"; case .cancelled: return "Скасовано" } }
}
public enum ReminderCancellationReason: String, Codable { case user, eligibilityLost }
public struct Reminder: Codable, Identifiable, Equatable {
    public var id: UUID
    public var appointmentID: UUID
    public var dueAt: Date
    public var channel: MessengerChannel
    public var recipient: String
    public var message: String
    public var state: ReminderState
    public var error: String?
    // Missing in older payloads: conservatively retain terminal cancellation.
    public var cancellationReason: ReminderCancellationReason?
    public init(id: UUID = UUID(), appointmentID: UUID, dueAt: Date, channel: MessengerChannel, recipient: String, message: String, state: ReminderState = .scheduled) { self.id = id; self.appointmentID = appointmentID; self.dueAt = dueAt; self.channel = channel; self.recipient = recipient; self.message = message; self.state = state }
}
public struct LocalProfile: Codable, Equatable {
    public var name = ""
    public var phone = ""
    public var email = ""
    public var notificationsEnabled = false
    public init() {}
}
