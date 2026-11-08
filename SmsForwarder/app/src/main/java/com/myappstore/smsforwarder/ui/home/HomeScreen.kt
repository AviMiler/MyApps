@file:OptIn(ExperimentalLayoutApi::class)

package com.myappstore.smsforwarder.ui.home

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.core.Hebrew
import com.myappstore.smsforwarder.core.HoldPolicy
import com.myappstore.smsforwarder.core.RestCalendar
import com.myappstore.smsforwarder.core.SourceMode
import com.myappstore.smsforwarder.data.AppSettings
import com.myappstore.smsforwarder.data.ForwardEvent
import com.myappstore.smsforwarder.data.Route
import com.myappstore.smsforwarder.data.RouteStat
import com.myappstore.smsforwarder.engine.IsraeliHolidays
import com.myappstore.smsforwarder.engine.Permissions
import com.myappstore.smsforwarder.engine.Texts
import com.myappstore.smsforwarder.ui.Fmt
import com.myappstore.smsforwarder.ui.components.BigSwitch
import com.myappstore.smsforwarder.ui.components.ConfirmDialog
import com.myappstore.smsforwarder.ui.components.EmptyState
import com.myappstore.smsforwarder.ui.components.HalaaCard
import com.myappstore.smsforwarder.ui.components.IconBadge
import com.myappstore.smsforwarder.ui.components.InfoCard
import com.myappstore.smsforwarder.ui.components.InlineAction
import com.myappstore.smsforwarder.ui.components.LoadingBox
import com.myappstore.smsforwarder.ui.components.LocalResumeTick
import com.myappstore.smsforwarder.ui.components.Pill
import com.myappstore.smsforwarder.ui.components.PrimaryButton
import com.myappstore.smsforwarder.ui.components.RouteDiagram
import com.myappstore.smsforwarder.ui.components.RouteIllustration
import com.myappstore.smsforwarder.ui.components.animationsEnabled
import com.myappstore.smsforwarder.ui.components.SectionHeader
import com.myappstore.smsforwarder.ui.components.pluralText
import com.myappstore.smsforwarder.ui.components.rememberNow
import com.myappstore.smsforwarder.ui.components.switchColors
import com.myappstore.smsforwarder.ui.theme.Halaa
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.sin

enum class HeroMode { SETUP, OFF, PAUSED, REST, QUIET, ACTIVE }

