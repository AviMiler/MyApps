package com.myappstore.smsforwarder.ui.components

import android.util.LruCache
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.core.Phones
import com.myappstore.smsforwarder.core.SourceMode
import com.myappstore.smsforwarder.data.Party
import com.myappstore.smsforwarder.data.Route
import com.myappstore.smsforwarder.ui.theme.Halaa
import com.myappstore.smsforwarder.ui.theme.HalaaColors
import com.myappstore.smsforwarder.ui.theme.Secular
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

// ------------------------------------------------------------------ avatars

private val photoCache = LruCache<String, ImageBitmap>(96)

@Composable
fun rememberContactPhoto(uri: String?): ImageBitmap? {
    if (uri.isNullOrBlank()) return null
    val photo by produceState(initialValue = photoCache.get(uri), uri) {
        if (value == null) {
            val loaded = withContext(Dispatchers.IO) { Graph.contacts.loadPhoto(uri)?.asImageBitmap() }
            if (loaded != null) {
                photoCache.put(uri, loaded)
                value = loaded
            }
        }
    }
    return photo
}

fun monogram(label: String): String {
    val words = label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && it.first().isLetter() }
    if (words.isEmpty()) return "#"
    val first = words.first().first()
    // Hebrew avatars read best with a single letter.
    if (first in '֐'..'׿' || words.size == 1) return first.uppercase()
    return (first.toString() + words[1].first()).uppercase()
}

fun avatarColor(label: String, colors: HalaaColors): Color =
    colors.routes[Math.floorMod(Phones.key(label).hashCode(), colors.routes.size)]

