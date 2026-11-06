package ua.zapys.device
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone

object ContactPicker {
 const val REQUEST=410
 fun intent()=Intent(Intent.ACTION_PICK).apply { type=Phone.CONTENT_TYPE }
 // Only the URI granted by the user's selection; never enumerate the address book.
 fun read(context: Context, uri: Uri): Pair<String,String>? {
  if(uri.scheme!="content") return null
  return context.contentResolver.query(uri,arrayOf(Phone.DISPLAY_NAME,Phone.NUMBER),null,null,null)?.use {
   if(it.moveToFirst()) it.getString(0).orEmpty() to it.getString(1).orEmpty() else null
  }
 }
}