@Composable
fun HomeScreen(
    contentPadding: PaddingValues,
    onOpenRoute: (Long?) -> Unit,
    onOpenHealth: () -> Unit,
    onFixPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Graph.settings.flow.collectAsState()
    val routes by remember { Graph.db.routes().observeAll() }.collectAsState(initial = null)
    val stats by remember { Graph.db.events().observeRouteStats() }.collectAsState(initial = emptyList())
    val waiting by remember { Graph.db.events().observeWaiting() }.collectAsState(initial = emptyList())
    val now = rememberNow()
    val todayStart = remember(Fmt.zoned(now).toLocalDate()) { Fmt.startOfToday() }
    val weekStart = remember(todayStart) { Fmt.startOfWeek() }
    val today by remember(todayStart) { Graph.db.events().observeSentSince(todayStart) }.collectAsState(initial = 0)
    val week by remember(weekStart) { Graph.db.events().observeSentSince(weekStart) }.collectAsState(initial = 0)
    val problems by remember(todayStart) { Graph.db.events().observeProblemsSince(todayStart) }.collectAsState(initial = 0)
    val tick = LocalResumeTick.current
    val ready = remember(tick) { Permissions.coreReady(context) }

    val zone = ZoneId.systemDefault()
    val minute = now / 60_000L
    val restWindow = remember(settings, minute) {
        settings.restCalendar(zone, IsraeliHolidays::isYomTov)?.activeAt(Fmt.zoned(now))
    }
    val quietEnd = remember(settings, minute) {
        settings.quietWindow()?.occurrenceAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone))?.second
    }
    val mode = when {
        !ready -> HeroMode.SETUP
        !settings.masterEnabled -> HeroMode.OFF
        settings.isPaused(now) -> HeroMode.PAUSED
        restWindow != null -> HeroMode.REST
        quietEnd != null -> HeroMode.QUIET
        else -> HeroMode.ACTIVE
    }
    val activeRoutes = routes.orEmpty().count { it.enabled }
    var confirmCancelAll by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            HomeHeader(now = now, restWindow = restWindow, healthIssue = !ready, onOpenHealth = onOpenHealth)
        }
        item(key = "hero") {
            StatusHero(
                mode = mode,
                settings = settings,
                today = today,
                week = week,
                problems = problems,
                activeRoutes = activeRoutes,
                restWindow = restWindow,
                quietEnd = quietEnd?.atZone(zone)?.toInstant()?.toEpochMilli(),
                now = now,
                onToggle = { on -> Graph.settings.update { it.copy(masterEnabled = on, pausedUntil = if (on) 0L else it.pausedUntil) } },
                onFix = onFixPermissions,
            )
        }
        if (mode == HeroMode.ACTIVE || mode == HeroMode.PAUSED || mode == HeroMode.REST || mode == HeroMode.QUIET) {
            item(key = "quick") { QuickActions(mode) }
        }
        if (waiting.isNotEmpty()) {
            item(key = "waiting") {
                WaitingCard(
                    waiting = waiting,
                    now = now,
                    onSendAll = { scope.launch { Graph.engine.releaseAllNow() } },
                    onCancelAll = { confirmCancelAll = true },
                )
            }
        }
        item(key = "routes-header") {
            SectionHeader(
                title = stringResource(R.string.home_routes),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                if (!routes.isNullOrEmpty()) {
                    InlineAction(
                        text = stringResource(R.string.new_route),
                        icon = Icons.Rounded.Add,
                        onClick = { onOpenRoute(null) },
                    )
                }
            }
        }
        val list = routes
        when {
            list == null -> item(key = "loading") { LoadingBox() }
            list.isEmpty() -> item(key = "empty") { EmptyRoutes(onCreate = { onOpenRoute(null) }) }
            else -> {
                items(list, key = { it.id }) { route ->
                    RouteCard(
                        route = route,
                        stat = stats.firstOrNull { it.routeId == route.id },
                        appActive = mode == HeroMode.ACTIVE,
                        now = now,
                        onOpen = { onOpenRoute(route.id) },
                        onToggle = { enabled -> scope.launch { Graph.db.routes().setEnabled(route.id, enabled) } },
                    )
                }
                item(key = "new-route") { NewRouteCard(onClick = { onOpenRoute(null) }) }
            }
        }
        item(key = "tip") { TipCard(now) }
    }

    if (confirmCancelAll) {
        ConfirmDialog(
            title = stringResource(R.string.cancel_all_title),
            text = stringResource(R.string.cancel_all_text),
            confirm = stringResource(R.string.cancel_all_confirm),
            danger = true,
            onConfirm = {
                confirmCancelAll = false
                scope.launch { Graph.engine.cancelAllWaiting() }
            },
            onDismiss = { confirmCancelAll = false },
        )
    }
}

// ------------------------------------------------------------------ header