@Composable
fun Avatar(
    label: String,
    photoUri: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Color? = null,
    icon: ImageVector? = null,
    tint: Color? = null,
) {
    val colors = Halaa.colors
    val accent = tint ?: avatarColor(label, colors)
    val photo = rememberContactPhoto(photoUri)
    val fallbackIcon = icon ?: if (label.none { it.isLetter() }) Icons.Rounded.Phone else null
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(ring ?: Color.Transparent)
            .padding(if (ring != null) 2.dp else 0.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .background(accent.copy(alpha = if (colors.isDark) 0.28f else 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            photo != null -> Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            fallbackIcon != null -> Icon(
                fallbackIcon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(size * 0.5f),
            )
            else -> Text(
                monogram(label),
                color = accent,
                maxLines = 1,
                style = TextStyle(fontFamily = Secular, fontSize = (size.value * 0.42f).sp),
            )
        }
    }
}

/** Overlapping avatars like "stations" on a line. */
@Composable
fun PartyStack(parties: List<Party>, color: Color, max: Int, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    val shown = parties.take(max)
    val extra = parties.size - shown.size
    val step = size * 0.68f
    val count = shown.size + if (extra > 0) 1 else 0
    Box(modifier.width(size + step * (count - 1).coerceAtLeast(0).toFloat()).height(size)) {
        shown.forEachIndexed { index, party ->
            Avatar(
                label = party.label,
                photoUri = party.photoUri,
                size = size,
                ring = Halaa.colors.surface,
                modifier = Modifier.offset(x = step * index.toFloat()),
            )
        }
        if (extra > 0) {
            Box(
                Modifier
                    .offset(x = step * shown.size.toFloat())
                    .size(size)
                    .clip(CircleShape)
                    .background(Halaa.colors.surface)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text("+$extra", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun PartyChip(party: Party, onRemove: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(Halaa.colors.surfaceAlt)
            .border(1.dp, Halaa.colors.outline, CircleShape)
            .padding(start = 4.dp, end = if (onRemove != null) 6.dp else 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(party.label, party.photoUri, 30.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                party.label,
                style = MaterialTheme.typography.labelLarge,
                color = Halaa.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!party.name.isNullOrBlank() && party.name != party.address) {
                Text(
                    Phones.pretty(party.address),
                    style = MaterialTheme.typography.labelSmall,
                    color = Halaa.colors.inkSoft,
                    maxLines = 1,
                )
            }
        }
        if (onRemove != null) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Rounded.Close,
                contentDescription = null,
                tint = Halaa.colors.inkSoft,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove)
                    .padding(3.dp),
            )
        }
    }
}

// ------------------------------------------------------------------ route lines

/** A transit line with "messages" gliding from the start side to the end side. */
@Composable
fun FlowLine(color: Color, active: Boolean, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress: State<Float>? = if (active) {
        rememberInfiniteTransition(label = "flow").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "flowProgress",
        )
    } else {
        null
    }
    Canvas(modifier) {
        val y = size.height / 2f
        val stroke = 4.dp.toPx()
        val dir = if (rtl) -1f else 1f
        drawLine(
            color = color.copy(alpha = if (active) 0.3f else 0.2f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            pathEffect = if (active) null else PathEffect.dashPathEffect(floatArrayOf(stroke * 2f, stroke * 2.5f)),
        )
        val tip = Offset(if (rtl) 0f else size.width, y)
        val head = 7.dp.toPx()
        val headColor = color.copy(alpha = if (active) 0.95f else 0.35f)
        drawLine(headColor, tip, Offset(tip.x - dir * head, y - head * 0.75f), stroke, StrokeCap.Round)
        drawLine(headColor, tip, Offset(tip.x - dir * head, y + head * 0.75f), stroke, StrokeCap.Round)
        val p = progress?.value ?: return@Canvas
        for (i in 0 until 3) {
            val f = (p + i / 3f) % 1f
            val x = if (rtl) size.width * (1f - f) else size.width * f
            val alpha = sin(f * PI).toFloat().coerceIn(0f, 1f)
            drawCircle(color.copy(alpha = alpha), radius = stroke * 1.3f, center = Offset(x, y))
        }
    }
}

/** Senders → animated line → recipients, the visual signature of a route card. */
@Composable
fun RouteDiagram(route: Route, color: Color, active: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        when (route.sourceMode) {
            SourceMode.UNKNOWN -> Avatar("", null, 38.dp, icon = Icons.Rounded.PersonSearch, tint = color)
            SourceMode.EVERYONE -> Avatar("", null, 38.dp, icon = Icons.Rounded.AllInclusive, tint = color)
            else -> PartyStack(route.sources, color, max = 3)
        }
        FlowLine(
            color = color,
            active = active,
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .padding(horizontal = 10.dp),
        )
        PartyStack(route.destinations, color, max = 2)
    }
}

@Composable
fun StepBadge(number: Int, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = Color.White, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * One stop of the route editor's "journey": a numbered station on a vertical line,
 * with its settings in a card beside it.
 */
@Composable
fun JourneyStep(
    number: Int,
    title: String,
    color: Color,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val lineColor = color.copy(alpha = 0.35f)
    Row(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val railCenter = 20.dp.toPx()
                val x = if (rtl) size.width - railCenter else railCenter
                val dotY = 33.dp.toPx()
                val width = 3.dp.toPx()
                if (!isFirst) drawLine(lineColor, Offset(x, 0f), Offset(x, dotY), width)
                if (!isLast) drawLine(lineColor, Offset(x, dotY), Offset(x, size.height), width)
            },
    ) {
        Box(Modifier.width(40.dp).padding(top = 18.dp), contentAlignment = Alignment.TopCenter) {
            StepBadge(number, color)
        }
        Column(Modifier.weight(1f).padding(bottom = 14.dp)) {
            HalaaCard(Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = Halaa.colors.ink)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Halaa.colors.inkSoft)
                }
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

// ------------------------------------------------------------------ message preview

/** How the forwarded SMS will look on the recipient's phone. */
@Composable
fun SmsPreview(text: String, fromLabel: String, modifier: Modifier = Modifier) {
    val colors = Halaa.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(colors.sunken)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(fromLabel, null, 26.dp, icon = Icons.Rounded.Phone, tint = colors.brand)
            Spacer(Modifier.width(8.dp))
            Text(fromLabel, style = MaterialTheme.typography.labelMedium, color = colors.inkSoft)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(colors.surface)
                .border(
                    1.dp,
                    colors.outline,
                    RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
        }
    }
}

// ------------------------------------------------------------------ illustration

/**
 * The app's signature art: two sender stations whose lines merge and run to a
 * recipient terminal, with messages travelling along the route.
 */
@Composable
fun RouteIllustration(modifier: Modifier = Modifier, animate: Boolean = true) {
    val colors = Halaa.colors
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "illustration").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
            label = "illustrationProgress",
        )
    } else {
        null
    }
    val pulse: State<Float>? = if (animate) {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
            label = "pulseProgress",
        )
    } else {
        null
    }
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        fun x(fraction: Float) = if (rtl) w * (1f - fraction) else w * fraction
        val stroke = h * 0.07f

        val main = Offset(x(0.12f), h * 0.26f)
        val second = Offset(x(0.10f), h * 0.80f)
        val merge = Offset(x(0.50f), h * 0.52f)
        val terminal = Offset(x(0.86f), h * 0.60f)

        val mainPath = Path().apply {
            moveTo(main.x, main.y)
            cubicTo(x(0.34f), h * 0.26f, x(0.34f), h * 0.52f, merge.x, merge.y)
            cubicTo(x(0.64f), h * 0.52f, x(0.70f), h * 0.60f, terminal.x, terminal.y)
        }
        val branchPath = Path().apply {
            moveTo(second.x, second.y)
            cubicTo(x(0.30f), h * 0.80f, x(0.34f), h * 0.52f, merge.x, merge.y)
        }

        drawPath(branchPath, colors.route(1), style = Stroke(width = stroke * 0.8f, cap = StrokeCap.Round))
        drawPath(mainPath, colors.brand, style = Stroke(width = stroke, cap = StrokeCap.Round))

        messageBubble(Offset(main.x, main.y - h * 0.17f), h * 0.2f, colors, rtl)
        station(main, stroke, colors.brand, colors.surface)
        station(second, stroke * 0.85f, colors.route(1), colors.surface)
        station(merge, stroke * 0.7f, colors.brand, colors.surface)

        pulse?.value?.let { p ->
            drawCircle(colors.brand.copy(alpha = (1f - p) * 0.35f), radius = stroke * (1.2f + p * 1.8f), center = terminal)
        }
        drawCircle(colors.brand, radius = stroke * 1.25f, center = terminal)
        drawCircle(Color.White, radius = stroke * 0.45f, center = terminal)

        val p = progress?.value ?: return@Canvas
        val measure = PathMeasure()
        measure.setPath(mainPath, false)
        val length = measure.length
        for (i in 0 until 3) {
            val f = (p + i / 3f) % 1f
            val position = measure.getPosition(length * f)
            drawCircle(Color.White, radius = stroke * 0.32f, center = position)
        }
        val branchMeasure = PathMeasure()
        branchMeasure.setPath(branchPath, false)
        val f = (p * 1.3f + 0.4f) % 1f
        drawCircle(Color.White, radius = stroke * 0.26f, center = branchMeasure.getPosition(branchMeasure.length * f))
    }
}

private fun DrawScope.station(center: Offset, stroke: Float, color: Color, fill: Color) {
    drawCircle(fill, radius = stroke * 1.05f, center = center)
    drawCircle(color, radius = stroke * 1.05f, center = center, style = Stroke(width = stroke * 0.55f))
}

private fun DrawScope.messageBubble(anchor: Offset, height: Float, colors: HalaaColors, rtl: Boolean) {
    val width = height * 1.7f
    val left = if (rtl) anchor.x - width * 0.75f else anchor.x - width * 0.25f
    val top = anchor.y - height / 2f
    drawRoundRect(
        color = colors.surface,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(height * 0.45f, height * 0.45f),
    )
    drawRoundRect(
        color = colors.outline,
        topLeft = Offset(left, top),
        size = Size(width, height),
        cornerRadius = CornerRadius(height * 0.45f, height * 0.45f),
        style = Stroke(width = height * 0.05f),
    )
    for (i in 0 until 3) {
        drawCircle(
            colors.inkFaint,
            radius = height * 0.08f,
            center = Offset(left + width * (0.3f + i * 0.2f), anchor.y),
        )
    }
}
