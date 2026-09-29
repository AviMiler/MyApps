package com.myappstore.smsforwarder.engine

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.telephony.TelephonyManager

/** Permission and system-state checks shared by the UI and the engine. */
object Permissions {

    val SMS = arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
    const val CONTACTS = Manifest.permission.READ_CONTACTS
    const val READ_SMS = Manifest.permission.READ_SMS
    const val PHONE_STATE = Manifest.permission.READ_PHONE_STATE

    /** The permissions requested together on the onboarding screen. */
    fun essentials(): Array<String> {
        val list = mutableListOf(*SMS, CONTACTS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) list += Manifest.permission.POST_NOTIFICATIONS
        return list.toTypedArray()
    }

    fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun canReceive(context: Context) = granted(context, Manifest.permission.RECEIVE_SMS)
    fun canSend(context: Context) = granted(context, Manifest.permission.SEND_SMS)
    fun canReadContacts(context: Context) = granted(context, CONTACTS)
    fun canReadSms(context: Context) = granted(context, READ_SMS)
    fun canReadPhoneState(context: Context) = granted(context, PHONE_STATE)

    fun coreReady(context: Context) = canReceive(context) && canSend(context)

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted(context, Manifest.permission.POST_NOTIFICATIONS)
        ) return false
        return context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    }

    fun ignoresBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    fun canUseExactAlarms(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun hasSim(context: Context): Boolean {
        val telephony = context.getSystemService(TelephonyManager::class.java) ?: return false
        return telephony.simState == TelephonyManager.SIM_STATE_READY
    }

    fun airplaneMode(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    // ------------------------------------------------------------- system screens

    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    fun batteryIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun exactAlarmIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        } else {
            appDetailsIntent(context)
        }

    fun notificationSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /** Opens a system screen, falling back to the app's details page. */
    fun open(context: Context, intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                context.startActivity(appDetailsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: Exception) {
            }
        }
    }
}