@Composable
private fun HomeHeader(now: Long, restWindow: RestCalendar.Window?, healthIssue: Boolean, onOpenHealth: () -> Unit) {
    val colors = Halaa.colors
    val hour = Fmt.zoned(now).hour
    val greeting = stringResource(
        when {
            restWindow?.includesHoliday == true -> R.string.greeting_holiday
            restWindow != null -> R.string.greeting_rest
            hour < 5 -> R.string.greeting_night
            hour < 12 -> R.string.greeting_morning
            hour < 17 -> R.string.greeting_noon
            hour < 21 -> R.string.greeting_evening
            else -> R.string.greeting_night
        },
    )
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFFF9150), colors.brand, colors.brandDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_stat_halaa),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.labelLarge, color = colors.inkSoft)
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge, color = colors.ink)
        }
        Box {
            IconButton(onClick = onOpenHealth) {
                Icon(
                    Icons.Rounded.MonitorHeart,
                    contentDescription = stringResource(R.string.health_title),
                    tint = if (healthIssue) colors.warning else colors.inkSoft,
                )
            }
            if (healthIssue) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(colors.danger),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ hero

@Composable
private fun StatusHero(
    mode: HeroMode,
    settings: AppSettings,
    today: Int,
    week: Int,
    problems: Int,
    activeRoutes: Int,
    restWindow: RestCalendar.Window?,
    quietEnd: Long?,
    now: Long,
    onToggle: (Boolean) -> Unit,
    onFix: () -> Unit,
) {
    val colors = Halaa.colors
    val context = LocalContext.current
    val background: Brush = when (mode) {
        HeroMode.ACTIVE -> Brush.linearGradient(listOf(Color(0xFFFF9150), colors.brand, colors.brandDeep))
        HeroMode.PAUSED -> Brush.linearGradient(listOf(Color(0xFF8A88F5), colors.held, Color(0xFF3F3DB8)))
        HeroMode.REST, HeroMode.QUIET -> Brush.linearGradient(listOf(colors.night, colors.nightDeep))
        HeroMode.OFF -> SolidColor(colors.sunken)
        HeroMode.SETUP -> Brush.linearGradient(listOf(Color(0xFFFFB547), Color(0xFFE08A00)))
    }
    val onHero = if (mode == HeroMode.OFF) colors.ink else Color.White
    val softOnHero = onHero.copy(alpha = 0.8f)

    val tag = stringResource(
        when (mode) {
            HeroMode.ACTIVE -> R.string.hero_tag_active
            HeroMode.PAUSED -> R.string.hero_tag_paused
            HeroMode.REST -> if (restWindow?.includesHoliday == true) R.string.hero_tag_holiday else R.string.hero_tag_shabbat
            HeroMode.QUIET -> R.string.hero_tag_quiet
            HeroMode.OFF -> R.string.hero_tag_off
            HeroMode.SETUP -> R.string.hero_tag_setup
        },
    )
    val title = stringResource(
        when (mode) {
            HeroMode.ACTIVE -> R.string.hero_title_active
            HeroMode.PAUSED -> R.string.hero_title_paused
            HeroMode.REST -> if (restWindow?.includesHoliday == true) R.string.hero_title_holiday else R.string.hero_title_shabbat
            HeroMode.QUIET -> R.string.hero_title_quiet
            HeroMode.OFF -> R.string.hero_title_off
            HeroMode.SETUP -> R.string.hero_title_setup
        },
    )
    val subtitle = when (mode) {
        HeroMode.ACTIVE -> if (activeRoutes == 0) {
            stringResource(R.string.hero_sub_no_routes)
        } else {
            pluralText(R.plurals.hero_sub_active, activeRoutes, activeRoutes)
        }
        HeroMode.PAUSED -> Fmt.until(context, settings.pausedUntil, now) + " · " +
            stringResource(if (settings.holdWhilePaused) R.string.hero_sub_paused_hold else R.string.hero_sub_paused_drop)
        HeroMode.REST -> stringResource(
            R.string.hero_sub_rest,
            Fmt.until(context, restWindow?.end?.toInstant()?.toEpochMilli() ?: now, now),
        )
        HeroMode.QUIET -> stringResource(
            if (settings.holdDuringQuiet) R.string.hero_sub_quiet_hold else R.string.hero_sub_quiet_drop,
            Fmt.until(context, quietEnd ?: now, now),
        )
        HeroMode.OFF -> stringResource(R.string.hero_sub_off)
        HeroMode.SETUP -> stringResource(R.string.hero_sub_setup)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(background),
    ) {
        HeroBackdrop(mode, animationsEnabled(), Modifier.matchParentSize())
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusTag(tag, onHero, pulsing = mode == HeroMode.ACTIVE && animationsEnabled())
                Spacer(Modifier.weight(1f))
                if (mode != HeroMode.SETUP) {
                    BigSwitch(
                        checked = settings.masterEnabled,
                        onChange = onToggle,
                        trackOn = Color.White.copy(alpha = 0.32f),
                        trackOff = if (mode == HeroMode.OFF) colors.outline else Color.White.copy(alpha = 0.2f),
                        thumb = if (mode == HeroMode.OFF) colors.surface else Color.White,
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineLarge, color = onHero)
                    Spacer(Modifier.height(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = softOnHero)
                }
                HeroEmblem(mode)
            }
            if (mode == HeroMode.SETUP) {
                Spacer(Modifier.height(18.dp))
                PrimaryButton(
                    text = stringResource(R.string.hero_fix),
                    onClick = onFix,
                    color = Color.White,
                    contentColor = Color(0xFFB36A00),
                )
            } else {
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        targetState = today,
                        transitionSpec = {
                            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
                        },
                        label = "today",
                    ) { value ->
                        Text(value.toString(), style = MaterialTheme.typography.displayLarge, color = onHero)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.hero_today),
                        style = MaterialTheme.typography.titleSmall,
                        color = softOnHero,
                        modifier = Modifier.padding(bottom = 14.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    HeroStat(week, stringResource(R.string.hero_week), onHero)
                    if (problems > 0) {
                        Spacer(Modifier.width(18.dp))
                        HeroStat(problems, stringResource(R.string.hero_problems), onHero)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTag(text: String, color: Color, pulsing: Boolean) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "tag")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "tagPulse",
        )
        value
    } else {
        1f
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = alpha)),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun HeroStat(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 10.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.headlineMedium, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.75f))
    }
}

