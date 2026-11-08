@file:OptIn(ExperimentalFoundationApi::class)

package com.myappstore.smsforwarder.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.engine.Permissions
import com.myappstore.smsforwarder.ui.components.IconBadge
import com.myappstore.smsforwarder.ui.components.InfoCard
import com.myappstore.smsforwarder.ui.components.InlineAction
import com.myappstore.smsforwarder.ui.components.LocalResumeTick
import com.myappstore.smsforwarder.ui.components.PrimaryButton
import com.myappstore.smsforwarder.ui.components.RouteIllustration
import com.myappstore.smsforwarder.ui.theme.Halaa
import kotlinx.coroutines.launch

private const val PAGES = 4

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val colors = Halaa.colors
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { PAGES })
    val tick = LocalResumeTick.current
    var refresh by remember { mutableIntStateOf(0) }
    var attempted by remember { mutableStateOf(false) }
    val ready = remember(tick, refresh) { Permissions.coreReady(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        attempted = true
        refresh++
    }
    val last = pager.currentPage == PAGES - 1

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PageDots(pager.currentPage)
            Spacer(Modifier.weight(1f))
            if (!last) {
                TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGES - 1) } }) {
                    Text(stringResource(R.string.onb_skip), color = colors.inkSoft)
                }
            }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            when (page) {
                0 -> WelcomePage()
                1 -> HowPage()
                2 -> FeaturesPage()
                else -> PermissionsPage(ready = ready, attempted = attempted, refresh = refresh)
            }
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            PrimaryButton(
                text = stringResource(
                    when {
                        !last -> R.string.onb_next
                        ready -> R.string.onb_start
                        else -> R.string.onb_allow
                    },
                ),
                icon = if (last && ready) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                onClick = {
                    when {
                        !last -> scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                        ready -> onFinish()
                        else -> launcher.launch(Permissions.essentials())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (last && !ready) {
                TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.onb_later), color = colors.inkSoft)
                }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int) {
    val colors = Halaa.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(PAGES) { index ->
            val selected = index == current
            val width by animateDpAsState(if (selected) 26.dp else 8.dp, label = "dot")
            val color by animateColorAsState(if (selected) colors.brand else colors.outline, label = "dotColor")
            Box(
                Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun WelcomePage() {
    val colors = Halaa.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RouteIllustration(
            Modifier
                .fillMaxWidth()
                .height(220.dp),
        )
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayLarge, color = colors.brand)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.onb_welcome_title),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.onb_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = colors.inkSoft,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HowPage() {
    val colors = Halaa.colors
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onb_how_title), style = MaterialTheme.typography.headlineLarge, color = colors.ink)
        Spacer(Modifier.height(28.dp))
        HowStep(Icons.Rounded.PersonAdd, stringResource(R.string.onb_how_1_title), stringResource(R.string.onb_how_1_body), colors.route(0), isLast = false)
        HowStep(Icons.Rounded.Contacts, stringResource(R.string.onb_how_2_title), stringResource(R.string.onb_how_2_body), colors.route(1), isLast = false)
        HowStep(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.onb_how_3_title), stringResource(R.string.onb_how_3_body), colors.route(2), isLast = true)
    }
}

@Composable
private fun HowStep(icon: ImageVector, title: String, body: String, color: Color, isLast: Boolean) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val line = Halaa.colors.outline
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                if (!isLast) {
                    val x = 26.dp.toPx().let { if (rtl) size.width - it else it }
                    drawLine(line, Offset(x, 56.dp.toPx()), Offset(x, size.height), strokeWidth = 3.dp.toPx())
                }
            }
            .padding(bottom = 22.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Halaa.colors.ink)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Halaa.colors.inkSoft)
        }
    }
}

@Composable
private fun FeaturesPage() {
    val colors = Halaa.colors
    val features: List<Pair<Triple<ImageVector, Int, Int>, Color>> = listOf(
        Triple(Icons.Rounded.Key, R.string.onb_feature_codes, R.string.onb_feature_codes_body) to colors.route(0),
        Triple(Icons.Rounded.NightsStay, R.string.onb_feature_rest, R.string.onb_feature_rest_body) to colors.night,
        Triple(Icons.AutoMirrored.Rounded.Reply, R.string.onb_feature_reply, R.string.onb_feature_reply_body) to colors.route(1),
        Triple(Icons.Rounded.Bedtime, R.string.onb_feature_quiet, R.string.onb_feature_quiet_body) to colors.held,
        Triple(Icons.Rounded.HourglassTop, R.string.onb_feature_undo, R.string.onb_feature_undo_body) to colors.route(4),
        Triple(Icons.Rounded.Shield, R.string.onb_feature_safety, R.string.onb_feature_safety_body) to colors.success,
    )
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.onb_features_title),
            style = MaterialTheme.typography.headlineLarge,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        features.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                pair.forEach { (feature, color) ->
                    val (icon, title, body) = feature
                    Column(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.surface)
                            .padding(16.dp),
                    ) {
                        IconBadge(icon, color, size = 42.dp)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, color = colors.ink)
                        Spacer(Modifier.height(2.dp))
                        Text(stringResource(body), style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsPage(ready: Boolean, attempted: Boolean, refresh: Int) {
    val context = LocalContext.current
    val colors = Halaa.colors
    val tick = LocalResumeTick.current
    val contacts = remember(tick, refresh) { Permissions.canReadContacts(context) }
    val notify = remember(tick, refresh) { Permissions.canNotify(context) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onb_perm_title), style = MaterialTheme.typography.headlineLarge, color = colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.onb_perm_body), style = MaterialTheme.typography.bodyMedium, color = colors.inkSoft)
        Spacer(Modifier.height(20.dp))
        PermissionLine(Icons.Rounded.Sms, stringResource(R.string.onb_perm_sms), stringResource(R.string.onb_perm_sms_body), ready, required = true)
        PermissionLine(Icons.Rounded.Contacts, stringResource(R.string.onb_perm_contacts), stringResource(R.string.onb_perm_contacts_body), contacts, required = false)
        PermissionLine(Icons.Rounded.Notifications, stringResource(R.string.onb_perm_notify), stringResource(R.string.onb_perm_notify_body), notify, required = false)
        Spacer(Modifier.height(14.dp))
        if (attempted && !ready) {
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
            Spacer(Modifier.height(12.dp))
        }
        InfoCard(
            text = stringResource(R.string.onb_privacy),
            icon = Icons.Rounded.Lock,
            color = colors.success,
        )
    }
}

@Composable
private fun PermissionLine(icon: ImageVector, title: String, body: String, granted: Boolean, required: Boolean) {
    val colors = Halaa.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, if (granted) colors.success else if (required) colors.brand else colors.inkSoft, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title + if (required) " *" else "",
                style = MaterialTheme.typography.titleSmall,
                color = colors.ink,
            )
            Text(body, style = MaterialTheme.typography.bodySmall, color = colors.inkSoft)
        }
        if (granted) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.success)
        }
    }
}
