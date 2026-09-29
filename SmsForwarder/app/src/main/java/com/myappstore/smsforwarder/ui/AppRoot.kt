package com.myappstore.smsforwarder.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myappstore.smsforwarder.Graph
import com.myappstore.smsforwarder.MainActivity
import com.myappstore.smsforwarder.R
import com.myappstore.smsforwarder.data.ThemeMode
import com.myappstore.smsforwarder.engine.Permissions
import com.myappstore.smsforwarder.ui.editor.EditorScreen
import com.myappstore.smsforwarder.ui.health.HealthScreen
import com.myappstore.smsforwarder.ui.home.HomeScreen
import com.myappstore.smsforwarder.ui.log.LogScreen
import com.myappstore.smsforwarder.ui.onboarding.OnboardingScreen
import com.myappstore.smsforwarder.ui.settings.SettingsScreen
import com.myappstore.smsforwarder.ui.theme.Halaa
import com.myappstore.smsforwarder.ui.theme.HalaaTheme

sealed interface Screen {
    data object Main : Screen
    data class Editor(val routeId: Long?) : Screen
    data object Health : Screen
    data object Onboarding : Screen
}

private val Screen.key: String
    get() = when (this) {
        Screen.Main -> "main"
        is Screen.Editor -> "editor-${routeId ?: "new"}"
        Screen.Health -> "health"
        Screen.Onboarding -> "onboarding"
    }

private val Screen.depth: Int get() = if (this == Screen.Main) 0 else 1

enum class Tab(val icon: ImageVector, val label: Int) {
    ROUTES(Icons.Rounded.Route, R.string.tab_routes),
    LOG(Icons.Rounded.Timeline, R.string.tab_log),
    SETTINGS(Icons.Rounded.Tune, R.string.tab_settings),
}

@Composable
fun HalaaApp(activity: MainActivity) {
    val settings by Graph.settings.flow.collectAsState()
    val dark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> isSystemInDarkTheme()
    }
    LaunchedEffect(dark) { activity.applySystemBars(dark) }

    HalaaTheme(dark = dark) {
        val stack = remember {
            mutableStateListOf<Screen>(if (settings.onboardingDone) Screen.Main else Screen.Onboarding)
        }
        var tab by rememberSaveable { mutableStateOf(Tab.ROUTES) }
        val holder = rememberSaveableStateHolder()

        fun push(screen: Screen) {
            if (stack.lastOrNull() != screen) stack.add(screen)
        }
        fun pop() {
            if (stack.size > 1) stack.removeAt(stack.lastIndex)
        }

        val requested = activity.requestedAction.value
        LaunchedEffect(requested) {
            if (requested != null && settings.onboardingDone) {
                stack.clear()
                stack.add(Screen.Main)
                when (requested) {
                    MainActivity.TAB_LOG -> tab = Tab.LOG
                    MainActivity.ACTION_NEW_ROUTE -> {
                        tab = Tab.ROUTES
                        stack.add(Screen.Editor(null))
                    }
                }
            }
            if (requested != null) activity.requestedAction.value = null
        }

        val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

        BackHandler(enabled = stack.size > 1) { pop() }

        val current = stack.last()
        AnimatedContent(
            targetState = current,
            transitionSpec = {
                val forward = targetState.depth >= initialState.depth
                // Right-to-left: new screens come in from the left, going back slides to the right.
                val enter = slideInHorizontally(tween(320)) { width -> if (forward) -width / 4 else width / 4 } + fadeIn(tween(260))
                val exit = slideOutHorizontally(tween(320)) { width -> if (forward) width / 6 else -width / 6 } + fadeOut(tween(200))
                enter togetherWith exit
            },
            label = "screens",
        ) { screen ->
            holder.SaveableStateProvider(screen.key) {
                when (screen) {
                    Screen.Main -> MainScaffold(
                        tab = tab,
                        onTab = { tab = it },
                        onOpenRoute = { id -> push(Screen.Editor(id)) },
                        onOpenHealth = { push(Screen.Health) },
                        onFixPermissions = { permissions.launch(Permissions.essentials()) },
                        onReplayOnboarding = { push(Screen.Onboarding) },
                    )
                    is Screen.Editor -> EditorScreen(routeId = screen.routeId, onClose = { pop() })
                    Screen.Health -> HealthScreen(onBack = { pop() })
                    Screen.Onboarding -> OnboardingScreen(
                        onFinish = {
                            Graph.settings.update { it.copy(onboardingDone = true) }
                            stack.clear()
                            stack.add(Screen.Main)
                        },
                    )
                }
            }
        }
    }
}

@Composable
internal fun MainScaffold(
    tab: Tab,
    onTab: (Tab) -> Unit,
    onOpenRoute: (Long?) -> Unit,
    onOpenHealth: () -> Unit,
    onFixPermissions: () -> Unit,
    onReplayOnboarding: () -> Unit,
) {
    val colors = Halaa.colors
    val insets = WindowInsets.systemBars.asPaddingValues()
    val top = insets.calculateTopPadding()
    val padding = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = top + 12.dp,
        bottom = insets.calculateBottomPadding() + 112.dp,
    )
    val todayStart = remember { Fmt.startOfToday() }
    val problems by remember { Graph.db.events().observeProblemsSince(todayStart) }.collectAsState(initial = 0)
    val tabs = rememberSaveableStateHolder()

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Crossfade(targetState = tab, animationSpec = tween(220), label = "tabs") { current ->
            tabs.SaveableStateProvider(current.name) {
                when (current) {
                    Tab.ROUTES -> HomeScreen(
                        contentPadding = padding,
                        onOpenRoute = onOpenRoute,
                        onOpenHealth = onOpenHealth,
                        onFixPermissions = onFixPermissions,
                    )
                    Tab.LOG -> LogScreen(contentPadding = padding)
                    Tab.SETTINGS -> SettingsScreen(
                        contentPadding = padding,
                        onOpenHealth = onOpenHealth,
                        onReplayOnboarding = onReplayOnboarding,
                    )
                }
            }
        }
        // Keeps content from scrolling under a see-through status bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(top)
                .background(colors.background.copy(alpha = 0.94f)),
        )
        FloatingNav(
            current = tab,
            onSelect = onTab,
            badge = problems > 0,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** A floating ink-colored pill instead of a stock bottom bar. */
@Composable
private fun FloatingNav(current: Tab, onSelect: (Tab) -> Unit, badge: Boolean, modifier: Modifier = Modifier) {
    val colors = Halaa.colors
    Row(
        modifier
            .navigationBarsPadding()
            .padding(bottom = 14.dp)
            .shadow(18.dp, CircleShape)
            .clip(CircleShape)
            .background(colors.navBar)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tab.entries.forEach { tab ->
            val selected = tab == current
            val background by animateColorAsState(if (selected) colors.brand else Color.Transparent, label = "navItem")
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(background)
                    .clickable { onSelect(tab) }
                    .padding(horizontal = if (selected) 18.dp else 16.dp, vertical = 12.dp)
                    .animateContentSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Icon(
                        tab.icon,
                        contentDescription = stringResource(tab.label),
                        tint = if (selected) Color.White else colors.navInk,
                    )
                    if (tab == Tab.LOG && badge && !selected) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-2).dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.danger),
                        )
                    }
                }
                AnimatedVisibility(visible = selected) {
                    Text(
                        stringResource(tab.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