/** Decorative layer: transit lines (dashed while paused), or a night sky during Shabbat and quiet hours. */
@Composable
private fun HeroBackdrop(mode: HeroMode, animate: Boolean, modifier: Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    when (mode) {
        HeroMode.ACTIVE, HeroMode.PAUSED -> {
            val paused = mode == HeroMode.PAUSED
            // State objects are read inside the Canvas so only drawing repeats every frame.
            val progress: State<Float>? = if (animate && !paused) {
                rememberInfiniteTransition(label = "backdrop").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(5200)),
                    label = "backdropProgress",
                )
            } else {
                null
            }
            Canvas(modifier) {
                val w = size.width
                val h = size.height
                fun x(f: Float) = if (rtl) w * (1f - f) else w * f
                val line = Path().apply {
                    moveTo(x(-0.05f), h * 0.92f)
                    cubicTo(x(0.35f), h * 0.92f, x(0.45f), h * 0.45f, x(1.05f), h * 0.4f)
                }
                val second = Path().apply {
                    moveTo(x(0.25f), h * 1.05f)
                    cubicTo(x(0.5f), h * 0.75f, x(0.7f), h * 0.85f, x(1.05f), h * 0.7f)
                }
                val dashes = if (paused) PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 14.dp.toPx())) else null
                drawPath(
                    line,
                    Color.White.copy(alpha = 0.14f),
                    style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes),
                )
                drawPath(
                    second,
                    Color.White.copy(alpha = 0.08f),
                    style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, pathEffect = dashes),
                )
                val p = progress?.value ?: return@Canvas
                val measure = androidx.compose.ui.graphics.PathMeasure()
                measure.setPath(line, false)
                for (i in 0 until 2) {
                    val f = (p + i * 0.5f) % 1f
                    val alpha = sin(f * Math.PI).toFloat() * 0.6f
                    drawCircle(Color.White.copy(alpha = alpha), radius = 4.dp.toPx(), center = measure.getPosition(measure.length * f))
                }
            }
        }
        HeroMode.REST, HeroMode.QUIET -> {
            val twinkle: State<Float>? = if (animate) {
                rememberInfiniteTransition(label = "stars").animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
                    label = "twinkle",
                )
            } else {
                null
            }
            Canvas(modifier) {
                val w = size.width
                val h = size.height
                val t = twinkle?.value ?: 0.5f
                HeroStars.forEachIndexed { i, star ->
                    val phase = (i * 0.37f) % 1f
                    val alpha = 0.2f + 0.5f * ((sin((t + phase) * Math.PI * 2) + 1) / 2).toFloat()
                    val cx = if (rtl) w * star.x else w * (1f - star.x)
                    drawCircle(Color.White.copy(alpha = alpha), radius = star.radius.dp.toPx(), center = Offset(cx, h * star.y))
                }
            }
        }
        else -> Unit
    }
}

