import SwiftUI
import ZapysCore
struct HistoryView: View {
 @EnvironmentObject var model: AppModel
 let clientID: UUID
 @State private var now = Date()
 var body: some View {
  let groups = AppointmentHistory.split(model.appointments.filter { $0.clientID == clientID }, now: now)
  List { section("Майбутні записи", groups.upcoming); section("Минулі записи", groups.past) }.navigationTitle("Історія записів").onAppear { now = Date() }
 }
 private func section(_ title: String, _ values: [Appointment]) -> some View {
  Section(title) {
   if values.isEmpty { Text("Записів ще немає") }
   ForEach(values) { value in NavigationLink { AppointmentEditor(appointment: value) } label: { AppointmentRow(value: value) } }
  }
 }
}
struct AppointmentRow: View {
 let value: Appointment
 var body: some View {
  VStack(alignment: .leading, spacing: 6) {
   Text(value.clientNameSnapshot ?? "Чернетка").font(.headline)
   Text("\(value.day) · \(Rules.time(value.startMinute))–\(Rules.time(value.endMinute))")
   ForEach(value.services) { Text($0.name) }
   Text(value.isDraft ? "Чернетка" : status).font(.caption)
   Text(value.paid ? "Оплачено" : "Не оплачено").font(.caption)
   if !value.note.isEmpty { Text(value.note).font(.subheadline) }
  }.accessibilityElement(children: .combine)
 }
 private var status: String { switch value.status { case .planned: return "Заплановано"; case .completed: return "Виконано"; case .cancelled: return "Скасовано"; case .noShow: return "Не з’явився / не з’явилася" } }
}
