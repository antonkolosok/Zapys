import SwiftUI
import UIKit
import ZapysCore

struct CalendarEntryView: View {
 @EnvironmentObject var model: AppModel
 @State private var day = Rules.day(Date())
 @State private var week = false
 @State private var agenda = false
 @State private var selected: UUID?
 @State private var editor: Appointment?
 @State private var preview: Appointment?
 @State private var pending: Appointment?
 @State private var conflict = false
 @State private var confirmingResize = false
 @State private var endForm: Appointment?
 @State private var busy = false
 @State private var error: String?
 @State private var notice: String?
 private var days: [String] { week ? CalendarGeometry.weekDays(day) : [day] }
 private var visible: [Appointment] { model.appointments.filter { days.contains($0.day) }.sorted { ($0.day,$0.startMinute) < ($1.day,$1.startMinute) } }
 var body: some View {
  VStack(spacing: 8) {
   HStack {
    Button { day = CalendarGeometry.navigate(day, weeks: -1) } label: { Image(systemName: "chevron.left") }.accessibilityLabel("Попередній тиждень")
    Spacer(); Text(String(day.prefix(7))).font(.headline); Spacer()
    Button("Сьогодні") { day = Rules.day(Date()) }
    Button { day = CalendarGeometry.navigate(day, weeks: 1) } label: { Image(systemName: "chevron.right") }.accessibilityLabel("Наступний тиждень")
   }.padding(.horizontal, 12)
   Picker("Вигляд", selection: $week) { Text("День").tag(false); Text("Тиждень").tag(true) }.pickerStyle(.segmented).padding(.horizontal, 12)
   HStack(spacing: 0) {
    Color.clear.frame(width: CalendarGeometry.gutter)
    ForEach(CalendarGeometry.weekDays(day), id: \.self) { date in
     Button { day = date; week = false } label: {
      VStack(spacing: 2) { Text(weekday(date)); Text(String(date.suffix(2))) }.font(.caption).frame(maxWidth: .infinity).padding(.vertical, 4)
       .background(date == day ? Theme.primary : Theme.surface).foregroundStyle(date == day ? Theme.onPrimary : Theme.ink)
       .overlay(RoundedRectangle(cornerRadius: 6).stroke(date == Rules.day(Date()) ? Theme.primary : .clear))
     }.accessibilityLabel(CalendarPresentation.dayHeaderLabel(date, selected: date == day, today: date == Rules.day(Date())))
      .accessibilityAddTraits(date == day ? .isSelected : [])
      .accessibilityIdentifier("calendar.header." + date)
    }
   }
   Toggle("Список записів", isOn: $agenda).padding(.horizontal, 12).accessibilityIdentifier("calendar.agenda")
   if let current = preview ?? model.appointments.first(where: { $0.id == selected }) {
    Text("\(current.day) · \(Rules.time(current.startMinute))–\(Rules.time(current.endMinute)) · Утримуйте нижній край, потім тягніть")
     .font(.caption).padding(.horizontal, 12).accessibilityIdentifier("calendar.selectedTime")
   }
   if let loadingError = model.error { Text(loadingError).foregroundStyle(.red) }
   if let error { Text(error).foregroundStyle(.red) }
   if let notice { Text(notice).font(.caption).accessibilityIdentifier("calendar.notice") }
   if !model.ready { ProgressView("Завантаження") }
   if agenda { agendaList } else { timeline }
  }.background(Theme.background).navigationTitle("zapys")
   .toolbar { NavigationLink { AppointmentEditor() } label: { Label("Новий запис", systemImage: "plus") }.disabled(!model.ready || busy).accessibilityIdentifier("appointment.add") }
   .navigationDestination(isPresented: Binding(get: { editor != nil }, set: { if !$0 { editor = nil } })) { if let editor { AppointmentEditor(appointment: editor) } }
   .sheet(item: $endForm) { value in CalendarEndForm(value: value) { candidate in endForm = nil; proposeResize(candidate) } }
   .alert("Змінити час закінчення?", isPresented: $confirmingResize) {
    Button("Зберегти") { if let pending { persist(pending) } }
    Button("Скасувати", role: .cancel) { pending = nil; preview = nil }
   } message: { Text(pending.map { Rules.time($0.startMinute) + "–" + Rules.time($0.endMinute) } ?? "") }
   .alert("Цей час перетинається з іншим записом", isPresented: $conflict) {
    Button("Зберегти попри перетин") { if let pending { persist(pending, allowOverlap: true) } }
    Button("Скасувати", role: .cancel) { pending = nil; preview = nil }
   } message: { Text(conflicts) }
   .onDisappear { preview = nil }
 }
 private var agendaList: some View {
  List {
   if visible.isEmpty { Text("Записів поки немає") }
   ForEach(visible) { value in
    VStack(alignment: .leading) {
     Button { open(value) } label: {
      VStack(alignment: .leading) {
       Text(value.clientNameSnapshot ?? "Без клієнта").font(.headline)
       Text(value.day + " · " + Rules.time(value.startMinute) + "–" + Rules.time(value.endMinute))
       Text(CalendarPresentation.status(value))
       ForEach(value.services) { Text($0.name) }
      }
     }.buttonStyle(.plain).accessibilityElement(children: .ignore)
      .accessibilityLabel(CalendarPresentation.appointmentLabel(value)).accessibilityIdentifier("calendar.agenda.edit")
     Button("Змінити час закінчення") { selected = value.id; endForm = value }.buttonStyle(.borderless)
    }
   }
  }.disabled(busy)
 }
 private var timeline: some View {
  GeometryReader { geometry in
   ScrollViewReader { scroll in
    ScrollView(.vertical) {
     ZStack(alignment: .topLeading) {
      ForEach(0..<24, id: \.self) { hour in
       HStack(spacing: 0) {
        Text(String(format: "%02d:00", hour)).font(.system(size: 11)).monospacedDigit().frame(width: CalendarGeometry.gutter)
        Rectangle().fill(Theme.divider).frame(height: 0.5)
       }.offset(y: CalendarGeometry.top(hour * 60)).id(hour)
      }
      HStack(spacing: 0) {
       Color.clear.frame(width: CalendarGeometry.gutter)
       ForEach(days, id: \.self) { date in dayColumn(date, width: max(1, (geometry.size.width - CalendarGeometry.gutter) / Double(days.count))) }
      }
     }.frame(height: CalendarGeometry.top(1440), alignment: .top)
   }.onAppear { scroll.scrollTo(9, anchor: .top) }
   }
  }
 }
 private func dayColumn(_ date: String, width: Double) -> some View {
  let records = visible.filter { $0.day == date }.map { value in preview?.id == value.id ? preview! : value }
  return ZStack(alignment: .topLeading) {
   Rectangle().fill(Color.clear).contentShape(Rectangle())
    .onTapGesture { location in guard model.ready && !busy else { return }; persist(CalendarGeometry.draft(day: date, minute: location.y / CalendarGeometry.scale)) }
   Rectangle().fill(Theme.divider).frame(width: 0.5).allowsHitTesting(false)
   ForEach(0..<24, id: \.self) { hour in Rectangle().fill(Theme.divider.opacity(0.5)).frame(height: 0.5).offset(y: CalendarGeometry.top(hour * 60 + 30)).allowsHitTesting(false) }
   ForEach(CalendarGeometry.lanes(records), id: \.appointment.id) { lane in
    let original = model.appointments.first { $0.id == lane.appointment.id } ?? lane.appointment
    AppointmentBlock(value: lane.appointment, selected: selected == original.id, week: week,
     open: { open(original) },
     preview: { delta in preview = delta.map { CalendarGeometry.resize(original, minute: Double(original.endMinute) + $0 / CalendarGeometry.scale) } },
     finish: { delta in proposeResize(CalendarGeometry.resize(original, minute: Double(original.endMinute) + delta / CalendarGeometry.scale)) })
     .frame(width: max(1, width / Double(lane.laneCount) - 2))
     .offset(x: Double(lane.lane) * width / Double(lane.laneCount) + 1, y: CalendarGeometry.top(lane.appointment.startMinute))
     .contextMenu { Button("Змінити час закінчення") { selected = original.id; endForm = original }; Button("Вибрати для розтягування") { selected = original.id } }
     .accessibilityAction(named: "Змінити час закінчення") { selected = original.id; endForm = original }
   }
  }.frame(width: width, height: CalendarGeometry.top(1440)).disabled(busy).accessibilityIdentifier("calendar.day." + date)
 }
 private var conflicts: String { model.appointments.filter { a in pending.map { Rules.overlaps(a,$0) } ?? false }.map { ($0.clientNameSnapshot ?? "Чернетка") + " " + Rules.time($0.startMinute) + "–" + Rules.time($0.endMinute) }.joined(separator: "\n") }
 private func weekday(_ date: String) -> String { let f = DateFormatter(); f.locale = Locale(identifier: "uk_UA"); f.dateFormat = "EE"; return f.string(from: Rules.date(day: date, minute: 720) ?? Date()) }
 private func open(_ value: Appointment) { selected = value.id; editor = value }
 private func proposeResize(_ value: Appointment) { preview = nil; pending = value; confirmingResize = true }
 private func persist(_ value: Appointment, allowOverlap: Bool = false) {
  busy = true; error = nil; pending = value
  model.writeTyped({ try $0.saveAppointment(value, allowOverlap: allowOverlap) }) { result in
   busy = false
   switch result {
   case .success:
    pending = nil; preview = nil; selected = value.id
    notice = "Збережено: " + value.day + " " + Rules.time(value.startMinute) + "–" + Rules.time(value.endMinute)
    UIAccessibility.post(notification: .announcement, argument: notice)
   case .failure(let failure):
    if let typed = failure as? ZapysError, case .overlap = typed { conflict = true }
    else { error = failure.localizedDescription; pending = nil; preview = nil }
   }
  }
 }
}

private struct CalendarEndForm: View {
 let value: Appointment
 let save: (Appointment) -> Void
 @Environment(\.dismiss) private var dismiss
 @State private var end: Date
 init(value: Appointment, save: @escaping (Appointment) -> Void) { self.value = value; self.save = save; _end = State(initialValue: Rules.date(day: value.day, minute: value.endMinute) ?? Date()) }
 var body: some View {
  NavigationStack {
   Form {
    Text("Початок: " + Rules.time(value.startMinute))
    DatePicker("Час закінчення", selection: $end, displayedComponents: .hourAndMinute)
    Text("Календар округлює закінчення до 15 хвилин; найпізніше — 23:59.")
    Button("Переглянути зміну") { let c = Calendar.current.dateComponents([.hour,.minute], from: end); save(CalendarGeometry.resize(value, minute: Double((c.hour ?? 0) * 60 + (c.minute ?? 0)))) }
   }.navigationTitle("Час закінчення").toolbar { Button("Скасувати") { dismiss() } }
  }.environment(\.locale, Locale(identifier: "uk_UA"))
 }
}
