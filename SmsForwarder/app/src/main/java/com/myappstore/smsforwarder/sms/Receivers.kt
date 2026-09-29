package com.myappstore.smsforwarder.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.core.Origin
import kotlinx.coroutines.launch

/** An incoming text message, with multi-part messages already joined. */
data class IncomingSms(
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val subId: Int,
    /** The name shown in the messaging app's notification, which may not reveal the number. */
    val senderName: String? = null,
    /** Whether the notification's sender was found in the contacts. */
    val fromContact: Boolean = false,
    val origin: Int = Origin.SMS,
)

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
fun BroadcastReceiver.runAsync(tag: String, block: suspend () -> Unit) {
    val pending = goAsync()
    Graph.scope.launch {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(tag, "Background work failed", t)
        } finally {
            pending.finish()
        }
    }
}

/** Listens for every incoming SMS and passes it to the forwarding engine. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Unreadable SMS broadcast", e)
            null
        } ?: return

        val subId = subscriptionOf(intent)
        val now = System.currentTimeMillis()
        val bySender = LinkedHashMap<String, StringBuilder>()
        for (part in parts) {
            if (part == null) continue
            val address = part.originatingAddress ?: part.displayOriginatingAddress ?: continue
            bySender.getOrPut(address) { StringBuilder() }.append(part.messageBody.orEmpty())
        }
        if (bySender.isEmpty()) return
        val messages = bySender.map { (sender, body) -> IncomingSms(sender, body.toString(), now, subId) }

        runAsync(TAG) {
            messages.forEach { Graph.engine.onIncoming(it) }
        }
    }

    private fun subscriptionOf(intent: Intent): Int {
        val extras = intent.extras ?: return -1
        for (key in SUBSCRIPTION_KEYS) {
            @Suppress("DEPRECATION")
            val value = extras.get(key)
            if (value is Number) return value.toInt()
        }
        return -1
    }

    private companion object {
        const val TAG = "SmsReceiver"
        val SUBSCRIPTION_KEYS = arrayOf(
            "android.telephony.extra.SUBSCRIPTION_INDEX",
            "subscription",
            "subscription_id",
            "sub_id",
        )
    }
}

/** Receives the "sent" and "delivered" callbacks of messages sent by [SmsSender]. */
class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val ids = intent.getLongArrayExtra(SmsSender.EXTRA_EVENT_IDS) ?: return
        val part = intent.getIntExtra(SmsSender.EXTRA_PART, 0)
        val parts = intent.getIntExtra(SmsSender.EXTRA_PARTS, 1)
        val code = resultCode
        when (intent.action) {
            SmsSender.ACTION_SENT -> runAsync(TAG) {
                Graph.engine.onSent(ids, ok = code == Activity.RESULT_OK, errorCode = code, isLastPart = part == parts - 1)
            }
            SmsSender.ACTION_DELIVERED -> {
                val delivered = deliveryStatus(intent)
                runAsync(TAG) {
                    Graph.engine.onDelivered(ids, delivered = delivered, isLastPart = part == parts - 1)
                }
            }
        }
    }

    /** true = delivered, false = the network gave up, null = unknown / still trying. */
    private fun deliveryStatus(intent: Intent): Boolean? {
        val pdu = intent.getByteArrayExtra("pdu") ?: return true
        val message = try {
            SmsMessage.createFromPdu(pdu, intent.getStringExtra("format"))
        } catch (e: Exception) {
            null
        } ?: return true
        val status = message.status
        return when {
            status < 0x20 -> true
            status >= 0x40 -> false
            else -> null
        }
    }

    private companion object {
        const val TAG = "SmsStatusReceiver"
    }
}
