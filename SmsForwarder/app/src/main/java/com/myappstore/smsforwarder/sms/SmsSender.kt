package com.myappstore.smsforwarder.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Hands an SMS to the radio and asks for "sent" and "delivered" callbacks, which
 * arrive in [SmsStatusReceiver] tagged with the log event ids they belong to.
 */
object SmsSender {

    const val ACTION_SENT = "com.myappstore.smsforwarder.action.SMS_SENT"
    const val ACTION_DELIVERED = "com.myappstore.smsforwarder.action.SMS_DELIVERED"
    const val EXTRA_EVENT_IDS = "event_ids"
    const val EXTRA_PART = "part"
    const val EXTRA_PARTS = "parts"

    /** Throws when the system refuses the message outright (bad address, no permission...). */
    fun send(context: Context, to: String, text: String, subId: Int, eventIds: LongArray) {
        val manager = Sims.smsManager(context, subId)
        val parts = manager.divideMessage(text)
        if (parts.isEmpty()) return
        val token = eventIds.first()
        val sent = ArrayList<PendingIntent>(parts.size)
        val delivered = ArrayList<PendingIntent>(parts.size)
        for (index in parts.indices) {
            sent += callback(context, ACTION_SENT, token, eventIds, index, parts.size)
            delivered += callback(context, ACTION_DELIVERED, token, eventIds, index, parts.size)
        }
        if (parts.size == 1) {
            manager.sendTextMessage(to, null, parts[0], sent[0], delivered[0])
        } else {
            manager.sendMultipartTextMessage(to, null, parts, sent, delivered)
        }
    }

    private fun callback(
        context: Context,
        action: String,
        token: Long,
        eventIds: LongArray,
        part: Int,
        parts: Int,
    ): PendingIntent {
        val intent = Intent(context, SmsStatusReceiver::class.java)
            .setAction(action)
            // A unique data URI keeps every part's PendingIntent distinct.
            .setData(Uri.parse("halaa://sms/$action/$token/$part"))
            .putExtra(EXTRA_EVENT_IDS, eventIds)
            .putExtra(EXTRA_PART, part)
            .putExtra(EXTRA_PARTS, parts)
        // Mutable so the telephony stack can attach the delivery report PDU.
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, part, intent, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
    }
}