/** A star in the hero's night sky: [x] runs from the card's end edge, so text never sits on a star. */
private class Star(val x: Float, val y: Float, val radius: Float)

/** Hand-placed in the gaps between the switch, the title, the emblem and the numbers. */
private val HeroStars = listOf(
    Star(0.30f, 0.09f, 1.6f), Star(0.37f, 0.19f, 1.1f), Star(0.45f, 0.07f, 2.0f), Star(0.53f, 0.16f, 1.3f),
    Star(0.60f, 0.06f, 1.1f), Star(0.03f, 0.30f, 1.2f), Star(0.035f, 0.55f, 1.6f), Star(0.08f, 0.65f, 1.3f),
    Star(0.19f, 0.61f, 1.1f), Star(0.38f, 0.76f, 1.2f), Star(0.47f, 0.90f, 1.8f), Star(0.55f, 0.74f, 1.1f),
    Star(0.60f, 0.94f, 1.3f), Star(0.975f, 0.30f, 1.2f),
)

/** The illustration beside the hero title: a pause button, a crescent moon, or Shabbat candles. */
@Composable
private fun HeroEmblem(mode: HeroMode) {
    if (mode != HeroMode.PAUSED && mode != HeroMode.QUIET && mode != HeroMode.REST) return
    Spacer(Modifier.width(12.dp))
    Canvas(Modifier.size(64.dp)) {
        val c = center
        when (mode) {
            HeroMode.PAUSED -> {
                drawCircle(Color.White.copy(alpha = 0.16f), radius = size.minDimension / 2)
                val barW = 8.dp.toPx()
                val barH = 26.dp.toPx()
                val gap = 8.dp.toPx()
                listOf(-(gap / 2 + barW), gap / 2).forEach { dx ->
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.92f),
                        topLeft = Offset(c.x + dx, c.y - barH / 2),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2),
                    )
                }
            }
            HeroMode.QUIET -> {
                val moonlight = Color(0xFFFFE7A8)
                val r = 22.dp.toPx()
                drawCircle(moonlight.copy(alpha = 0.10f), radius = r * 1.4f, center = c)
                val moon = Path().apply { addOval(Rect(center = c, radius = r)) }
                val bite = Path().apply {
                    addOval(Rect(center = Offset(c.x + r * 0.55f, c.y - r * 0.25f), radius = r * 0.92f))
                }
                drawPath(Path().apply { op(moon, bite, PathOperation.Difference) }, moonlight.copy(alpha = 0.95f))
            }
            else -> {
                val flameColor = Color(0xFFFFC857)
                val wax = Color(0xFFFFF4DC)
                val candleW = 9.dp.toPx()
                val candleTop = 30.dp.toPx()
                val baseY = 58.dp.toPx()
                val glowCenter = Offset(c.x, 20.dp.toPx())
                drawCircle(
                    Brush.radialGradient(listOf(flameColor.copy(alpha = 0.38f), Color.Transparent), glowCenter, 30.dp.toPx()),
                    radius = 30.dp.toPx(),
                    center = glowCenter,
                )
                listOf(c.x - 10.dp.toPx(), c.x + 10.dp.toPx()).forEach { cx ->
                    drawRoundRect(
                        color = wax,
                        topLeft = Offset(cx - candleW / 2, candleTop),
                        size = Size(candleW, baseY - candleTop),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                    drawLine(
                        Color.White.copy(alpha = 0.7f),
                        Offset(cx, candleTop),
                        Offset(cx, candleTop - 3.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                    drawPath(flame(cx, candleTop - 2.dp.toPx(), 15.dp.toPx(), 4.5.dp.toPx()), flameColor)
                    drawPath(flame(cx, candleTop - 2.dp.toPx(), 7.dp.toPx(), 2.2.dp.toPx()), Color(0xFFFFF6D6))
                }
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset(c.x - 21.dp.toPx(), baseY),
                    size = Size(42.dp.toPx(), 4.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
        }
    }
}

/** A teardrop standing on ([cx], [bottom]). */
private fun flame(cx: Float, bottom: Float, height: Float, halfWidth: Float) = Path().apply {
    val tip = bottom - height
    moveTo(cx, tip)
    cubicTo(cx + halfWidth * 1.2f, tip + height * 0.45f, cx + halfWidth, bottom, cx, bottom)
    cubicTo(cx - halfWidth, bottom, cx - halfWidth * 1.2f, tip + height * 0.45f, cx, tip)
    close()
}

// ------------------------------------------------------------------ quick actions

/** "Resume" while paused; otherwise one-tap pauses: for an hour, until morning, or until resumed. */
@Composable
private fun QuickActions(mode: HeroMode) {
    val colors = Halaa.colors
    if (mode == HeroMode.PAUSED) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(colors.heldSoft)
                .clickable { Graph.settings.update { it.copy(pausedUntil = 0L) } }
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = colors.held, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.resume_now), style = MaterialTheme.typography.titleSmall, color = colors.held)
        }
        return
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Pause, contentDescription = null, tint = colors.inkSoft, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(stringResource(R.string.pause_label), style = MaterialTheme.typography.labelLarge, color = colors.inkSoft)
        Spacer(Modifier.width(12.dp))
        PauseChip(stringResource(R.string.pause_hour), Modifier.weight(1f)) { System.currentTimeMillis() + 60 * 60_000L }
        Spacer(Modifier.width(6.dp))
        PauseChip(stringResource(R.string.pause_morning), Modifier.weight(1f)) { Fmt.nextMorning(System.currentTimeMillis()) }
        Spacer(Modifier.width(6.dp))
        PauseChip(stringResource(R.string.pause_forever), Modifier.weight(1f)) { HoldPolicy.FOREVER }
    }
}

