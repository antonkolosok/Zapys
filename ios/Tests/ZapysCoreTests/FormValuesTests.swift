import XCTest
@testable import ZapysCore
final class FormValuesTests: XCTestCase {
 func testDuration() throws {
  XCTAssertNil(try FormValues.duration(" "))
  XCTAssertEqual(try FormValues.duration("1439"), 1439)
  for s in ["0","-1","1440","1.5","abc"] { XCTAssertThrowsError(try FormValues.duration(s)) }
 }
 func testPrice() throws {
  XCTAssertNil(try FormValues.price(""))
  XCTAssertEqual(try FormValues.price("0"), 0)
  XCTAssertEqual(try FormValues.price("12,05"), 1205)
  for s in ["-1","1.001","NaN","1e3","92233720368547758.08"] { XCTAssertThrowsError(try FormValues.price(s)) }
 }
 func testClient() throws {
  XCTAssertThrowsError(try FormValues.client(name: " ", phone: "123"))
  XCTAssertThrowsError(try FormValues.client(name: "Іра", phone: " "))
  XCTAssertEqual(FormValues.phoneKey("+380 (67) 123"), FormValues.phoneKey("38067123"))
 }
}
