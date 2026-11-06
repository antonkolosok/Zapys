import SwiftUI
import UIKit
import ZapysCore

struct ShareSheet: UIViewControllerRepresentable {
 let reminder: Reminder
 let completion: (ShareOutcome) -> Void
 func makeUIViewController(context: Context) -> UIActivityViewController {
  let controller = UIActivityViewController(activityItems: [reminder.message], applicationActivities: nil)
  controller.completionWithItemsHandler = { activity, completed, _, error in
   DispatchQueue.main.async { completion(DevicePolicy.shareOutcome(completed: completed, activity: activity?.rawValue, failed: error != nil)) }
  }
  return controller
 }
 func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
