package com.myappstore.smsforwarder.ui.settings

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationCity
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.automirrored.rounded.MergeType
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.core.Cities
import com.myappstore.smsforwarder.core.RestCalendar
import com.myappstore.smsforwarder.data.AppSettings
import com.myappstore.smsforwarder.data.Backup
import com.myappstore.smsforwarder.data.ThemeMode
import com.myappstore.smsforwarder.engine.IsraeliHolidays
import com.myappstore.smsforwarder.engine.Permissions
import com.myappstore.smsforwarder.sms.SimCard
import com.myappstore.smsforwarder.sms.Sims
import com.myappstore.smsforwarder.ui.Fmt
import com.myappstore.smsforwarder.ui.components.ChoiceChips
import com.myappstore.smsforwarder.ui.components.ConfirmDialog
import com.myappstore.smsforwarder.ui.components.DaysPicker
import com.myappstore.smsforwarder.ui.components.HalaaCard
import com.myappstore.smsforwarder.ui.components.IconBadge
import com.myappstore.smsforwarder.ui.components.InlineAction
import com.myappstore.smsforwarder.ui.components.LocalResumeTick
import com.myappstore.smsforwarder.ui.components.NavRow
import com.myappstore.smsforwarder.ui.components.SectionHeader
import com.myappstore.smsforwarder.ui.components.Segmented
import com.myappstore.smsforwarder.ui.components.SwitchRow
import com.myappstore.smsforwarder.ui.components.TimeRangeRow
import com.myappstore.smsforwarder.ui.components.fieldColors
import com.myappstore.smsforwarder.ui.components.pluralText
import com.myappstore.smsforwarder.ui.components.rememberNow
import com.myappstore.smsforwarder.ui.health.HealthChecks
import com.myappstore.smsforwarder.ui.theme.Halaa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private fun update(block: (AppSettings) -> AppSettings) = Graph.settings.update(block)

