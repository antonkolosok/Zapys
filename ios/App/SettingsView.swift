import SwiftUI
import ZapysCore
struct SettingsView: View {
 @EnvironmentObject var model: AppModel
 @State private var value = LocalProfile()
 @State private var loaded = false
 @State private var busy = false
 @State private var message: String?
 var body: some View {
  Form {
   Section("Нагадування") { NavigationLink("Черга та сповіщення") { RemindersView(coordinator: model.reminderCoordinator) }.accessibilityIdentifier("reminders.open") }
   Section("Локальний профіль") {
    VStack(alignment: .leading, spacing: 6) { Text("Ім’я").font(.subheadline); TextField("Ім’я", text: $value.name) }
    VStack(alignment: .leading, spacing: 6) { Text("Телефон").font(.subheadline); TextField("Телефон", text: $value.phone).keyboardType(.phonePad) }
    VStack(alignment: .leading, spacing: 6) { Text("Email").font(.subheadline); TextField("Email", text: $value.email).keyboardType(.emailAddress).textInputAutocapitalization(.never) }
    Text("Не підтверджено. Це локальний профіль, а не вхід у хмарний обліковий запис.").font(.caption)
    Button("Зберегти профіль") { busy = true; let saved = value; model.write({ var merged = saved; merged.notificationsEnabled = try $0.profile().notificationsEnabled; try $0.saveProfile(merged) }) { result in busy = false; message = result ?? "Профіль збережено" } }.disabled(busy || !loaded)
    if let message { Text(message) }
   }
   Section("Дані та сповіщення") {
    Text("Дані зберігаються на цьому пристрої. Хмарну синхронізацію ще не підключено.")
    Text("У черзі нагадувань можна дозволити сповіщення та вручну відкрити месенджер. Автоматичне надсилання не підключено.")
   }
  }.disabled(!loaded || busy).navigationTitle("Налаштування")
   .onAppear { if model.ready && !loaded { value = model.profile; loaded = true } }
   .onChange(of: model.ready) { _, ready in if ready && !loaded { value = model.profile; loaded = true } }
 }
}
