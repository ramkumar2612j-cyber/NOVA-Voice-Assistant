package com.nova.assistant

import android.content.ContentResolver
import android.provider.ContactsContract

/** Stage 2: reads real rows from the device's Android contacts provider. */
class ContactLookup(private val resolver: ContentResolver) : ContactRowSource {
    override fun queryAll(): ContactLookupResult {
        val out = ArrayList<ContactPhoneRow>()
        return try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
            )
            val cursor = resolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)
                ?: return ContactLookupResult.Failure("Android contacts provider returned no cursor")
            cursor.use {
                val idCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.TYPE)
                val primaryCol = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameCol).orEmpty().trim()
                    val number = cursor.getString(numberCol).orEmpty().trim()
                    if (name.isNotBlank() && number.isNotBlank()) {
                        out.add(ContactPhoneRow(
                            contactId = cursor.getString(idCol).orEmpty(),
                            displayName = name,
                            number = number,
                            type = cursor.getInt(typeCol),
                            isSuperPrimary = cursor.getInt(primaryCol) == 1
                        ))
                    }
                }
            }
            ContactLookupResult.Success(out)
        } catch (e: SecurityException) {
            ContactLookupResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: "Android denied access to contacts", permissionDenied = true)
        } catch (e: Exception) {
            ContactLookupResult.Failure(e.message?.takeIf { it.isNotBlank() } ?: "${e.javaClass.simpleName}: contact query failed")
        }
    }
}
