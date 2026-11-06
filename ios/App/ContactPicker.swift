import SwiftUI
import ContactsUI

/// Select one phone property. No CNContactStore enumeration or permission request.
struct ContactPicker: UIViewControllerRepresentable {
 var selected: (String, String) -> Void
 var cancelled: () -> Void
 func makeCoordinator() -> Coordinator { Coordinator(self) }
 func makeUIViewController(context: Context) -> CNContactPickerViewController {
  let picker = CNContactPickerViewController()
  picker.delegate = context.coordinator
  picker.displayedPropertyKeys = [CNContactPhoneNumbersKey]
  picker.predicateForEnablingContact = NSPredicate(format: "phoneNumbers.@count > 0")
  picker.predicateForSelectionOfContact = NSPredicate(value: false)
  picker.predicateForSelectionOfProperty = NSPredicate(format: "key == 'phoneNumbers'")
  return picker
 }
 func updateUIViewController(_ controller: CNContactPickerViewController, context: Context) {}
 final class Coordinator: NSObject, CNContactPickerDelegate {
  let owner: ContactPicker
  init(_ owner: ContactPicker) { self.owner = owner }
  func contactPickerDidCancel(_ picker: CNContactPickerViewController) { owner.cancelled() }
  func contactPicker(_ picker: CNContactPickerViewController, didSelect property: CNContactProperty) {
   guard let phone = property.value as? CNPhoneNumber else { owner.cancelled(); return }
   owner.selected(CNContactFormatter.string(from: property.contact, style: .fullName) ?? "", phone.stringValue)
  }
 }
}