@Composable
private fun PauseChip(label: String, modifier: Modifier, until: () -> Long) {
    val colors = Halaa.colors
    Box(
        modifier
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.outline, CircleShape)
            .clickable { Graph.settings.update { it.copy(pausedUntil = until()) } }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.ink, maxLines = 1)
    }
}

// ------------------------------------------------------------------ waiting messages

@Composable
private fun WaitingCard(
    waiting: List<ForwardEvent>,
    now: Long,
    onSendAll: () -> Unit,
    onCancelAll: () -> Unit,
) {
    val colors = Halaa.colors
    val context = LocalContext.current
    val next = waiting.minOf { it.scheduledAt }
    HalaaCard(Modifier.fillMaxWidth(), color = colors.heldSoft, borderColor = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.HourglassTop, colors.held)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    pluralText(R.plurals.waiting_count, waiting.size, waiting.size),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                )
                Text(
                    if (next == HoldPolicy.FOREVER) {
                        stringResource(R.string.waiting_until_resume)
                    } else {
                        stringResource(R.string.waiting_next, Fmt.at(context, next, now))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        waiting.take(3).forEach { event ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.route(event.routeColor)),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Hebrew.arrow(event.senderLabel, event.recipientLabel),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Texts.reason(context, event.reason, event.detail),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.inkSoft,
                    maxLines = 1,
                )
            }
        }
        if (waiting.size > 3) {
            Text(
                stringResource(R.string.and_more, waiting.size - 3),
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSoft,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InlineAction(stringResource(R.string.send_all_now), Icons.AutoMirrored.Rounded.Send, onSendAll, color = colors.held)
            InlineAction(stringResource(R.string.cancel_all), Icons.Rounded.Close, onCancelAll, color = colors.danger)
        }
    }
}

// ------------------------------------------------------------------ routes

