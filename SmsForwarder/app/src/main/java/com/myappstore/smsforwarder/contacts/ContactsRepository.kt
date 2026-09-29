package com.myappstore.smsforwarder.contacts

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.LruCache
import com.myappstore.smsforwarder.core.Phones
import com.myappstore.smsforwarder.data.Party
import com.myappstore.smsforwarder.engine.Permissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactNumber(val address: String, val label: String)

data class PhoneContact(
    val id: Long,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    val numbers: List<ContactNumber>,
) {
    fun party(number: ContactNumber) = Party(number.address, name, id, photoUri)
}

/** A sender found in the SMS inbox, for picking bank / service sender ids easily. */
data class RecentSender(
    val address: String,
    val name: String?,
    val photoUri: String?,
    val lastBody: String,
    val lastAt: Long,
    val count: Int,
) {
    fun party() = Party(address, name, 0L, photoUri)
}

/** Who sent a message seen in a notification, as far as the phone can tell. */
data class NotificationSender(val address: String, val name: String?, val isContact: Boolean)

class ContactsRepository(private val context: Context) {

    private val lookupCache = LruCache<String, Party>(200)
    private val missing = LruCache<String, Boolean>(200)

    /** The saved contact for [address], or null (also when contacts access is not granted). */
    fun lookup(address: String): Party? {
        if (!Phones.isPhoneLike(address) || !Permissions.canReadContacts(context)) return null
        val key = Phones.key(address)
        lookupCache.get(key)?.let { return it }
        if (missing.get(key) == true) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
        val projection = arrayOf(
            ContactsContract.PhoneLookup._ID,
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
        )
        val found = try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    Party(
                        address = address,
                        name = cursor.getString(1),
                        contactId = cursor.getLong(0),
                        photoUri = cursor.getString(2),
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
        if (found != null) lookupCache.put(key, found) else missing.put(key, true)
        return found
    }

    /**
     * Turns a notification's sender into an address: the number behind a tel: or contact link, a
     * number shown as the name, or the number of the contact with that exact name. Otherwise the name
     * itself is the address - business sender ids such as "Leumi" arrive that way.
     */
    fun resolveNotificationSender(name: String, personUri: String?): NotificationSender {
        val uri = personUri?.trim().orEmpty()
        val shownAsNumber = Phones.isPhoneLike(name)
        val fromLink = when {
            uri.startsWith("tel:", ignoreCase = true) -> Uri.decode(uri.substring(4))
            uri.startsWith("content://") -> numberOfContact(uri)
            else -> null
        }
        val byName = if (fromLink == null && !shownAsNumber) numberForName(name) else null
        val number = fromLink ?: byName ?: name.takeIf { shownAsNumber }
        if (number.isNullOrBlank() || !Phones.isPhoneLike(number)) {
            // Sender ids of businesses are Latin only, so a Hebrew name is someone saved in the contacts.
            return NotificationSender(name, name, isContact = name.any { it in '\u05D0'..'\u05EA' })
        }
        val contact = lookup(number)
        return NotificationSender(
            address = number,
            name = if (shownAsNumber) contact?.name else name,
            isContact = contact != null || byName != null || uri.startsWith("content://"),
        )
    }

    private fun numberOfContact(uri: String): String? {
        if (!Permissions.canReadContacts(context)) return null
        return try {
            val parsed = Uri.parse(uri)
            val contactUri = ContactsContract.Contacts.lookupContact(context.contentResolver, parsed) ?: parsed
            firstNumber("${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?", ContentUris.parseId(contactUri).toString())
        } catch (e: Exception) {
            null
        }
    }

    private fun numberForName(name: String): String? {
        if (!Permissions.canReadContacts(context)) return null
        return firstNumber("${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} = ?", name)
    }

    /** The contact's main number: the one marked default, else the first. */
    private fun firstNumber(selection: String, arg: String): String? = try {
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            selection,
            arrayOf(arg),
            "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, ${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0)?.trim() else null }
    } catch (e: Exception) {
        null
    }

    fun invalidate() {
        lookupCache.evictAll()
        missing.evictAll()
    }

    suspend fun loadAll(): List<PhoneContact> = withContext(Dispatchers.IO) {
        if (!Permissions.canReadContacts(context)) return@withContext emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
        )
        val byId = LinkedHashMap<Long, PhoneContact>()
        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " COLLATE LOCALIZED ASC",
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val number = cursor.getString(2)?.trim().orEmpty()
                    if (number.isEmpty()) continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        cursor.getInt(5),
                        cursor.getString(6),
                    ).toString()
                    val existing = byId[id]
                    if (existing == null) {
                        byId[id] = PhoneContact(
                            id = id,
                            name = cursor.getString(1)?.takeIf { it.isNotBlank() } ?: number,
                            photoUri = cursor.getString(3),
                            starred = cursor.getInt(4) == 1,
                            numbers = listOf(ContactNumber(number, label)),
                        )
                    } else if (existing.numbers.none { Phones.same(it.address, number) }) {
                        byId[id] = existing.copy(numbers = existing.numbers + ContactNumber(number, label))
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        byId.values.toList()
    }

    /** Distinct senders from the SMS inbox, newest first (needs READ_SMS). */
    suspend fun recentSenders(limit: Int = 60): List<RecentSender> = withContext(Dispatchers.IO) {
        if (!Permissions.canReadSms(context)) return@withContext emptyList()
        val found = LinkedHashMap<String, RecentSender>()
        try {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
                null,
                null,
                Telephony.Sms.DATE + " DESC",
            )?.use { cursor ->
                var rows = 0
                while (cursor.moveToNext() && rows < MAX_INBOX_ROWS && found.size < limit) {
                    rows++
                    val address = cursor.getString(0)?.trim().orEmpty()
                    if (address.isEmpty()) continue
                    val key = Phones.key(address)
                    val existing = found[key]
                    if (existing == null) {
                        found[key] = RecentSender(
                            address = address,
                            name = null,
                            photoUri = null,
                            lastBody = cursor.getString(1).orEmpty(),
                            lastAt = cursor.getLong(2),
                            count = 1,
                        )
                    } else {
                        found[key] = existing.copy(count = existing.count + 1)
                    }
                }
            }
        } catch (e: Exception) {
            return@withContext emptyList()
        }
        found.values.map { sender ->
            val contact = lookup(sender.address)
            sender.copy(name = contact?.name, photoUri = contact?.photoUri)
        }
    }

    /** Decodes a contact thumbnail; null when it is missing or unreadable. */
    fun loadPhoto(uri: String): Bitmap? = try {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_INBOX_ROWS = 1500
    }
}
