package com.myappstore.smsforwarder.ui.health

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.engine.Permissions
import com.myappstore.smsforwarder.ui.components.HalaaCard
import com.myappstore.smsforwarder.ui.components.IconBadge
import com.myappstore.smsforwarder.ui.components.InfoCard
import com.myappstore.smsforwarder.ui.components.InlineAction
import com.myappstore.smsforwarder.ui.components.LocalResumeTick
import com.myappstore.smsforwarder.ui.components.Pill
import com.myappstore.smsforwarder.ui.components.SectionHeader
import com.myappstore.smsforwarder.ui.components.TopBar
import com.myappstore.smsforwarder.ui.components.pluralText
import com.myappstore.smsforwarder.ui.theme.Halaa

/** Everything that can stop forwarding from working, checked live. */
object HealthChecks {

    enum class Level { REQUIRED, RECOMMENDED, OPTIONAL, INFO }

    enum class Check { SMS, CONTACTS, NOTIFICATIONS, BATTERY, EXACT_ALARMS, PHONE_STATE, READ_SMS, SIM, AIRPLANE }

    data class Item(val check: Check, val ok: Boolean, val level: Level)

    fun items(context: Context): List<Item> {
        val items = mutableListOf(
            Item(Check.SMS, Permissions.coreReady(context), Level.REQUIRED),
            Item(Check.CONTACTS, Permissions.canReadContacts(context), Level.RECOMMENDED),
            Item(Check.NOTIFICATIONS, Permissions.canNotify(context), Level.RECOMMENDED),
            Item(Check.BATTERY, Permissions.ignoresBatteryOptimizations(context), Level.RECOMMENDED),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items += Item(Check.EXACT_ALARMS, Permissions.canUseExactAlarms(context), Level.OPTIONAL)
        }
        items += Item(Check.PHONE_STATE, Permissions.canReadPhoneState(context), Level.OPTIONAL)
        items += Item(Check.READ_SMS, Permissions.canReadSms(context), Level.OPTIONAL)
        items += Item(Check.SIM, Permissions.hasSim(context), Level.INFO)
        items += Item(Check.AIRPLANE, !Permissions.airplaneMode(context), Level.INFO)
        return items
    }

    fun issueCount(context: Context): Int =
        items(context).count { !it.ok && (it.level == Level.REQUIRED || it.level == Level.RECOMMENDED) }
}

private data class CheckText(val icon: ImageVector, val title: Int, val okText: Int, val badText: Int, val fix: Int?)

private fun textFor(check: HealthChecks.Check): CheckText = when (check) {
    HealthChecks.Check.SMS -> CheckText(Icons.Rounded.Sms, R.string.health_sms, R.string.health_sms_ok, R.string.health_sms_bad, R.string.grant_access)
    HealthChecks.Check.CONTACTS -> CheckText(Icons.Rounded.Contacts, R.string.health_contacts, R.string.health_contacts_ok, R.string.health_contacts_bad, R.string.grant_access)
    HealthChecks.Check.NOTIFICATIONS -> CheckText(Icons.Rounded.Notifications, R.string.health_notifications, R.string.health_notifications_ok, R.string.health_notifications_bad, R.string.grant_access)
    HealthChecks.Check.BATTERY -> CheckText(Icons.Rounded.BatteryChargingFull, R.string.health_battery, R.string.health_battery_ok, R.string.health_battery_bad, R.string.health_fix_battery)
    HealthChecks.Check.EXACT_ALARMS -> CheckText(Icons.Rounded.Alarm, R.string.health_exact, R.string.health_exact_ok, R.string.health_exact_bad, R.string.grant_access)
    HealthChecks.Check.PHONE_STATE -> CheckText(Icons.Rounded.PhoneAndroid, R.string.health_phone, R.string.health_phone_ok, R.string.health_phone_bad, R.string.grant_access)
    HealthChecks.Check.READ_SMS -> CheckText(Icons.Rounded.Inbox, R.string.health_read_sms, R.string.health_read_sms_ok, R.string.health_read_sms_bad, R.string.grant_access)
    HealthChecks.Check.SIM -> CheckText(Icons.Rounded.SimCard, R.string.health_sim, R.string.health_sim_ok, R.string.health_sim_bad, null)
    HealthChecks.Check.AIRPLANE -> CheckText(Icons.Rounded.AirplanemodeActive, R.string.health_airplane, R.string.health_airplane_ok, R.string.health_airplane_bad, R.string.health_fix_airplane)
}