@Composable
private fun RouteCard(
    route: Route,
    stat: RouteStat?,
    appActive: Boolean,
    now: Long,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = Halaa.colors
    val context = LocalContext.current
    val color = colors.route(route.colorIndex)
    val name = route.displayName(
        stringResource(R.string.source_unknown_short),
        stringResource(R.string.source_everyone_short),
    )
    HalaaCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    routeSubtitle(context, route),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.inkSoft,
                    maxLines = 1,
                )
            }
            Switch(checked = route.enabled, onCheckedChange = onToggle, colors = switchColors(color))
        }
        Spacer(Modifier.height(16.dp))
        RouteDiagram(route, color, active = route.enabled && appActive)
        val features = routeFeatures(context, route)
        if (features.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                features.forEach { (icon, label) ->
                    Pill(
                        text = label,
                        color = color,
                        background = color.copy(alpha = if (colors.isDark) 0.2f else 0.1f),
                        icon = icon,
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = colors.outline)
        Spacer(Modifier.height(10.dp))
        Text(
            if (stat != null && stat.count > 0) {
                pluralText(R.plurals.route_stats, stat.count, stat.count, Fmt.ago(context, stat.lastAt, now))
            } else {
                stringResource(R.string.route_no_stats)
            },
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkSoft,
        )
    }
}

private fun routeSubtitle(context: Context, route: Route): String {
    val res = context.resources
    val from = when (route.sourceMode) {
        SourceMode.UNKNOWN -> context.getString(R.string.source_unknown_short)
        SourceMode.EVERYONE -> context.getString(R.string.source_everyone_short)
        else -> res.getQuantityString(R.plurals.senders_count, route.sources.size, route.sources.size)
    }
    val to = res.getQuantityString(R.plurals.recipients_count, route.destinations.size, route.destinations.size)
    return "$from · $to"
}

private fun routeFeatures(context: Context, route: Route): List<Pair<ImageVector, String>> {
    val features = mutableListOf<Pair<ImageVector, String>>()
    if (route.codesOnly) features += Icons.Rounded.Key to context.getString(R.string.feature_codes)
    if (route.includeWords.isNotEmpty()) {
        features += Icons.Rounded.FilterAlt to context.getString(R.string.feature_words, route.includeWords.take(2).joinToString(", "))
    }
    if (route.excludeWords.isNotEmpty()) {
        features += Icons.Rounded.Block to context.getString(R.string.feature_blocked, route.excludeWords.size)
    }
    if (route.scheduleEnabled) {
        features += Icons.Rounded.Schedule to "${Fmt.days(context, route.scheduleDays)} ${Fmt.minutes(route.scheduleStart)}–${Fmt.minutes(route.scheduleEnd)}"
    }
    if (route.delaySeconds > 0) {
        val label = if (route.delaySeconds < 60) {
            context.getString(R.string.delay_seconds, route.delaySeconds)
        } else {
            context.getString(R.string.delay_minutes, route.delaySeconds / 60)
        }
        features += Icons.Rounded.HourglassTop to label
    }
    if (route.replyRelay) features += Icons.AutoMirrored.Rounded.Reply to context.getString(R.string.feature_reply)
    if (route.receiveSubId >= 0 || route.sendSubId >= 0) {
        features += Icons.Rounded.SimCard to context.getString(R.string.feature_sim)
    }
    return features
}

@Composable
private fun EmptyRoutes(onCreate: () -> Unit) {
    HalaaCard(Modifier.fillMaxWidth()) {
        EmptyState(
            title = stringResource(R.string.empty_routes_title),
            body = stringResource(R.string.empty_routes_body),
            illustration = {
                RouteIllustration(
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
            },
            action = {
                PrimaryButton(
                    text = stringResource(R.string.create_first_route),
                    onClick = onCreate,
                    icon = Icons.Rounded.Add,
                )
            },
        )
    }
}

@Composable
private fun NewRouteCard(onClick: () -> Unit) {
    val colors = Halaa.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .drawBehind {
                drawRoundRect(
                    color = colors.inkFaint,
                    cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
                    ),
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = colors.brand)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.new_route), style = MaterialTheme.typography.titleMedium, color = colors.brand)
    }
}

@Composable
private fun TipCard(now: Long) {
    val tips = listOf(R.string.tip_tile, R.string.tip_codes, R.string.tip_reply, R.string.tip_shabbat, R.string.tip_undo)
    val index = Math.floorMod(Fmt.zoned(now).dayOfYear, tips.size)
    InfoCard(
        title = stringResource(R.string.tip_title),
        text = stringResource(tips[index]),
        icon = Icons.Rounded.Lightbulb,
        color = Halaa.colors.held,
        modifier = Modifier.padding(top = 4.dp),
    )
}
