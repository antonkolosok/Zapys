import SwiftUI
import UIKit
import ZapysCore

struct RemindersView: View {
 @EnvironmentObject var model: AppModel
 @Environment(\.scenePhase) private var phase
 @ObservedObject var coordinator: ReminderCoordinator
 @State private var preview: Reminder?
 @State private var share: Reminder?
 @State private var preparing = false
 @State private var epoch = UUID()
 @State private var message: String?
 var body: some View {
  List {
   Section("Локальні сповіщення") {
    Text(coordinator.authorization); Text(coordinator.coverage)
    Toggle("Нагадувати локально", isOn: Binding(get: { model.profile.notificationsEnabled }, set: { enabled in model.write({ repo in var value = try repo.profile(); value.notificationsEnabled = enabled; try repo.saveProfile(value) }) { message = $0 } }))
    Button("Запитати дозвіл") { coordinator.requestPermission { model.refreshReminders() } }
    Link("Системні налаштування", destination: URL(string: UIApplication.openSettingsURLString)!)
    Button("Оновити чергу") { model.refreshReminders() }
   }
   Section("Ручна черга") {
    Text("Автоматичне надсилання не підключено. Виберіть одержувача у потрібному застосунку. Відкриття або завершення поширення не підтверджує доставку.")
    ForEach(model.reminders) { row in
     VStack(alignment: .leading, spacing: 8) {
      Text(row.dueAt, style: .date); Text(row.dueAt, style: .time)
      Text(row.channel.title); Text(label(row.dueAt > Date() && (row.state == .needsManualAction || row.state == .scheduled) ? .scheduled : row.state))
      if let error = row.error { Text(error).foregroundStyle(.red) }
      if DevicePolicy.actionable(row, now: Date()) {
       Button("Підготувати нагадування") { prepare(row.id, open: false) }.disabled(preparing).buttonStyle(.borderless).accessibilityIdentifier("reminder.prepare")
       Button("Скасувати нагадування", role: .destructive) { model.write({ try $0.setReminderState(row.id, state: .cancelled) }) { message = $0 } }.buttonStyle(.borderless).accessibilityIdentifier("reminder.cancel")
      }
     }
    }
   }
   if let message { Text(message).foregroundStyle(.secondary) }
   if let error = model.error { Text(error).foregroundStyle(.red) }
  }.navigationTitle("Нагадування")
   .onAppear { model.refreshReminders() }
   .onDisappear { epoch = UUID(); preparing = false }
   .onChange(of: preview?.id) { _, value in if value == nil { epoch = UUID(); preparing = false } }
   .onChange(of: phase) { _, phase in if phase != .active { epoch = UUID(); preparing = false; share = nil } }
   .sheet(item: $preview) { row in
    NavigationStack { Form {
     Text(row.channel.title); LabeledContent("Одержувач", value: row.recipient); Text(row.message)
     Text("Одержувача потрібно вибрати самостійно. Копіювання або скасування залишає нагадування доступним.")
     Button("Відкрити поширення") { prepare(row.id, open: true) }.disabled(preparing).accessibilityIdentifier("reminder.share")
     if let message { Text(message) }
    }.navigationTitle("Перегляд").toolbar { Button("Закрити") { preview = nil }.accessibilityIdentifier("reminder.preview.close") }
     .sheet(item: $share) { current in ShareSheet(reminder: current) { outcome in
      share = nil
      switch outcome {
      case .retry: message = "Скасовано, скопійовано або непідтверджений застосунок. Можна повторити."
      case .error: record(current.id, state: .error)
      case .handoff: record(current.id, state: .handedToMessenger)
      }
     } }
    }
   }
 }
 private func prepare(_ id: UUID, open: Bool) {
  guard phase == .active else { return }
  preparing = true; let request = epoch
  model.prepareReminder(id) { result in
   guard request == epoch, phase == .active else { return }
   preparing = false
   switch result {
   case .success(let row):
    guard DevicePolicy.actionable(row, now: Date()), let appointment = model.appointments.first(where: { $0.id == row.appointmentID }), let start = Rules.date(day: appointment.day, minute: appointment.startMinute), start > Date() else { message = "Нагадування вже недоступне"; return }
    if open { share = row } else { preview = row }
   case .failure: message = "Нагадування змінилось або вже недоступне. Оновіть чергу."
   }
  }
 }
 private func record(_ id: UUID, state: ReminderState) {
  model.write({ try $0.setReminderState(id, state: state, error: state == .error ? "Не вдалося завершити поширення. Повторіть або скопіюйте текст." : nil) }) { error in
   message = error ?? (state == .handedToMessenger ? "Передано розширенню месенджера. Одержувач і доставка не підтверджені." : "Помилка поширення. Можна повторити.")
   if error == nil && state == .handedToMessenger { preview = nil }
  }
 }
 private func label(_ state: ReminderState) -> String {
  switch state { case .scheduled: return "Заплановано — очікує часу"; case .needsManualAction: return "Потрібна ручна дія"; case .error: return "Помилка — можна повторити"; case .cancelled: return "Скасовано"; case .handedToMessenger: return "Передано месенджеру. Доставку не підтверджено." }
 }
}
