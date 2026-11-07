package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test

class PersonNameRulesTest {
 private val valid = Client(firstName="Олена",phone="0671234567")
 private fun rejects(client:Client) { assertThrows(ValidationException::class.java) { Rules.validateClient(client) } }

 @Test fun firstNameAccepts50AndRejects51CodePoints() {
  Rules.validateClient(valid.copy(firstName="І".repeat(50)))
  rejects(valid.copy(firstName="І".repeat(51)))
 }
 @Test fun surnameAccepts50AndRejects51CodePoints() {
  Rules.validateClient(valid.copy(surname="Ї".repeat(50)))
  rejects(valid.copy(surname="Ї".repeat(51)))
 }
 @Test fun patronymicAccepts50AndRejects51CodePoints() {
  Rules.validateClient(valid.copy(patronymic="Є".repeat(50)))
  rejects(valid.copy(patronymic="Є".repeat(51)))
 }
 @Test fun firstNameIsRequiredAndOtherNamesAreOptional() {
  Rules.validateClient(valid.copy(surname=" \t\u00a0",patronymic=""))
  rejects(valid.copy(firstName=" \t\u00a0"))
 }
 @Test fun eachFieldTrimsOnlyOuterWhitespaceForItsBoundary() {
  Rules.validateClient(valid.copy(firstName=" \t\u00a0"+"І".repeat(50)+"\u00a0 ",surname="\t"+"Ї".repeat(50)+" ",patronymic=" "+"Є".repeat(50)+"\n"))
  rejects(valid.copy(firstName=" "+"І".repeat(51)+" "))
  rejects(valid.copy(surname=" "+"Ї".repeat(51)+" "))
  rejects(valid.copy(patronymic=" "+"Є".repeat(51)+" "))
 }
 @Test fun supplementaryLettersCountAsOneCodePointInEveryField() {
  val letter="\uD801\uDC00" // U+10400, a supplementary Unicode letter.
  Rules.validateClient(valid.copy(firstName=letter.repeat(50),surname=letter.repeat(50),patronymic=letter.repeat(50)))
  rejects(valid.copy(firstName=letter.repeat(51)))
  rejects(valid.copy(surname=letter.repeat(51)))
  rejects(valid.copy(patronymic=letter.repeat(51)))
 }
 @Test fun combinedDisplayNameMayExceed50AndNamesKeepPunctuationAndInternalSpaces() {
  val client=valid.copy(firstName="Мар’яна-Анна "+"І".repeat(30),surname="О'Коннор "+"Ї".repeat(30),patronymic="Є".repeat(50))
  Rules.validateClient(client)
  assertEquals(client.firstName+" "+client.surname+" "+client.patronymic,client.displayName)
  assertTrue(client.displayName.length>50)
 }
 @Test fun sharedLengthCountsCodePointsAfterUnicodeOuterTrimWithoutDroppingInternalCharacters() {
  assertEquals(0,PersonNameRules.length(" \t\u00a0\n"))
  assertEquals(4,PersonNameRules.length(" \u00a0А \uD801\uDC00Ї\t"))
  assertEquals(2,PersonNameRules.length("і\u0308")) // A combining mark is its own code point.
  assertEquals(14,PersonNameRules.length("Мар’яна-Анна І"))
 }
 @Test fun inlineSaveUsesSameBoundaryAndRetainsRawInvalidInput() {
  val raw=" \uD801\uDC00"+"І".repeat(50)+"\u00a0 "
  val session=AppointmentSaveSession(AppointmentDraft.fresh().apply { end="23:59" },emptyList(),"name-client",raw," 0671234567 ")
  for(status in listOf(AppointmentStatus.DRAFT,AppointmentStatus.PLANNED)) {
   assertThrows(ValidationException::class.java) { session.prepare(status) }
   assertEquals(raw,session.inlineName)
   assertEquals(" 0671234567 ",session.inlinePhone)
   assertTrue(session.clients.isEmpty())
  }
  session.inlineName=" "+"І".repeat(50)+" "
  val client=session.prepare(AppointmentStatus.DRAFT).newClient!!
  assertEquals("І".repeat(50),client.firstName)
  assertEquals(" 0671234567 ",client.phone)
 }
}