@Composable
fun HealthScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val colors = Halaa.colors
    val tick = LocalResumeTick.current
    var refresh by remember { mutableIntStateOf(0) }
    val items = remember(tick, refresh) { HealthChecks.items(context) }
    val issues = items.count { !it.ok && (it.level == HealthChecks.Level.REQUIRED || it.level == HealthChecks.Level.RECOMMENDED) }
    val multiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val singleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun fix(check: HealthChecks.Check) {
        when (check) {
            HealthChecks.Check.SMS -> multiLauncher.launch(Permissions.SMS)
            HealthChecks.Check.CONTACTS -> singleLauncher.launch(Permissions.CONTACTS)
            HealthChecks.Check.NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !Permissions.granted(context, Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    singleLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    Permissions.open(context, Permissions.notificationSettingsIntent(context))
                }
            HealthChecks.Check.BATTERY -> Permissions.open(context, Permissions.batteryIntent(context))
            HealthChecks.Check.EXACT_ALARMS -> Permissions.open(context, Permissions.exactAlarmIntent(context))
            HealthChecks.Check.PHONE_STATE -> singleLauncher.launch(Permissions.PHONE_STATE)
            HealthChecks.Check.READ_SMS -> singleLauncher.launch(Permissions.READ_SMS)
            HealthChecks.Check.AIRPLANE -> Permissions.open(context, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            HealthChecks.Check.SIM -> Unit
        }
    }

    BackHandler(onBack = onBack)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        TopBar(title = stringResource(R.string.health_title), onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBottom + 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "summary") { Summary(issues) }
            items(items, key = { it.check.name }) { item ->
                CheckRow(item, onFix = { fix(item.check) })
            }
            item(key = "tips-header") {
                SectionHeader(stringResource(R.string.tips_title), Modifier.padding(top = 12.dp))
            }
            item(key = "tip-rcs") {
                InfoCard(
                    title = stringResource(R.string.tip_rcs_title),
                    text = stringResource(R.string.tip_rcs),
                    icon = Icons.Rounded.Forum,
                    color = colors.held,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                item(key = "tip-restricted") {
                    InfoCard(
                        title = stringResource(R.string.tip_restricted_title),
                        text = stringResource(R.string.tip_restricted),
                        icon = Icons.Rounded.Security,
                        color = colors.warning,
                        action = {
                            InlineAction(
                                stringResource(R.string.open_app_settings),
                                Icons.Rounded.Security,
                                { Permissions.open(context, Permissions.appDetailsIntent(context)) },
                                color = colors.warning,
                            )
                        },
                    )
                }
            }
            val vendorTip = vendorTip()
            if (vendorTip != null) {
                item(key = "tip-vendor") {
                    InfoCard(
                        title = stringResource(R.string.tip_vendor_title),
                        text = stringResource(vendorTip),
                        icon = Icons.Rounded.BatteryChargingFull,
                        color = colors.route(2),
                    )
                }
            }
            item(key = "tip-limit") {
                InfoCard(
                    title = stringResource(R.string.tip_limit_title),
                    text = stringResource(R.string.tip_limit),
                    icon = Icons.Rounded.Speed,
                    color = colors.route(1),
                )
            }
            item(key = "tip-test") {
                InfoCard(
                    title = stringResource(R.string.tip_test_title),
                    text = stringResource(R.string.tip_test),
                    icon = Icons.Rounded.Science,
                    color = colors.brand,
                )
            }
        }
    }
}

private fun vendorTip(): Int? {
    val maker = Build.MANUFACTURER.lowercase()
    return when {
        maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco") -> R.string.tip_vendor_xiaomi
        maker.contains("huawei") || maker.contains("honor") -> R.string.tip_vendor_huawei
        maker.contains("samsung") -> R.string.tip_vendor_samsung
        maker.contains("oppo") || maker.contains("realme") || maker.contains("oneplus") || maker.contains("vivo") -> R.string.tip_vendor_oppo
        else -> null
    }
}

@Composable
private fun Summary(issues: Int) {
    val colors = Halaa.colors
    val good = issues == 0
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    if (good) listOf(Color(0xFF34C58A), colors.success) else listOf(Color(0xFFFFB547), Color(0xFFE08A00)),
                ),
            )
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (good) Icons.Rounded.Check else Icons.Rounded.PriorityHigh, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (good) stringResource(R.string.health_all_good) else pluralText(R.plurals.health_issues, issues, issues),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
            )
            Text(
                stringResource(if (good) R.string.health_all_good_sub else R.string.health_issues_sub),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun CheckRow(item: HealthChecks.Item, onFix: () -> Unit) {
    val colors = Halaa.colors
    val text = textFor(item.check)
    val statusColor = when {
        item.ok -> colors.success
        item.level == HealthChecks.Level.REQUIRED -> colors.danger
        item.level == HealthChecks.Level.RECOMMENDED -> colors.warning
        else -> colors.inkSoft
    }
    HalaaCard(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(text.icon, statusColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(text.title),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.ink,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    val level = when (item.level) {
                        HealthChecks.Level.REQUIRED -> R.string.level_required
                        HealthChecks.Level.RECOMMENDED -> R.string.level_recommended
                        HealthChecks.Level.OPTIONAL -> R.string.level_optional
                        HealthChecks.Level.INFO -> R.string.level_info
                    }
                    Pill(stringResource(level), colors.inkSoft, colors.sunken)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(if (item.ok) text.okText else text.badText),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (item.ok) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.success)
            } else if (text.fix != null) {
                InlineAction(stringResource(text.fix), Icons.Rounded.Warning, onFix, color = statusColor)
            }
        }
    }
}
