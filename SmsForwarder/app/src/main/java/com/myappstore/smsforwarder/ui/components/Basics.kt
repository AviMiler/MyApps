@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.myappstore.smsforwarder.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.core.Days
import com.myappstore.smsforwarder.engine.Permissions
import com.myappstore.smsforwarder.ui.Fmt
import com.myappstore.smsforwarder.ui.theme.Halaa
import kotlinx.coroutines.delay

/** Increments every time the activity resumes, so permission-derived UI refreshes. */
val LocalResumeTick = compositionLocalOf { 0 }

/** The current time, refreshed every [periodMs] so relative labels stay fresh. */
@Composable
fun rememberNow(periodMs: Long = 30_000L): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(periodMs)
            value = System.currentTimeMillis()
        }
    }
    return now
}

val CardShape = RoundedCornerShape(24.dp)

@Composable
fun HalaaCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = Halaa.colors.surface,
    borderColor: Color? = Halaa.colors.outline,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CardShape)
            .background(color)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, CardShape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Halaa.colors.ink,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = Halaa.colors.ink,
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Halaa.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        )
        actions()
    }
}

@Composable
fun Pill(
    text: String,
    color: Color,
    background: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (Halaa.colors.isDark) 0.22f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** A chunky, tactile switch used on the status hero. */
@Composable
fun BigSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    trackOn: Color,
    trackOff: Color,
    thumb: Color,
    modifier: Modifier = Modifier,
) {
    val offset by animateDpAsState(
        targetValue = if (checked) 28.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "thumb",
    )
    val track by animateColorAsState(if (checked) trackOn else trackOff, label = "track")
    Box(
        modifier
            .size(width = 66.dp, height = 38.dp)
            .clip(CircleShape)
            .background(track)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .size(30.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
                .background(thumb),
        )
    }
}

@Composable
fun switchColors(color: Color = Halaa.colors.brand) = SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = color,
    checkedBorderColor = color,
    uncheckedThumbColor = Halaa.colors.inkFaint,
    uncheckedTrackColor = Halaa.colors.sunken,
    uncheckedBorderColor = Halaa.colors.outline,
)

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    color: Color = Halaa.colors.brand,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, color)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Halaa.colors.ink)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = Halaa.colors.inkSoft)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled, colors = switchColors(color))
    }
}

@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    icon: ImageVector? = null,
    value: String? = null,
    color: Color = Halaa.colors.brand,
    titleColor: Color = Halaa.colors.ink,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            IconBadge(icon, color)
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = Halaa.colors.inkSoft)
            }
        }
        if (value != null) {
            Text(value, style = MaterialTheme.typography.labelLarge, color = Halaa.colors.inkSoft)
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Halaa.colors.inkFaint,
        )
    }
}

/** A pill-shaped segmented control. */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Halaa.colors.sunken)
            .padding(4.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val background by animateColorAsState(
                if (isSelected) Halaa.colors.surface else Color.Transparent,
                label = "segment",
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(background)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Halaa.colors.ink else Halaa.colors.inkSoft,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
fun SelectChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Halaa.colors.brand,
    icon: ImageVector? = null,
) {
    val background by animateColorAsState(if (selected) color else Halaa.colors.surfaceAlt, label = "chip")
    Row(
        modifier
            .clip(CircleShape)
            .background(background)
            .border(1.dp, if (selected) color else Halaa.colors.outline, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Halaa.colors.inkSoft,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else Halaa.colors.ink,
        )
    }
}

@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Halaa.colors.brand,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            SelectChip(label = label, selected = value == selected, onClick = { onSelect(value) }, color = color)
        }
    }
}

/** Seven round day toggles, Sunday first (on the right in RTL). */
@Composable
fun DaysPicker(mask: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, color: Color = Halaa.colors.brand) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (index in 0..6) {
            val on = Days.contains(mask, index)
            val background by animateColorAsState(if (on) color else Halaa.colors.sunken, label = "day")
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onChange(Days.toggle(mask, index)) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    Fmt.dayLetters[index],
                    style = MaterialTheme.typography.titleSmall,
                    color = if (on) Color.White else Halaa.colors.inkSoft,
                )
            }
        }
    }
}

