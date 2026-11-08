package com.myappstore.smsforwarder.sms

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.myappstore.smsforwarder.Graph
import kotlinx.coroutines.launch

/** One incoming message as shown in a messaging app's notification. */
data class NotifiedMessage(
    val senderName: String,
    /** "tel:…" or a contact link, when the app attaches one to the sender. */
    val senderUri: String?,
    val text: String,
    /** When the message arrived, or 0 when the notification does not say. */
    val time: Long,
)

/** Reads the incoming messages out of a messaging app's notification. */
object NotificationReader {

    private const val KEY_TEXT = "text"
    private const val KEY_TIME = "time"
    private const val KEY_SENDER = "sender"
    private const val KEY_SENDER_PERSON = "sender_person"

    /** Placeholders some apps show instead of the text when previews are hidden. */
    private val placeholders = setOf(
        "הודעה חדשה", "הודעות חדשות", "new message", "new messages", "message", "הודעה",
    )

    fun messages(notification: Notification): List<NotifiedMessage> {
        val extras = notification.extras ?: return emptyList()
        @Suppress("DEPRECATION")
        val bundles = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!bundles.isNullOrEmpty()) {
            val self = selfName(extras)
            return bundles.mapNotNull { (it as? Bundle)?.let { bundle -> fromMessagingStyle(bundle, self) } }
        }
        // Without MessagingStyle only a plain "sender / text" message notification is trusted.
        if (notification.category != Notification.CATEGORY_MESSAGE) return emptyList()
        if (extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) != null) return emptyList()
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.trim().orEmpty()
        if (title.isEmpty() || !isRealText(text)) return emptyList()
        return listOf(NotifiedMessage(title, null, text, notification.`when`))
    }

    private fun fromMessagingStyle(bundle: Bundle, self: String?): NotifiedMessage? {
        val text = bundle.getCharSequence(KEY_TEXT)?.toString()?.trim().orEmpty()
        if (!isRealText(text)) return null
        var name: CharSequence? = null
        var uri: String? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val person = bundle.getParcelable<Person>(KEY_SENDER_PERSON)
            name = person?.name
            uri = person?.uri
        }
        if (name.isNullOrBlank()) name = bundle.getCharSequence(KEY_SENDER)
        // A message without a sender is one the user sent.
        val sender = name?.toString()?.trim()
        if (sender.isNullOrEmpty() || sender == self) return null
        return NotifiedMessage(sender, uri, text, bundle.getLong(KEY_TIME))
    }

    private fun selfName(extras: Bundle): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val person = extras.getParcelable<Person>(Notification.EXTRA_MESSAGING_PERSON)
            person?.name?.toString()?.trim()?.let { return it }
        }
        @Suppress("DEPRECATION")
        return extras.getCharSequence(Notification.EXTRA_SELF_DISPLAY_NAME)?.toString()?.trim()
    }

    private fun isRealText(text: String) = text.isNotBlank() && text.lowercase() !in placeholders
}

/**
 * Backup capture: reads new messages from the messaging app's notifications. A message whose SMS
 * already reached the app is ignored; one the phone never handed over (or a chat message, which is
 * not an SMS at all) is forwarded from here. Runs only while the user grants notification access.
 */
class MessageNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            if (sbn.packageName == packageName || !isMessagingApp(sbn.packageName)) return
            val notification = sbn.notification ?: return
            if (notification.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_ONGOING_EVENT) != 0) return
            if (hidesSensitiveContent(sbn)) return
            val messages = NotificationReader.messages(notification)
            if (messages.isEmpty()) return
            Graph.scope.launch { Graph.engine.onNotifiedMessages(sbn.packageName, messages, sbn.postTime) }
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable notification", e)
        }
    }

    private fun isMessagingApp(pkg: String): Boolean =
        pkg in MESSAGING_APPS || pkg == Telephony.Sms.getDefaultSmsPackage(this)

    /** From Android 15 one-time codes are blanked for apps like this one; those still arrive as SMS. */
    private fun hidesSensitiveContent(sbn: StatusBarNotification): Boolean {
        if (Build.VERSION.SDK_INT < ANDROID_15) return false
        return try {
            val ranking = Ranking()
            currentRanking.getRanking(sbn.key, ranking) &&
                Ranking::class.java.getMethod("hasSensitiveContent").invoke(ranking) == true
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val TAG = "MessageNotifications"
        const val ANDROID_15 = 35
        val MESSAGING_APPS = setOf(
            "com.samsung.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.android.messaging",
            "com.oneplus.mms",
            "com.motorola.messaging",
            "com.sonyericsson.conversations",
            "com.lge.message",
            "com.asus.message",
        )
    }
}