@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onOpenHealth: () -> Unit,
    onReplayOnboarding: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Graph.settings.flow.collectAsState()
    val tick = LocalResumeTick.current
    var phoneGranted by remember(tick) { mutableStateOf(Permissions.canReadPhoneState(context)) }
    val sims = remember(tick, phoneGranted) { Sims.active(context) }
    val healthIssues = remember(tick) { HealthChecks.issueCount(context) }
    val now = rememberNow()

    var cityDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Backup.Parsed?>(null) }

    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        phoneGranted = it
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = exportTo(context, uri)
                toast(context, if (ok) R.string.backup_exported else R.string.backup_failed)
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val parsed = readBackup(context, uri)
                if (parsed == null) toast(context, R.string.backup_invalid) else pendingImport = parsed
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            SectionHeader(stringResource(R.string.settings_title), Modifier.padding(top = 8.dp))
        }
        item(key = "rest") { RestSection(settings, now, onPickCity = { cityDialog = true }) }
        item(key = "quiet") { QuietSection(settings) }
        item(key = "pause") { PauseSection(settings) }
        item(key = "safety") { SafetySection(settings) }
        item(key = "notifications") { NotificationsSection(settings) }
        if (!phoneGranted || sims.size > 1) {
            item(key = "sim") {
                SimSection(settings, sims, phoneGranted, onGrant = { phoneLauncher.launch(Permissions.PHONE_STATE) })
            }
        }
        item(key = "privacy") { PrivacySection(settings, onClear = { confirmClear = true }) }
        item(key = "backup") {
            BackupSection(
                onExport = { exportLauncher.launch("halaa-backup-${LocalDate.now()}.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
            )
        }
        item(key = "appearance") { AppearanceSection(settings) }
        item(key = "help") { HelpSection(healthIssues, onOpenHealth, onReplayOnboarding) }
        item(key = "about") { AboutFooter() }
    }

    if (cityDialog) {
        CityDialog(
            selectedId = settings.restCityId,
            onSelect = { city ->
                update { it.copy(restCityId = city.id, candleMinutes = city.candleMinutes) }
                cityDialog = false
            },
            onDismiss = { cityDialog = false },
        )
    }
    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.log_clear_title),
            text = stringResource(R.string.log_clear_text),
            confirm = stringResource(R.string.log_clear),
            danger = true,
            onConfirm = {
                confirmClear = false
                scope.launch { Graph.db.events().clearFinished() }
            },
            onDismiss = { confirmClear = false },
        )
    }
    val parsed = pendingImport
    if (parsed != null) {
        ImportDialog(
            parsed = parsed,
            onApply = { replace ->
                pendingImport = null
                scope.launch {
                    applyImport(parsed, replace)
                    toast(context, R.string.backup_imported)
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    color: Color,
    content: @Composable ColumnScope.() -> Unit,
) {
    HalaaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = Halaa.colors.ink)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Halaa.colors.ink,
        modifier = Modifier.padding(top = 10.dp, bottom = 8.dp),
    )
}

// ------------------------------------------------------------------ Shabbat & holidays

@Composable
private fun RestSection(s: AppSettings, now: Long, onPickCity: () -> Unit) {
    val colors = Halaa.colors
    SettingsCard(stringResource(R.string.rest_title), Icons.Rounded.NightsStay, colors.night) {
        SwitchRow(
            title = stringResource(R.string.rest_switch),
            checked = s.restEnabled,
            onChange = { on -> update { it.copy(restEnabled = on) } },
            description = stringResource(R.string.rest_desc),
            color = colors.held,
        )
        AnimatedVisibility(visible = s.restEnabled) {
            Column {
                NavRow(
                    title = stringResource(R.string.rest_city),
                    onClick = onPickCity,
                    value = Cities.byId(s.restCityId).name,
                    icon = Icons.Rounded.LocationCity,
                    color = colors.held,
                )
                Label(stringResource(R.string.rest_candle))
                ChoiceChips(
                    options = listOf(18, 20, 30, 40).map { it to stringResource(R.string.minutes_short, it) },
                    selected = s.candleMinutes,
                    onSelect = { m -> update { it.copy(candleMinutes = m) } },
                    color = colors.held,
                )
                Label(stringResource(R.string.rest_havdalah))
                ChoiceChips(
                    options = listOf(30, 40, 50, 72).map { it to stringResource(R.string.minutes_short, it) },
                    selected = s.havdalahMinutes,
                    onSelect = { m -> update { it.copy(havdalahMinutes = m) } },
                    color = colors.held,
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = stringResource(R.string.rest_holidays),
                    checked = s.restIncludesHolidays,
                    onChange = { on -> update { it.copy(restIncludesHolidays = on) } },
                    description = stringResource(R.string.rest_holidays_desc),
                    color = colors.held,
                )
                val minute = now / 60_000L
                val window = remember(s, minute) {
                    s.restCalendar(ZoneId.systemDefault(), IsraeliHolidays::isYomTov)?.currentOrNext(Fmt.zoned(now))
                }
                if (window != null) {
                    Spacer(Modifier.height(10.dp))
                    RestPreview(window, now)
                }
            }
        }
    }
}

@Composable
private fun RestPreview(window: RestCalendar.Window, now: Long) {
    val colors = Halaa.colors
    val start = window.start.toInstant().toEpochMilli()
    val end = window.end.toInstant().toEpochMilli()
    val active = now in start until end
    val title = stringResource(
        when {
            active && window.includesHoliday -> R.string.rest_now_holiday
            active -> R.string.rest_now_shabbat
            window.includesHoliday -> R.string.rest_next_holiday
            else -> R.string.rest_next_shabbat
        },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(colors.night, colors.nightDeep)))
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(10.dp))
        Row {
            RestTime(stringResource(R.string.rest_enters), window.start.toLocalDate(), Fmt.time(start), Modifier.weight(1f))
            RestTime(stringResource(R.string.rest_leaves), window.end.toLocalDate(), Fmt.time(end), Modifier.weight(1f))
        }
    }
}

