package ua.zapys
import android.test.AndroidTestCase
import android.content.Intent
import android.provider.ContactsContract
import ua.zapys.device.*
import ua.zapys.domain.*
/** Authored, UNRUN without SDK/device. Never launches a composer or queries real contacts. */
class DeviceIntentTest: AndroidTestCase() {
 fun testProfilePreferenceSurvivesReopenAndIdentitySave() {
  val name="device-profile-test.db"; context.deleteDatabase(name)
  try {
   ua.zapys.data.SQLiteRepository(context,name).use { it.saveProfile(LocalProfile(name="A",notificationsEnabled=true)) }
   ua.zapys.data.SQLiteRepository(context,name).use { val p=it.profile(); assertTrue(p.notificationsEnabled); it.saveProfile(p.copy(email="local@example.invalid")) }
   ua.zapys.data.SQLiteRepository(context,name).use { assertTrue(it.profile().notificationsEnabled); assertEquals("local@example.invalid",it.profile().email) }
  } finally { context.deleteDatabase(name) }
 }
 fun testSelectivePickerHasOnlyPhoneMimeAndNoGlobalReadPermission() {
  val intent=ContactPicker.intent()
  assertEquals(Intent.ACTION_PICK,intent.action)
  assertEquals(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE,intent.type)
  assertNull(intent.data)
 }
 fun testMessengerIntentTargetsChosenAppButDoesNotPretendToSelectRecipient() {
  val r=Reminder(appointmentId="synthetic",scheduledAt=1,text="literal $1",channel=Channel.WHATSAPP,recipient="synthetic")
  val intent=MessengerShare.intent(r)
  assertEquals(Intent.ACTION_SEND,intent.action)
  assertEquals("com.whatsapp",intent.`package`)
  assertEquals("literal $1",intent.getStringExtra(Intent.EXTRA_TEXT))
  assertNull(intent.data)
 }
 fun testBlankAndLegacyRecipientProduceSameManualShareWithoutAddressExtras() {
  val r=Reminder(appointmentId="synthetic",scheduledAt=1,text="Synthetic manual preview",channel=Channel.TELEGRAM,recipient="")
  for(recipient in listOf(""," legacy recipient ")) {
   val intent=MessengerShare.intent(r.copy(recipient=recipient))
   assertEquals(Intent.ACTION_SEND,intent.action); assertEquals("org.telegram.messenger",intent.`package`)
   assertEquals("text/plain",intent.type); assertEquals("Synthetic manual preview",intent.getStringExtra(Intent.EXTRA_TEXT))
   assertNull(intent.data); assertEquals(setOf(Intent.EXTRA_TEXT),intent.extras!!.keySet())
  }
 }
}
