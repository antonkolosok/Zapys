import SwiftUI
import ZapysCore
@main struct ZapysApp: App {
 @StateObject private var model = AppModel()
 @Environment(\.scenePhase) private var phase
 var body: some Scene { WindowGroup {
  TabView {
   NavigationStack { CalendarEntryView() }.tabItem { Label("Календар", systemImage: "calendar") }.accessibilityIdentifier("tab.calendar")
   NavigationStack { ClientsView() }.tabItem { Label("Клієнти", systemImage: "person.2") }.accessibilityIdentifier("tab.clients")
   NavigationStack { ServicesView() }.tabItem { Label("Послуги", systemImage: "list.bullet") }.accessibilityIdentifier("tab.services")
   NavigationStack { SettingsView() }.tabItem { Label("Налаштування", systemImage: "gearshape") }.accessibilityIdentifier("tab.settings")
  }.tint(Theme.primary).environmentObject(model)
   .onChange(of: phase) { _, value in if value == .active { model.refreshReminders() } }
   .onReceive(NotificationCenter.default.publisher(for: UIApplication.significantTimeChangeNotification)) { _ in model.refreshReminders() }
   .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name.NSSystemTimeZoneDidChange)) { _ in model.refreshReminders() }
 }}
}