@Composable
private fun RestTime(label: String, date: LocalDate, time: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
        Text(time, style = MaterialTheme.typography.headlineMedium, color = Color(0xFFFFE7A8))
        Text(
            stringResource(R.string.rest_day, Fmt.dayName(date)),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun CityDialog(selectedId: String, onSelect: (com.myappstore.smsforwarder.core.City) -> Unit, onDismiss: () -> Unit) {
    val colors = Halaa.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.rest_city), style = MaterialTheme.typography.titleLarge) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(Cities.all, key = { it.id }) { city ->
                    val selected = city.id == selectedId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) colors.heldSoft else Color.Transparent)
                            .clickable { onSelect(city) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            city.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) colors.held else colors.ink,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.rest_city_candle, city.candleMinutes),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.inkSoft,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close), color = colors.inkSoft) }
        },
    )
}

// ------------------------------------------------------------------ quiet hours & pause

@Composable
private fun QuietSection(s: AppSettings) {
    val colors = Halaa.colors
    SettingsCard(stringResource(R.string.quiet_title), Icons.Rounded.Bedtime, colors.held) {
        SwitchRow(
            title = stringResource(R.string.quiet_switch),
            checked = s.quietEnabled,
            onChange = { on -> update { it.copy(quietEnabled = on) } },
            description = stringResource(R.string.quiet_desc),
            color = colors.held,
        )
        AnimatedVisibility(visible = s.quietEnabled) {
            Column {
                Spacer(Modifier.height(8.dp))
                TimeRangeRow(
                    start = s.quietStart,
                    end = s.quietEnd,
                    onStart = { m -> update { it.copy(quietStart = m) } },
                    onEnd = { m -> update { it.copy(quietEnd = m) } },
                    color = colors.held,
                )
                Spacer(Modifier.height(12.dp))
                DaysPicker(s.quietDays, { days -> update { it.copy(quietDays = days) } }, color = colors.held)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.quiet_days_hint), style = MaterialTheme.typography.bodySmall, color = colors.inkFaint)
                Label(stringResource(R.string.quiet_behavior))
                Segmented(
                    options = listOf(true to stringResource(R.string.behavior_hold), false to stringResource(R.string.behavior_drop)),
                    selected = s.holdDuringQuiet,
                    onSelect = { hold -> update { it.copy(holdDuringQuiet = hold) } },
                )
            }
        }
    }
}

@Composable
private fun PauseSection(s: AppSettings) {
    val colors = Halaa.colors
    SettingsCard(stringResource(R.string.pause_title), Icons.Rounded.PauseCircle, colors.held) {
        Text(stringResource(R.string.pause_desc), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        Label(stringResource(R.string.pause_behavior))
        Segmented(
            options = listOf(true to stringResource(R.string.behavior_hold), false to stringResource(R.string.behavior_drop)),
            selected = s.holdWhilePaused,
            onSelect = { hold -> update { it.copy(holdWhilePaused = hold) } },
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = stringResource(R.string.combine_title),
            checked = s.combineHeld,
            onChange = { on -> update { it.copy(combineHeld = on) } },
            description = stringResource(R.string.combine_desc),
            icon = Icons.AutoMirrored.Rounded.MergeType,
            color = colors.held,
        )
    }
}

// ------------------------------------------------------------------ safety & notifications

@Composable
private fun SafetySection(s: AppSettings) {
    val colors = Halaa.colors
    SettingsCard(stringResource(R.string.safety_title), Icons.Rounded.Shield, colors.success) {
        Text(stringResource(R.string.limit_title), style = MaterialTheme.typography.titleSmall, color = colors.ink)
        Text(stringResource(R.string.limit_desc), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        Spacer(Modifier.height(10.dp))
        ChoiceChips(
            options = listOf(25, 50, 100, 200, 0).map { it to if (it == 0) stringResource(R.string.limit_none) else it.toString() },
            selected = s.dailyLimit,
            onSelect = { v -> update { it.copy(dailyLimit = v) } },
            color = colors.success,
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = stringResource(R.string.loop_title),
            checked = s.loopGuard,
            onChange = { on -> update { it.copy(loopGuard = on) } },
            description = stringResource(R.string.loop_desc),
            icon = Icons.Rounded.Loop,
            color = colors.success,
        )
        Spacer(Modifier.height(8.dp))
        var prefix by remember(s.replyPrefix) { mutableStateOf(s.replyPrefix) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Tune, colors.success)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.reply_prefix_title), style = MaterialTheme.typography.titleSmall, color = colors.ink)
                Text(stringResource(R.string.reply_prefix_desc), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
            }
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = prefix,
                onValueChange = { value ->
                    prefix = value.take(3)
                    val clean = value.trim().take(3)
                    if (clean.isNotEmpty()) update { it.copy(replyPrefix = clean) }
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(),
                modifier = Modifier.width(76.dp),
            )
        }
    }
}

