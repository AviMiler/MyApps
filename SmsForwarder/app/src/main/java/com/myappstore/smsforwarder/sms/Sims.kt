package com.myappstore.smsforwarder.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager

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
     * Picks the SIM to send from: the route's choice, then the app default, then the
     * phone's default SMS SIM, then the first active SIM. -1 means "let the system decide".
     */
    fun resolveForSending(context: Context, routeSubId: Int, defaultSubId: Int): Int {
        val active = active(context)
        fun usable(id: Int) = id >= 0 && (active.isEmpty() || active.any { it.subId == id })
        if (usable(routeSubId)) return routeSubId
        if (usable(defaultSubId)) return defaultSubId
        val systemDefault = SubscriptionManager.getDefaultSmsSubscriptionId()
        if (usable(systemDefault)) return systemDefault
        return active.firstOrNull()?.subId ?: -1
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
