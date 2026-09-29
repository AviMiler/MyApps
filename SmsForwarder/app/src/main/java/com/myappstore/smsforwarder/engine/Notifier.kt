package com.myappstore.smsforwarder.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.myappstore.smsforwarder.MainActivity
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.core.Hebrew
import com.myappstore.smsforwarder.data.ForwardEvent

/** All notifications the app posts. Uses the platform API directly (minSdk 26). */
class Notifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        val channels = listOf(
            NotificationChannel(CHANNEL_UNDO, context.getString(R.string.channel_undo), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_undo_desc)
                setSound(null, null)
                enableVibration(false)
            },
            NotificationChannel(CHANNEL_ACTIVITY, context.getString(R.string.channel_activity), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.channel_activity_desc)
            },
            NotificationChannel(CHANNEL_PROBLEMS, context.getString(R.string.channel_problems), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_problems_desc)
            },
        )
        manager.createNotificationChannels(channels)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return manager.areNotificationsEnabled()
    }

    private fun builder(channel: String): Notification.Builder =
        Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_halaa)
            .setColor(BRAND_COLOR)
            .setContentIntent(openApp(MainActivity.TAB_LOG))
            .setAutoCancel(true)

    /** Countdown shown while a message waits in its undo window. */
    fun showUndo(event: ForwardEvent) {
        if (!canPost()) return
        val seconds = ((event.scheduledAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(1L)
        val notification = builder(CHANNEL_UNDO)
            .setContentTitle(context.getString(R.string.notif_undo_title, Hebrew.afterPrefix(event.recipientLabel)))
            .setContentText(event.outgoing)
            .setStyle(Notification.BigTextStyle().bigText(event.outgoing))
            .setSubText(context.getString(R.string.notif_undo_sub, seconds))
            .setWhen(event.scheduledAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter(seconds * 1000L + 1500L)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .addAction(action(R.string.action_cancel, ActionReceiver.ACTION_CANCEL, event.id))
            .addAction(action(R.string.action_send_now, ActionReceiver.ACTION_SEND_NOW, event.id))
            .build()
        manager.notify(undoId(event.id), notification)
    }

    fun clearUndo(eventId: Long) = manager.cancel(undoId(eventId))

    fun showForwarded(event: ForwardEvent) {
        if (!canPost()) return
        val title = if (event.batchSize > 1) {
            context.resources.getQuantityString(
                R.plurals.notif_forwarded_batch,
                event.batchSize,
                event.batchSize,
                Hebrew.afterPrefix(event.recipientLabel),
            )
        } else {
            context.getString(
                R.string.notif_forwarded_title,
                Hebrew.afterPrefix(event.senderLabel),
                Hebrew.afterPrefix(event.recipientLabel),
            )
        }
        val notification = builder(CHANNEL_ACTIVITY)
            .setContentTitle(title)
            .setContentText(event.body)
            .setStyle(Notification.BigTextStyle().bigText(event.body))
            .setGroup(GROUP_ACTIVITY)
            .build()
        manager.notify(forwardedId(event.id), notification)
    }

    fun showReplyRelayed(event: ForwardEvent) {
        if (!canPost()) return
        val notification = builder(CHANNEL_ACTIVITY)
            .setContentTitle(context.getString(R.string.notif_reply_title, event.senderLabel, Hebrew.afterPrefix(event.recipientLabel)))
            .setContentText(event.outgoing)
            .setStyle(Notification.BigTextStyle().bigText(event.outgoing))
            .setGroup(GROUP_ACTIVITY)
            .build()
        manager.notify(forwardedId(event.id), notification)
    }

    fun showFailure(event: ForwardEvent, reason: String) {
        if (!canPost()) return
        val notification = builder(CHANNEL_PROBLEMS)
            .setContentTitle(context.getString(R.string.notif_failed_title, Hebrew.afterPrefix(event.recipientLabel)))
            .setContentText(reason)
            .setStyle(Notification.BigTextStyle().bigText(reason + "\n" + event.outgoing))
            .addAction(action(R.string.action_retry, ActionReceiver.ACTION_RETRY, event.id))
            .build()
        manager.notify(failureId(event.id), notification)
    }

    fun clearFailure(eventId: Long) = manager.cancel(failureId(eventId))

    fun showProblem(id: Int, title: String, text: String) {
        if (!canPost()) return
        val notification = builder(CHANNEL_PROBLEMS)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(id, notification)
    }

    private fun action(label: Int, action: String, eventId: Long): Notification.Action {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(ActionReceiver.EXTRA_EVENT_ID, eventId)
        val pending = PendingIntent.getBroadcast(
            context,
            (eventId % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_stat_halaa),
            context.getString(label),
            pending,
        ).build()
    }

    private fun openApp(tab: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_TAB, tab)
        return PendingIntent.getActivity(
            context,
            tab.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun undoId(eventId: Long) = 100_000 + (eventId % 100_000).toInt()
    private fun forwardedId(eventId: Long) = 200_000 + (eventId % 100_000).toInt()
    private fun failureId(eventId: Long) = 300_000 + (eventId % 100_000).toInt()

    companion object {
        const val CHANNEL_UNDO = "undo"
        const val CHANNEL_ACTIVITY = "activity"
        const val CHANNEL_PROBLEMS = "problems"
        const val GROUP_ACTIVITY = "forwarded"
        const val ID_DAILY_LIMIT = 11
        const val ID_BURST = 12
        const val ID_NO_PERMISSION = 13
        const val ID_REPLY = 14
        private const val BRAND_COLOR = 0xFFFF6A3D.toInt()
    }
}
