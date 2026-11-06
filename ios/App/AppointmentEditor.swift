import SwiftUI
import ZapysCore

struct AppointmentEditor: View {
 @EnvironmentObject var model: AppModel
 @Environment(\.dismiss) private var dismiss
 @State private var draft: AppointmentDraft
 private let baseline: AppointmentDraft
 @State private var busy = false
 @State private var error: String?
 @State private var overlap: Appointment?
 @State private var back = false
 @State private var deleting = false
 @State private var duplicate = false
 @State private var pending: Appointment?
 @State private var pendingClient: Client?
 @FocusState private var focus: String?
 @AccessibilityFocusState private var accessibleTimeFocus: AppointmentDraft.TimeField?
 @State private var invalidTime: AppointmentDraft.TimeValidationIssue?
 @State private var validationAttempt = 0
 init(appointment: Appointment? = nil, repeating: Bool = false) {
  let value = repeating ? AppointmentDraft.repeated(appointment!) : AppointmentDraft(appointment: appointment)
  baseline = value; _draft = State(initialValue: value)
 }
 private func dateBinding(_ minute: Bool, end: Bool = false) -> Binding<Date> {
  Binding(get: { Rules.date(day: draft.appointment.day, minute: minute ? (end ? draft.appointment.endMinute : draft.appointment.startMinute) : 0) ?? Date() }, set: { date in
   if minute {
    let c = Calendar.current.dateComponents([.hour, .minute], from: date); let value = (c.hour ?? 0) * 60 + (c.minute ?? 0)
    if end { draft.appointment.endMinute = value; draft.endProvided = true; draft.appointment.endManuallySet = true }
    else { draft.appointment.startMinute = value; draft.startProvided = true; draft.applySuggestion() }
   } else { draft.appointment.day = Rules.day(date); draft.dateProvided = true }
  })
 }
 var body: some View {
  ScrollViewReader { scroll in
  Form {
   Section("Дата й час") {
    if !draft.dateProvided { Button("Обрати нову дату *") { draft.dateProvided = true } }
    DatePicker("Дата *", selection: dateBinding(false), displayedComponents: .date).id("time.date").focused($focus, equals: "date").accessibilityFocused($accessibleTimeFocus, equals: .date)
    if !draft.startProvided { Button("Обрати новий час початку *") { draft.startProvided = true } }
    DatePicker("Початок *", selection: dateBinding(true), displayedComponents: .hourAndMinute).id("time.start").focused($focus, equals: "start").accessibilityFocused($accessibleTimeFocus, equals: .start)
    if draft.endProvided { DatePicker("Закінчення *", selection: dateBinding(true, end: true), displayedComponents: .hourAndMinute).id("time.end").focused($focus, equals: "end").accessibilityFocused($accessibleTimeFocus, equals: .end) }
    else { Button("Укажіть закінчення *") { draft.endProvided = true; draft.appointment.endManuallySet = true }.id("time.end").focused($focus, equals: "end").accessibilityFocused($accessibleTimeFocus, equals: .end) }
    if let invalidTime { Text(invalidTime.message).foregroundStyle(.red).accessibilityIdentifier("appointment.time.error") }
    if let end = draft.suggestion { Button("За тривалістю послуг: \(Rules.time(end))") { draft.applySuggestion(force: true) } }
   }
   Section("Клієнт *") {
    Picker("Клієнт", selection: Binding(get: { draft.appointment.clientID }, set: { id in draft.selectClient(model.clients.first { $0.id == id }) })) {
     Text("Без клієнта (чернетка)").tag(nil as UUID?)
     ForEach(model.clients) { Text($0.displayName).tag(Optional($0.id)) }
    }.focused($focus, equals: "client")
    field("Ім’я нового клієнта", text: $draft.inlineName, key: "name")
    field("Телефон нового клієнта", text: $draft.inlinePhone, key: "phone").keyboardType(.phonePad)
    Text("Заповніть обидва поля, щоб створити нового клієнта разом із записом.").font(.caption)
   }
   Section("Послуги \(draft.appointment.services.count) / 5") {
    ForEach(Array(draft.appointment.services.enumerated()), id: \.element.id) { index, service in
     VStack(alignment: .leading) {
      Text("\(index + 1). \(service.name)").font(.headline)
      field("Тривалість, хв", text: raw(service.id, duration: true), key: service.id.uuidString + "duration").keyboardType(.numberPad)
      field("Ціна, ₴ (порожньо — невідома)", text: raw(service.id, duration: false), key: service.id.uuidString + "price").keyboardType(.decimalPad)
      if draft.priceText[service.id] == "" { Text("Ціна невідома").font(.caption) } else if (try? FormValues.price(draft.priceText[service.id] ?? "")) == 0 { Text("Безкоштовно").font(.caption) }
      if !service.note.isEmpty { Text(service.note).font(.caption) }
      Button("Вилучити \(service.name)", role: .destructive) { draft.removeService(at: index) }
     }
    }
    Menu("Додати послугу") { ForEach(model.services) { service in Button(service.name) { do { try draft.addService(service) } catch { self.error = error.localizedDescription } } } }.disabled(draft.appointment.services.count >= 5)
    if draft.appointment.services.count == 5 { Text("У записі вже 5 послуг").font(.caption) }
    Text("Разом: \(draft.totalText)")
   }
   Section("Запис") {
    field("Нотатка", text: $draft.appointment.note, key: "note")
    Text("\(draft.appointment.note.count) / 2000").font(.caption)
    Picker("Статус", selection: $draft.appointment.status) { Text("Заплановано").tag(AppointmentStatus.planned); Text("Виконано").tag(AppointmentStatus.completed); Text("Скасовано").tag(AppointmentStatus.cancelled); Text("Не з’явився / не з’явилася").tag(AppointmentStatus.noShow) }
    Toggle("Оплачено", isOn: $draft.appointment.paid)
    field("Отримана сума, ₴", text: $draft.receivedText, key: "received").keyboardType(.decimalPad)
    Text(draft.appointment.isDraft ? "Чернетка" : "Запис").font(.caption)
   }
   Section("Нагадування") { Text("Параметри беруться з картки клієнта. Автоматичне надсилання не підключено.").font(.caption) }
   if let error { Section { Text(error).foregroundStyle(.red).accessibilityIdentifier("appointment.error") } }
   Section {
    Button("Зберегти запис") { save(isDraft: false) }.accessibilityIdentifier("appointment.save")
    Button("Зберегти чернетку") { save(isDraft: true) }.accessibilityIdentifier("appointment.draft")
    if model.appointments.contains(where: { $0.id == draft.appointment.id }) {
     NavigationLink("Повторити запис") { AppointmentEditor(appointment: draft.appointment, repeating: true) }
     Button("Видалити запис", role: .destructive) { deleting = true }
    }
   }
  }.onChange(of: validationAttempt) { _, _ in
   if let field = invalidTime?.field {
    withAnimation { scroll.scrollTo("time." + field.rawValue, anchor: .center) }
    DispatchQueue.main.async { focus = field.rawValue; accessibleTimeFocus = field }
   }
  }
  }.environment(\.locale, Locale(identifier: "uk_UA")).disabled(busy)
   .navigationTitle("Запис").navigationBarBackButtonHidden(true)
   .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Назад") { if draft != baseline { back = true } else { dismiss() } }.disabled(busy) } }
   .confirmationDialog("Зберегти зміни?", isPresented: $back, titleVisibility: .visible) { Button("Зберегти чернетку") { save(isDraft: true) }; Button("Відкинути", role: .destructive) { dismiss() }; Button("Продовжити редагування", role: .cancel) {} }
   .alert("Час перетинається з іншим записом", isPresented: Binding(get: { overlap != nil }, set: { if !$0 { overlap = nil } })) { Button("Зберегти попри перетин") { if let value = overlap { overlap = nil; persist(value, allowOverlap: true) } }; Button("Скасувати", role: .cancel) { overlap = nil } } message: { Text(conflictText) }
   .alert("Такий телефон уже є", isPresented: $duplicate) { Button("Створити окремого клієнта") { persistClientThenAppointment() }; Button("Скасувати", role: .cancel) {} } message: { Text("Однаковий телефон не об’єднує різних клієнтів.") }
   .alert("Видалити цей запис?", isPresented: $deleting) { Button("Видалити", role: .destructive) { busy = true; model.writeTyped({ try $0.deleteAppointment(draft.appointment.id) }) { result in busy = false; switch result { case .success: dismiss(); case .failure(let e): error = e.localizedDescription } } }; Button("Скасувати", role: .cancel) {} }
 }
 private var conflictText: String { model.appointments.filter { a in overlap.map { Rules.overlaps(a, $0) } ?? false }.map { "\($0.clientNameSnapshot ?? "Чернетка") \(Rules.time($0.startMinute))–\(Rules.time($0.endMinute))" }.joined(separator: "\n") }
 private func field(_ label: String, text: Binding<String>, key: String) -> some View { VStack(alignment: .leading) { Text(label).font(.subheadline); TextField(label, text: text, axis: .vertical).focused($focus, equals: key) } }
 private func raw(_ id: UUID, duration: Bool) -> Binding<String> { Binding(get: { (duration ? draft.durationText[id] : draft.priceText[id]) ?? "" }, set: { value in if duration { draft.durationText[id] = value } else { draft.priceText[id] = value }; draft.applySuggestion() }) }
 private func save(isDraft: Bool) {
  do {
   invalidTime = draft.timeValidationIssue
   if let issue = invalidTime { validationAttempt += 1; focus = issue.field.rawValue; throw ZapysError.validation(issue.message) }
   focus = "name"; if !draft.inlineName.isEmpty || !draft.inlinePhone.isEmpty { focus = draft.inlineName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "name" : "phone" }
   let client = try draft.prepareInlineClient()
   if !isDraft && draft.appointment.clientID == nil && client == nil { focus = "client"; throw ZapysError.validation("Оберіть клієнта") }
   for service in draft.appointment.services { focus = service.id.uuidString + "duration"; _ = try FormValues.duration(draft.durationText[service.id] ?? ""); focus = service.id.uuidString + "price"; _ = try FormValues.price(draft.priceText[service.id] ?? "") }
   focus = "received"; _ = try FormValues.price(draft.receivedText); focus = "note"; try Rules.note(draft.appointment.note)
   pending = try draft.prepared(isDraft: isDraft); pendingClient = client; focus = nil
   if let client, model.clients.contains(where: { $0.id != client.id && FormValues.phoneKey($0.phone) == FormValues.phoneKey(client.phone) }) { duplicate = true } else { persistClientThenAppointment() }
  } catch { self.error = error.localizedDescription }
 }
 private func persistClientThenAppointment() {
  guard let value = pending else { return }
  guard let client = pendingClient else { persist(value); return }
  busy = true
  model.writeTyped({ try $0.saveClient(client) }) { result in
   busy = false
   switch result { case .success: draft.inlineClientSaved(client); pendingClient = nil; persist(value)
   case .failure(let e): error = e.localizedDescription }
  }
 }
 private func persist(_ value: Appointment, allowOverlap: Bool = false) {
  busy = true
  model.writeTyped({ try $0.saveAppointment(value, allowOverlap: allowOverlap) }) { result in
   busy = false
   switch result { case .success: dismiss()
   case .failure(let e): if let typed = e as? ZapysError, case .overlap = typed { overlap = value } else { error = e.localizedDescription } }
  }
 }
}



