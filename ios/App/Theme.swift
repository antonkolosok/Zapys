import SwiftUI
enum Theme {
 static let background = Color(light: 0xF5F6F2, dark: 0x141B17)
 static let surface = Color(light: 0xFFFEFA, dark: 0x1C2520)
 static let ink = Color(light: 0x233A2C, dark: 0xE8F1E9)
 static let onPrimary = Color(light: 0xFFFEFA, dark: 0x172D20)
 static let divider = Color(light: 0xE4E9E0, dark: 0x344039)
 static let appointment = Color(light: 0xE8F1E5, dark: 0x2A3D30)
 static let draft = Color(light: 0xFBEFCF, dark: 0x443A22)
 static let primary = Color(light: 0x2F6047, dark: 0xB3DBC0)
 static let palette = ["#2F6047", "#B3DBC0", "#FBEFCF", "#AC3B36", "#4578A0", "#AA76B0"]
}
extension Color {
 init(hex: String) { self.init(rgb: UInt32(hex.dropFirst(), radix: 16) ?? 0x2F6047) }
 init(rgb: UInt32) { self.init(red: Double((rgb >> 16) & 255)/255, green: Double((rgb >> 8) & 255)/255, blue: Double(rgb & 255)/255) }
 init(light: UInt32, dark: UInt32) { self.init(uiColor: UIColor { $0.userInterfaceStyle == .dark ? UIColor(Color(rgb: dark)) : UIColor(Color(rgb: light)) }) }
}
struct ColorField: View {
 @Binding var value: String?
 var body: some View {
  Picker("Колір", selection: $value) {
   Text("Без кольору").tag(String?.none)
   ForEach(Theme.palette, id: \.self) { hex in Text(hex).tag(Optional(hex)) }
  }
  if let value { Label("Обраний колір", systemImage: "circle.fill").foregroundStyle(Color(hex: value)).accessibilityLabel("Обраний колір " + value) }
 }
}
struct NoteField: View {
 @Binding var text: String
 @FocusState.Binding var focusedField: String?
 var body: some View { VStack(alignment: .leading) {
  VStack(alignment: .leading, spacing: 6) { Text("Примітка").font(.subheadline); TextField("Примітка", text: $text, axis: .vertical).lineLimit(3...8).focused($focusedField, equals: "note") }
  Text("\(text.count) / 2000").font(.caption).foregroundStyle(text.count > 2000 ? .red : .secondary)
 }}
}
