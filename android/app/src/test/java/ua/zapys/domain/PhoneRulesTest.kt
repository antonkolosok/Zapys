package ua.zapys.domain

import org.junit.Assert.*
import org.junit.Test

class PhoneRulesTest {
 @Test fun acceptsFullUkrainianPrefixesAndPresentationFormatting() {
  for(raw in listOf("0671234567", "+380671234567", "380671234567", "00380671234567", " (067) 123-45-67 ", "+380\u00a0(67)\u00a0123-45-67")) {
   assertEquals(raw, "+380671234567", PhoneRules.normalize(raw))
  }
  assertEquals("+380441234567",PhoneRules.normalize("+380 (44) 123-45-67"))
  // Format validation does not restrict numbers to a stale carrier/area allowlist.
  assertEquals("+380111234567",PhoneRules.normalize("0111234567"))
 }
 @Test fun rejectsUnsupportedCharactersPrefixesLengthsAndZeroNationalPrefix() {
  for(raw in listOf("", " ", "123", "067123456", "06712345678", "+48123456789", "abc0671234567", "++380671234567", "0671234567 ext 1", "0000000000", "+380000000000", "+380067123456", "00380067123456", "+380 (67 123-45-67", "+380 67) 123-45-67", "067+1234567", "380+671234567", "067\t1234567", "067\n1234567", "０６７１２３４５６７", "+00380671234567", "+0671234567")) {
   assertNull(raw,PhoneRules.normalize(raw))
  }
 }
}