@Composable
private fun NotificationsSection(s: AppSettings) {
    val colors = Halaa.colors
    val context = LocalContext.current
    SettingsCard(stringResource(R.string.notif_settings_title), Icons.Rounded.NotificationsActive, colors.brand) {
        SwitchRow(
            title = stringResource(R.string.notif_each),
            checked = s.notifyEachForward,
            onChange = { on -> update { it.copy(notifyEachForward = on) } },
            description = stringResource(R.string.notif_each_desc),
        )
        SwitchRow(
            title = stringResource(R.string.notif_problems),
            checked = s.notifyProblems,
            onChange = { on -> update { it.copy(notifyProblems = on) } },
            description = stringResource(R.string.notif_problems_desc),
        )
        NavRow(
            title = stringResource(R.string.notif_system),
            description = stringResource(R.string.notif_system_desc),
            onClick = { Permissions.open(context, Permissions.notificationSettingsIntent(context)) },
        )
    }
}

@Composable
private fun SimSection(s: AppSettings, sims: List<SimCard>, granted: Boolean, onGrant: () -> Unit) {
    val colors = Halaa.colors
    SettingsCard(stringResource(R.string.sim_title), Icons.Rounded.SimCard, colors.route(2)) {
        if (!granted) {
            Text(stringResource(R.string.sim_permission_desc), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
            Spacer(Modifier.height(10.dp))
            InlineAction(stringResource(R.string.grant_access), Icons.Rounded.LockOpen, onGrant, color = colors.route(2))
        } else {
            Text(stringResource(R.string.sim_default_desc), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
            Spacer(Modifier.height(10.dp))
            ChoiceChips(
                options = listOf(-1 to stringResource(R.string.sim_system_default)) + sims.map { it.subId to it.label },
                selected = s.defaultSubId,
                onSelect = { v -> update { it.copy(defaultSubId = v) } },
                color = colors.route(2),
            )
        }
    }
}

// ------------------------------------------------------------------ privacy, backup, appearance, help

@Composable
private fun PrivacySection(s: AppSettings, onClear: () -> Unit) {
    val colors = Halaa.colors
    SettingsCard(stringResource(R.string.privacy_title), Icons.Rounded.Lock, colors.inkSoft) {
        SwitchRow(
            title = stringResource(R.string.hide_bodies),
            checked = s.hideBodies,
            onChange = { on -> update { it.copy(hideBodies = on) } },
            description = stringResource(R.string.hide_bodies_desc),
        )
        Label(stringResource(R.string.retention_title))
        ChoiceChips(
            options = listOf(7, 30, 90, 0).map {
                it to if (it == 0) stringResource(R.string.retention_forever) else stringResource(R.string.retention_days, it)
            },
            selected = s.retentionDays,
            onSelect = { v -> update { it.copy(retentionDays = v) } },
            color = colors.ink,
        )
        Spacer(Modifier.height(6.dp))
        NavRow(
            title = stringResource(R.string.log_clear),
            onClick = onClear,
            icon = Icons.Rounded.DeleteSweep,
            color = colors.danger,
            titleColor = colors.danger,
        )
    }
}

@Composable
private fun BackupSection(onExport: () -> Unit, onImport: () -> Unit) {
    val colors = Halaa.colors
    SettingsCard(stringResource(R.string.backup_title), Icons.Rounded.Backup, colors.route(1)) {
        Text(stringResource(R.string.backup_desc), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        Spacer(Modifier.height(4.dp))
        NavRow(stringResource(R.string.backup_export), onClick = onExport, icon = Icons.Rounded.Upload, color = colors.route(1))
        NavRow(stringResource(R.string.backup_import), onClick = onImport, icon = Icons.Rounded.Download, color = colors.route(1))
    }
}

@Composable
private fun AppearanceSection(s: AppSettings) {
    SettingsCard(stringResource(R.string.appearance_title), Icons.Rounded.Palette, Halaa.colors.route(3)) {
        Segmented(
            options = listOf(
                ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                ThemeMode.LIGHT to stringResource(R.string.theme_light),
                ThemeMode.DARK to stringResource(R.string.theme_dark),
            ),
            selected = s.themeMode,
            onSelect = { mode -> update { it.copy(themeMode = mode) } },
        )
    }
}

@Composable
private fun HelpSection(issues: Int, onOpenHealth: () -> Unit, onReplayOnboarding: () -> Unit) {
    val colors = Halaa.colors
    val context = LocalContext.current
    SettingsCard(stringResource(R.string.help_title), Icons.AutoMirrored.Rounded.HelpOutline, colors.brand) {
        NavRow(
            title = stringResource(R.string.health_title),
            description = if (issues == 0) {
                stringResource(R.string.health_all_good_short)
            } else {
                pluralText(R.plurals.health_issues, issues, issues)
            },
            onClick = onOpenHealth,
            icon = Icons.Rounded.MonitorHeart,
            color = if (issues == 0) colors.success else colors.warning,
        )
        NavRow(
            title = stringResource(R.string.replay_onboarding),
            onClick = onReplayOnboarding,
            icon = Icons.Rounded.AutoAwesome,
        )
        NavRow(
            title = stringResource(R.string.app_info),
            onClick = { Permissions.open(context, Permissions.appDetailsIntent(context)) },
            icon = Icons.Rounded.Info,
        )
    }
}

@Composable
private fun AboutFooter() {
    val colors = Halaa.colors
    val context = LocalContext.current
    val version = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (e: Exception) {
            null
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.brand),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_stat_halaa), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.about_version, version ?: "1.0"),
            style = MaterialTheme.typography.labelLarge,
            color = colors.inkSoft,
        )
        Text(
            stringResource(R.string.about_privacy),
            style = MaterialTheme.typography.bodySmall,
            color = colors.inkFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ImportDialog(parsed: Backup.Parsed, onApply: (replace: Boolean) -> Unit, onDismiss: () -> Unit) {
    val colors = Halaa.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(stringResource(R.string.import_title), style = MaterialTheme.typography.titleLarge) },
        text = {
            Text(
                pluralText(R.plurals.import_text, parsed.routes.size, parsed.routes.size),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inkSoft,
            )
        },
        confirmButton = {
            Row {
                TextButton(onClick = { onApply(true) }) { Text(stringResource(R.string.import_replace), color = colors.danger) }
                TextButton(onClick = { onApply(false) }) { Text(stringResource(R.string.import_add), color = colors.brand) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = colors.inkSoft) }
        },
    )
}

