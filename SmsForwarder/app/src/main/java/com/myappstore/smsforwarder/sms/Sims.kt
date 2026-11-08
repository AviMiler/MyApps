package com.myappstore.smsforwarder.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** An active SIM card (subscription). */
data class SimCard(
    val subId: Int,
    val slotIndex: Int,
    val displayName: String,
    val carrier: String,
) {
    val label: String get() = "SIM ${slotIndex + 1} · $displayName"
}

object Sims {

    fun canRead(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED

    /** Active SIMs, or an empty list when the phone-state permission is missing. */
    fun active(context: Context): List<SimCard> {
        if (!canRead(context)) return emptyList()
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        return try {
            manager.activeSubscriptionInfoList.orEmpty()
                .map { info ->
                    val name = info.displayName?.toString().orEmpty()
                    val carrier = info.carrierName?.toString().orEmpty()
                    SimCard(
                        subId = info.subscriptionId,
                        slotIndex = info.simSlotIndex,
                        displayName = name.ifBlank { carrier.ifBlank { "SIM ${info.simSlotIndex + 1}" } },
                        carrier = carrier,
                    )
                }
                .sortedBy { it.slotIndex }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun nameOf(context: Context, subId: Int): String? {
        if (subId < 0) return null
        return active(context).firstOrNull { it.subId == subId }?.label
    }

    /**
     * Picks the SIM to send from: the route's choice, the app default, the phone's default SMS SIM,
     * then its default voice/data SIM, then the first SIM in the phone. A concrete SIM matters: on a
     * dual-SIM phone set to "ask every time" there is no default SMS SIM, and a message sent in the
     * background without one is rejected by the system. -1 means "let the system decide".
     */
    fun resolveForSending(context: Context, routeSubId: Int, defaultSubId: Int): Int {
        val present = active(context).map { it.subId }.ifEmpty { slotSubscriptions(context) }
        fun usable(id: Int) = id >= 0 && (present.isEmpty() || id in present)
        return listOf(
            routeSubId,
            defaultSubId,
            SubscriptionManager.getDefaultSmsSubscriptionId(),
            SubscriptionManager.getDefaultSubscriptionId(),
            SubscriptionManager.getDefaultDataSubscriptionId(),
        ).firstOrNull { usable(it) } ?: present.firstOrNull() ?: -1
    }

    /** Subscription ids of the SIMs in the phone's slots. Needs no permission (Android 10+). */
    private fun slotSubscriptions(context: Context): List<Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        val subscriptions = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        @Suppress("DEPRECATION")
        val slots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) telephony.activeModemCount else telephony.phoneCount
        return (0 until slots).mapNotNull { slot ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    SubscriptionManager.getSubscriptionId(slot).takeIf { SubscriptionManager.isValidSubscriptionId(it) }
                } else {
                    @Suppress("DEPRECATION")
                    subscriptions.getSubscriptionIds(slot)?.firstOrNull { SubscriptionManager.isValidSubscriptionId(it) }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun smsManager(context: Context, subId: Int): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (subId >= 0) base.createForSubscriptionId(subId) else base
        } else {
            @Suppress("DEPRECATION")
            if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId) else SmsManager.getDefault()
        }
    }
}
