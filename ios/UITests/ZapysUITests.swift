import XCTest
final class ZapysUITests: XCTestCase {
 func testReminderPrepareAndCancelAreIndependentRowActions() {
  let app = XCUIApplication()
  app.launchArguments = ["--ui-testing-temporary-database", "--ui-testing-reminder-actions"]
  app.launch()
  app.tabBars.buttons.element(boundBy: 3).tap()
  let queue = app.buttons["reminders.open"]
  XCTAssertTrue(queue.waitForExistence(timeout: 10))
  let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true AND hittable == true"), object: queue)
  XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 10), .completed); queue.tap()
  let prepare = app.buttons["reminder.prepare"].firstMatch
  XCTAssertTrue(prepare.waitForExistence(timeout: 10))
  let initialCount = app.buttons.matching(identifier: "reminder.prepare").count
  XCTAssertEqual(initialCount, 2); prepare.tap()
  XCTAssertTrue(app.buttons["reminder.share"].waitForExistence(timeout: 5))
  app.buttons["reminder.preview.close"].tap()
  // Prepare must not have cancelled this row; the action remains available.
  XCTAssertTrue(prepare.waitForExistence(timeout: 5))
  let count = app.buttons.matching(identifier: "reminder.prepare").count
  XCTAssertEqual(count, initialCount, "Prepare must not cancel either row")
  app.buttons["reminder.cancel"].firstMatch.tap()
  let remaining = XCTNSPredicateExpectation(predicate: NSPredicate(format: "count == %d", count - 1), object: app.buttons.matching(identifier: "reminder.prepare"))
  XCTAssertEqual(XCTWaiter.wait(for: [remaining], timeout: 5), .completed)
  XCTAssertFalse(app.buttons["reminder.share"].exists, "Cancel must not also prepare or open a composer")
  XCTAssertFalse(app.buttons["reminder.preview.close"].exists)
  // No share action, permission request or real contact/message is invoked.
 }
 func testCalendarDraftAndAgendaRoundTrip() {
  let app = XCUIApplication(); app.launchArguments = ["--ui-testing-temporary-database"]; app.launch()
  let add = app.buttons["appointment.add"]
  let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true AND enabled == true"), object: add)
  guard XCTWaiter.wait(for: [ready], timeout: 15) == .completed else { return XCTFail("Database did not become ready") }
  let timeline = app.scrollViews.firstMatch
  XCTAssertTrue(timeline.waitForExistence(timeout: 5))
  // Empty timeline tap creates a durable draft, not an unsaved editor.
  timeline.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.35)).tap()
  XCTAssertTrue(app.staticTexts["calendar.notice"].waitForExistence(timeout: 5))
  let header = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@ AND selected == true", "calendar.header.")).firstMatch
  XCTAssertTrue(header.exists)
  XCTAssertTrue(header.label.contains("Вибрано")); XCTAssertTrue(header.label.contains("Сьогодні"))
  let block = app.buttons.matching(NSPredicate(format: "identifier BEGINSWITH %@", "calendar.block.")).firstMatch
  XCTAssertTrue(block.waitForExistence(timeout: 5))
  let blockLabel = block.label
  XCTAssertTrue(blockLabel.contains("Послуг: 0"))
  XCTAssertTrue(blockLabel.contains(String(header.identifier.dropFirst("calendar.header.".count))))
  app.switches["calendar.agenda"].tap()
  let edit = app.buttons["calendar.agenda.edit"].firstMatch
  XCTAssertTrue(edit.waitForExistence(timeout: 5))
  XCTAssertTrue(edit.label.contains("Послуг: 0"))
  XCTAssertEqual(edit.label, blockLabel)
  XCTAssertTrue(edit.label.contains(String(header.identifier.dropFirst("calendar.header.".count))))
  let endAction = app.buttons["Змінити час закінчення"].firstMatch
  XCTAssertTrue(endAction.waitForExistence(timeout: 5)); endAction.tap()
  XCTAssertTrue(app.datePickers.firstMatch.waitForExistence(timeout: 5))
  XCTAssertFalse(app.buttons["appointment.save"].exists, "End action must not also push the appointment editor")
  app.buttons["Скасувати"].tap()
  XCTAssertTrue(endAction.waitForExistence(timeout: 5))
  XCTAssertFalse(app.buttons["appointment.save"].exists, "Cancelling end sheet must reveal the agenda, not an accidentally pushed editor")
  XCTAssertTrue(app.switches["calendar.agenda"].exists)
 }
 func testLaunchNavigationAndRequiredClient() {
  let app = XCUIApplication()
  app.launchArguments = ["--ui-testing-temporary-database"]
  app.launch()
  XCTAssertTrue(app.staticTexts["zapys"].waitForExistence(timeout: 10))
  app.tabBars.buttons["Послуги"].tap()
  XCTAssertTrue(app.navigationBars["Послуги"].exists)
  app.tabBars.buttons["Налаштування"].tap()
  XCTAssertTrue(app.staticTexts["Локальний профіль"].exists)
  app.tabBars.buttons["Клієнти"].tap()
  let add = app.buttons["client.add"]
  let ready = NSPredicate(format: "exists == true AND enabled == true AND hittable == true")
  let readyExpectation = XCTNSPredicateExpectation(predicate: ready, object: add)
  guard XCTWaiter.wait(for: [readyExpectation], timeout: 15) == .completed else {
   XCTFail("Client Add did not become enabled and hittable")
   return
  }
  add.tap()
  app.buttons["client.save"].tap()
  XCTAssertTrue(app.staticTexts["client.error"].exists)
  XCTAssertTrue(app.textFields["client.name"].exists)
  app.textFields["client.name"].tap()
  app.textFields["client.name"].typeText("Test client")
  XCTAssertTrue(app.staticTexts["Ім’я *"].exists)
  app.buttons["client.save"].tap()
  XCTAssertTrue(app.staticTexts["client.error"].exists)
  XCTAssertEqual(app.textFields["client.name"].value as? String, "Test client")
  app.textFields["client.phone"].tap()
  app.textFields["client.phone"].typeText("123")
  XCTAssertTrue(app.staticTexts["Телефон *"].exists)
  app.tabBars.buttons["Календар"].tap()
 }
}
