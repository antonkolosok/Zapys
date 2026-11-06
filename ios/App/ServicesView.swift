import SwiftUI
import ZapysCore
struct ServicesView: View {
 @EnvironmentObject var model: AppModel
 @State private var search = ""
 var body: some View {
  List { ForEach(model.services.filter { search.isEmpty || $0.name.localizedCaseInsensitiveContains(search) }) { service in
   NavigationLink { ServiceForm(original: service) } label: { VStack(alignment: .leading) {
    Text(service.name)
    Text(service.durationMinutes.map { "\($0) хв" } ?? "Тривалість не вказана").font(.subheadline)
    Text(service.priceMinor.map { $0 == 0 ? "Безкоштовно" : FormValues.priceText($0) + " ₴" } ?? "Ціна не вказана").font(.subheadline).foregroundStyle(.secondary)
   }}
  }}.overlay { if model.services.isEmpty { ContentUnavailableView("Послуг ще немає", systemImage: "list.bullet") } }
   .searchable(text: $search, prompt: "Назва послуги").navigationTitle("Послуги")
   .toolbar { NavigationLink { ServiceForm(original: nil) } label: { Label("Додати послугу", systemImage: "plus") }.accessibilityIdentifier("service.add").disabled(!model.ready) }
 }
}
struct ServiceForm: View {
 @EnvironmentObject var model: AppModel
 @Environment(\.dismiss) private var dismiss
 let original: Service?
 @State private var value: Service
 @State private var duration: String
 @State private var price: String
 @State private var error: String?
 @State private var busy = false
 @State private var deleting = false
 @State private var discarding = false
 @FocusState private var focus: String?
 init(original: Service?) {
  self.original = original
  _value = State(initialValue: original ?? Service(name: ""))
  _duration = State(initialValue: original?.durationMinutes.map(String.init) ?? "")
  _price = State(initialValue: FormValues.priceText(original?.priceMinor))
 }
 private var changed: Bool {
  value != (original ?? Service(id: value.id, name: "")) || duration != (original?.durationMinutes.map(String.init) ?? "") || price != FormValues.priceText(original?.priceMinor)
 }
 var body: some View {
  Form {
   VStack(alignment: .leading, spacing: 6) { Text("Назва *").font(.subheadline); TextField("Назва *", text: $value.name).focused($focus, equals: "name") }
   VStack(alignment: .leading, spacing: 6) { Text("Тривалість, хв (необов’язково)").font(.subheadline); TextField("Тривалість, хв (необов’язково)", text: $duration).keyboardType(.numberPad).focused($focus, equals: "duration") }
   VStack(alignment: .leading, spacing: 6) { Text("Ціна, ₴ (необов’язково)").font(.subheadline); TextField("Ціна, ₴ (необов’язково)", text: $price).keyboardType(.decimalPad).focused($focus, equals: "price") }
   Text("Порожня ціна — невідома; 0 — безкоштовно.").font(.caption)
   NoteField(text: $value.note, focusedField: $focus); ColorField(value: $value.colorHex)
   if let error { Text(error).foregroundStyle(.red) }
   if original != nil { Button("Видалити послугу", role: .destructive) { deleting = true } }
  }.disabled(busy).navigationTitle(original == nil ? "Нова послуга" : "Послуга").navigationBarBackButtonHidden(true)
   .toolbar {
    ToolbarItem(placement: .cancellationAction) { Button("Назад") { if changed { discarding = true } else { dismiss() } }.disabled(busy) }
    ToolbarItem(placement: .confirmationAction) { Button("Зберегти") { save() }.disabled(busy) }
   }
   .confirmationDialog("Відкинути зміни?", isPresented: $discarding, titleVisibility: .visible) { Button("Відкинути", role: .destructive) { dismiss() }; Button("Продовжити редагування", role: .cancel) {} }
   .alert("Видалити \(value.name)?", isPresented: $deleting) { Button("Видалити", role: .destructive) { busy = true; model.write({ try $0.deleteService(value.id) }) { result in busy = false; if let result { error = result } else { dismiss() } } }; Button("Скасувати", role: .cancel) {} }
 }
 private func save() {
  do {
   value.name = value.name.trimmingCharacters(in: .whitespacesAndNewlines)
   focus = "name"
   guard !value.name.isEmpty else { throw ZapysError.validation("Вкажіть назву послуги") }
   focus = "duration"; value.durationMinutes = try FormValues.duration(duration)
   focus = "price"; value.priceMinor = try FormValues.price(price)
   focus = "note"; try Rules.note(value.note)
   try Rules.validate(value); focus = nil
   busy = true; let saved = value
   model.write({ try $0.saveService(saved) }) { result in busy = false; if let result { error = result } else { dismiss() } }
  } catch { self.error = error.localizedDescription }
 }
}