@Composable
fun TimeRangeRow(
    start: Int,
    end: Int,
    onStart: (Int) -> Unit,
    onEnd: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Halaa.colors.brand,
) {
    var editing by remember { mutableStateOf<Int?>(null) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TimeButton(stringResource(R.string.time_from), Fmt.minutes(start), color, Modifier.weight(1f)) { editing = 0 }
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = Halaa.colors.inkFaint,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        TimeButton(stringResource(R.string.time_to), Fmt.minutes(end), color, Modifier.weight(1f)) { editing = 1 }
    }
    val which = editing
    if (which != null) {
        TimePickerDialog(
            initialMinutes = if (which == 0) start else end,
            title = stringResource(if (which == 0) R.string.time_pick_start else R.string.time_pick_end),
            onDismiss = { editing = null },
            onConfirm = { minutes ->
                if (which == 0) onStart(minutes) else onEnd(minutes)
                editing = null
            },
        )
    }
}

@Composable
private fun TimeButton(label: String, value: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Halaa.colors.sunken)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Halaa.colors.inkSoft)
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text(value, style = MaterialTheme.typography.headlineMedium, color = color)
        }
    }
}

@Composable
fun TimePickerDialog(
    initialMinutes: Int,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Halaa.colors.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        text = {
            // Clock faces read left-to-right everywhere.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TimePicker(state = state)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = Halaa.colors.brand,
    contentColor: Color = Color.White,
    height: Dp = 54.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .background(if (enabled) color else Halaa.colors.sunken)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) contentColor else Halaa.colors.inkFaint
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = tint, maxLines = 1)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Halaa.colors.ink,
    enabled: Boolean = true,
    height: Dp = 54.dp,
) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(18.dp))
            .border(BorderStroke(1.5.dp, Halaa.colors.outline), RoundedCornerShape(18.dp))
            .background(Halaa.colors.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val tint = if (enabled) color else Halaa.colors.inkFaint
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = tint, maxLines = 1)
    }
}

/** A small text button with an icon, used for inline actions in cards. */
@Composable
fun InlineAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Halaa.colors.brand,
) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = if (Halaa.colors.isDark) 0.2f else 0.1f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
fun InfoCard(
    text: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = if (Halaa.colors.isDark) 0.16f else 0.1f))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Halaa.colors.ink)
                Spacer(Modifier.height(2.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = Halaa.colors.inkSoft)
            if (action != null) {
                Spacer(Modifier.height(10.dp))
                action()
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false,
    dismiss: String = stringResource(R.string.cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Halaa.colors.surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = Halaa.colors.ink) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = Halaa.colors.inkSoft) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (danger) Halaa.colors.danger else Halaa.colors.brand)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismiss, color = Halaa.colors.inkSoft) }
        },
    )
}

/** Big, friendly empty state with an illustration slot. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    illustration: @Composable () -> Unit = {},
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        illustration()
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = Halaa.colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = Halaa.colors.inkSoft,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Halaa.colors.brand,
    unfocusedBorderColor = Halaa.colors.outline,
    focusedContainerColor = Halaa.colors.surface,
    unfocusedContainerColor = Halaa.colors.surface,
    cursorColor = Halaa.colors.brand,
    focusedLabelColor = Halaa.colors.brand,
)

/** Quantity strings (Hebrew has one/two/many forms). */
@Composable
fun pluralText(id: Int, count: Int, vararg args: Any): String =
    LocalContext.current.resources.getQuantityString(id, count, *args)

@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, color: Color = Halaa.colors.brand) {
    val background by animateColorAsState(if (checked) color else Color.Transparent, label = "check")
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(background)
            .border(2.dp, if (checked) color else Halaa.colors.outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Halaa.colors.brand, strokeWidth = 3.dp)
    }
}

/** Explains why a permission is needed, with a button to grant it. */
@Composable
fun PermissionAsk(
    icon: ImageVector,
    title: String,
    text: String,
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, Halaa.colors.brand, size = 64.dp)
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Halaa.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Halaa.colors.inkSoft, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        PrimaryButton(stringResource(R.string.grant_access), onClick = onGrant)
        TextButton(onClick = { Permissions.open(context, Permissions.appDetailsIntent(context)) }) {
            Text(stringResource(R.string.open_app_settings), color = Halaa.colors.inkSoft)
        }
    }
}