// ------------------------------------------------------------------ backup io

private fun toast(context: Context, message: Int) {
    Toast.makeText(context, context.getString(message), Toast.LENGTH_SHORT).show()
}

private suspend fun exportTo(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        val json = Backup.export(Graph.db.routes().all(), Graph.settings.value)
        val stream = context.contentResolver.openOutputStream(uri) ?: error("no output stream")
        stream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
    }.isSuccess
}

private suspend fun readBackup(context: Context, uri: Uri): Backup.Parsed? = withContext(Dispatchers.IO) {
    runCatching {
        val stream = context.contentResolver.openInputStream(uri) ?: error("no input stream")
        val text = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        Backup.parse(text)
    }.getOrNull()
}

private suspend fun applyImport(parsed: Backup.Parsed, replace: Boolean) {
    val dao = Graph.db.routes()
    if (replace) dao.deleteAll()
    var order = dao.maxSortOrder()
    parsed.routes.forEach { route ->
        order += 1
        dao.insert(route.copy(id = 0L, sortOrder = order))
    }
    parsed.settings?.let { imported ->
        Graph.settings.update { current ->
            imported.copy(
                masterEnabled = current.masterEnabled,
                pausedUntil = current.pausedUntil,
                defaultSubId = current.defaultSubId,
                onboardingDone = current.onboardingDone,
                lastCleanupAt = current.lastCleanupAt,
            )
        }
    }
}
