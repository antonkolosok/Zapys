import SwiftUI
import UIKit
import ZapysCore

struct AppointmentBlock: View {
 let value: Appointment
 let selected: Bool
 let week: Bool
 let open: () -> Void
 let preview: (Double?) -> Void
 let finish: (Double) -> Void
 @ScaledMetric(relativeTo: .caption) private var rowHeight = 22.0
 var body: some View {
  let height = CalendarGeometry.height(start: value.startMinute, end: value.endMinute)
  let rows = CalendarPresentation.rows(value, height: height, rowHeight: rowHeight, week: week)
  let colors = [value.clientColorSnapshot] + value.services.map(\.colorHex)
  let centers = rows.labelCenters(rowHeight: rowHeight)
  let stops = CalendarGeometry.gradient(colors: colors, rowCenters: centers)
  // Draw over the logical content span, then clip to true duration. Never clamp hidden anchors.
  let span = max(height, centers.last ?? height)
  ZStack(alignment: .topLeading) {
   if stops.count > 1 {
    LinearGradient(stops: stops.map { .init(color: Color(hex: $0.colorHex), location: $0.center / span) }, startPoint: .top, endPoint: .bottom).frame(height: span)
   } else if let stop = stops.first { Color(hex: stop.colorHex) }
   else { value.isDraft ? Theme.draft : Theme.appointment }
   VStack(alignment: .leading, spacing: 0) {
    ForEach(Array(rows.labels.enumerated()), id: \.offset) { index, label in
     Group {
      if index == 0, let time = rows.compactTime {
       // Two compact lines inside ONE logical row: service centers and event height stay fixed.
       VStack(alignment: .leading, spacing: 0) {
        Text(time).monospacedDigit().frame(height: rowHeight / 2)
        Text(label).frame(height: rowHeight / 2)
       }.font(.caption2).lineLimit(1).minimumScaleFactor(0.7)
      } else { Text(label).font(.caption).lineLimit(1) }
     }.foregroundStyle(Theme.ink)
      .frame(maxWidth: .infinity, alignment: .leading).frame(height: rowHeight).background(Theme.surface)
    }
    if let metadata = rows.metadata { Text(metadata).font(.caption2).lineLimit(1).foregroundStyle(Theme.ink).frame(height: rowHeight).background(Theme.surface) }
   }.padding(.top, 2).padding(.leading, 5).padding(.trailing, 2)
  }.frame(height: height, alignment: .top).clipped().clipShape(RoundedRectangle(cornerRadius: 6))
   .overlay(RoundedRectangle(cornerRadius: 6).stroke(selected ? Theme.primary : Theme.divider, lineWidth: selected ? 2 : 0.5))
   .contentShape(Rectangle()).onTapGesture(perform: open)
   .overlay(alignment: .bottom) {
    if selected {
     ResizeGrip(open: open, preview: preview, finish: finish)
      .frame(height: min(16, height)).overlay(alignment: .bottom) { Capsule().fill(Theme.ink).frame(width: 18, height: 3).padding(.bottom, 2).allowsHitTesting(false) }
      .accessibilityHidden(true)
    }
   }
   .accessibilityElement(children: .ignore)
   .accessibilityIdentifier("calendar.block." + value.id.uuidString)
   .accessibilityLabel(CalendarPresentation.appointmentLabel(value))
   .accessibilityAddTraits(.isButton).accessibilityAction(perform: open)
 }
}

/// A stationary hold arms resizing; ordinary vertical scrolling wins before that hold.
/// Tap remains a tap, and UIKit cancellation never commits a preview.
private struct ResizeGrip: UIViewRepresentable {
 let open: () -> Void
 let preview: (Double?) -> Void
 let finish: (Double) -> Void
 func makeCoordinator() -> Coordinator { Coordinator(self) }
 func makeUIView(context: Context) -> UIView {
  let view = UIView(); view.backgroundColor = .clear
  let hold = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.hold(_:)))
  hold.minimumPressDuration = 0.35; hold.allowableMovement = 8
  let tap = UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tap))
  tap.require(toFail: hold)
  view.addGestureRecognizer(hold); view.addGestureRecognizer(tap)
  return view
 }
 func updateUIView(_ uiView: UIView, context: Context) { context.coordinator.parent = self }
 final class Coordinator: NSObject {
  var parent: ResizeGrip
  var origin: CGFloat = 0
  var delta: Double?
  init(_ parent: ResizeGrip) { self.parent = parent }
  @objc func tap() { parent.open() }
  @objc func hold(_ recognizer: UILongPressGestureRecognizer) {
   // Use the window: resizing moves the grip itself and must not feed back into translation.
   let y = recognizer.location(in: recognizer.view?.window).y
   switch recognizer.state {
   case .began: origin = y; delta = nil
   case .changed:
    let movement = Double(y - origin)
    if abs(movement) >= 8 || delta != nil { delta = movement; parent.preview(movement) }
   case .ended:
    if let delta { parent.finish(delta) }
    parent.preview(nil); delta = nil
   case .cancelled, .failed: parent.preview(nil); delta = nil
   default: break
   }
  }
 }
}
