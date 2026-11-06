import SwiftUI
import ZapysCore
struct ClientsView: View {
 @EnvironmentObject var model: AppModel
 @State private var search = ""
 var body: some View {
  List {
   ForEach(model.clients.filter { search.isEmpty || $0.displayName.localizedCaseInsensitiveContains(search) || $0.phone.contains(search) }) { client in
    NavigationLink { ClientForm(original: client) } label: { VStack(alignment: .leading) { Text(client.displayName); Text(client.phone).font(.subheadline).foregroundStyle(.secondary) } }
   }
  }.overlay { if model.clients.isEmpty { ContentUnavailableView("Клієнтів ще немає", systemImage: "person.2") } }
   .searchable(text: $search, prompt: "Ім’я або телефон").navigationTitle("Клієнти")
   .toolbar { NavigationLink { ClientForm(original: nil) } label: { Label("Додати клієнта", systemImage: "plus") }.accessibilityIdentifier("client.add").disabled(!model.ready) }
 }
}
struct ClientForm: View {
 @EnvironmentObject var model: AppModel
 @Environment(\.dismiss) private var dismiss
 let original: Client?
 @State private var value: Client
 @State private var error: String?
 @State private var busy = false
 @State private var duplicate = false
 @State private var pickingContact = false
 @State private var leadText: String
 @State private var deleting = false
 @State private var discarding = false
 @FocusState private var focus: String?
 init(original: Client?) { self.original = original; _value = State(initialValue: original ?? Client(name: "", phone: "")); _leadText = State(initialValue: String(original?.reminder.leadMinutes ?? 1440)) }
 private var changed: Bool { leadText != String(original?.reminder.leadMinutes ?? 1440) || value != (original ?? Client(id: value.id, name: "", phone: "")) }
 var body: some View {
  Form {
   Section("Контакти") {
    Button("Вибрати телефон із контактів") { pickingContact = true }
    Text("Заповнюються лише порожні ім’я та телефон. Введені дані зберігаються; їх можна змінити вручну.").font(.caption)
   }
   Section("Обов’язкове") {
    VStack(alignment: .leading, spacing: 6) { Text("Ім’я *").font(.subheadline); TextField("Ім’я *", text: $value.name).focused($focus, equals: "name").accessibilityIdentifier("client.name") }
    VStack(alignment: .leading, spacing: 6) { Text("Телефон *").font(.subheadline); TextField("Телефон *", text: $value.phone).keyboardType(.phonePad).focused($focus, equals: "phone").accessibilityIdentifier("client.phone") }
   }
   Section("Додатково") {
    VStack(alignment: .leading, spacing: 6) { Text("Прізвище").font(.subheadline); TextField("Прізвище", text: $value.surname) }
    VStack(alignment: .leading, spacing: 6) { Text("По батькові").font(.subheadline); TextField("По батькові", text: $value.patronymic) }
    NoteField(text: $value.note, focusedField: $focus); ColorField(value: $value.colorHex)
   }
   Section("Нагадування") {
    Toggle("Увімкнено", isOn: $value.reminder.enabled)
    Picker("Месенджер", selection: $value.reminder.channel) { ForEach(MessengerChannel.allCases) { Text($0.title).tag($0) } }
    VStack(alignment: .leading, spacing: 6) { Text("Одержувач").font(.subheadline); TextField("Одержувач", text: $value.reminder.recipient).focused($focus, equals: "recipient") }
    VStack(alignment: .leading) { Text("За скільки хвилин (1–525600)"); TextField("Хвилини", text: $leadText).keyboardType(.numberPad) }
    VStack(alignment: .leading, spacing: 6) { Text("Шаблон").font(.subheadline); TextField("Шаблон", text: $value.reminder.template, axis: .vertical).focused($focus, equals: "template") }
    Text("До 1 нагадування на запис. Автоматичне надсилання не підключено. Ви зможете самостійно відкрити підготовлене повідомлення.").font(.caption)
   }
   if let error { Section { Text(error).foregroundStyle(.red).accessibilityIdentifier("client.error") } }
   if let original { Section { NavigationLink("Історія записів") { HistoryView(clientID: original.id) } } }
   if original != nil { Section { Button("Видалити клієнта", role: .destructive) { deleting = true } } }
  }.disabled(busy).navigationTitle(original == nil ? "Новий клієнт" : "Клієнт")
   .sheet(isPresented: $pickingContact) { ContactPicker(selected: { name, phone in
    if value.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { value.name = name }
    if value.phone.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { value.phone = phone }
    pickingContact = false
   }, cancelled: { pickingContact = false }) }
   .navigationBarBackButtonHidden(true)
   .toolbar {
    ToolbarItem(placement: .cancellationAction) { Button("Назад") { if changed { discarding = true } else { dismiss() } }.disabled(busy) }
    ToolbarItem(placement: .confirmationAction) { Button("Зберегти") { save() }.disabled(busy).accessibilityIdentifier("client.save") }
   }
   .confirmationDialog("Відкинути зміни?", isPresented: $discarding, titleVisibility: .visible) { Button("Відкинути", role: .destructive) { dismiss() }; Button("Продовжити редагування", role: .cancel) {} }
   .alert("Такий телефон уже є", isPresented: $duplicate) { Button("Зберегти окремого клієнта") { persist() }; Button("Скасувати", role: .cancel) {} } message: { Text("Буде створено окремий запис. Дані інших клієнтів не зміняться.") }
   .alert("Видалити \(value.displayName)?", isPresented: $deleting) { Button("Видалити", role: .destructive) { busy = true; model.write({ try $0.deleteClient(value.id) }) { result in busy = false; if let result { error = result } else { dismiss() } } }; Button("Скасувати", role: .cancel) {} }
 }
 private func save() {
  do {
   focus = value.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "name" : "phone"
   let validated = try FormValues.client(name: value.name, phone: value.phone)
   value.name = validated.name; value.phone = validated.phone
   focus = "note"; try Rules.note(value.note)
   focus = "recipient"
   if value.reminder.enabled && value.reminder.recipient.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
    throw ZapysError.validation("Вкажіть одержувача нагадування")
   }
   focus = "template"; try Rules.note(value.reminder.template)
   guard let lead = Int(leadText.trimmingCharacters(in: .whitespacesAndNewlines)), ((value.reminder.enabled ? 1 : 0)...525600).contains(lead) else { throw ZapysError.validation("Укажіть цілу кількість хвилин: 1–525600 для увімкнених нагадувань") }
   value.reminder.leadMinutes = lead
   try Rules.validate(value)
   focus = nil
   if model.clients.contains(where: { $0.id != value.id && FormValues.phoneKey($0.phone) == FormValues.phoneKey(value.phone) }) { duplicate = true } else { persist() }
  } catch { self.error = error.localizedDescription }
 }
 private func persist() { busy = true; let saved = value; model.write({ try $0.saveClient(saved) }) { result in busy = false; if let result { error = result } else { dismiss() } } }
}

