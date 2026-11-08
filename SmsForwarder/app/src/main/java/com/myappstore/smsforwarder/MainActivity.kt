package com.myappstore.smsforwarder

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.myappstore.smsforwarder.ui.HalaaApp
import com.myappstore.smsforwarder.ui.components.LocalResumeTick
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val resumeTick = mutableIntStateOf(0)

    /** Something requested from outside: a notification opening the log, or a launcher shortcut. */
    val requestedAction = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            CompositionLocalProvider(LocalResumeTick provides resumeTick.intValue) {
                HalaaApp(this)
            }
        }
        // Catch up on anything that became due while the process was not running.
        Graph.scope.launch { Graph.engine.dispatchDue() }
    }

    override fun onResume() {
        super.onResume()
        Graph.contacts.invalidate()
        resumeTick.intValue += 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        intent.getStringExtra(EXTRA_TAB)?.let { requestedAction.value = it }
        when (intent.getStringExtra(EXTRA_SHORTCUT)) {
            SHORTCUT_LOG -> requestedAction.value = TAB_LOG
            SHORTCUT_NEW_ROUTE -> requestedAction.value = ACTION_NEW_ROUTE
            SHORTCUT_PAUSE -> {
                Graph.settings.update { it.copy(pausedUntil = System.currentTimeMillis() + HOUR_MS) }
                Toast.makeText(this, R.string.paused_for_hour, Toast.LENGTH_SHORT).show()
            }
        }
        intent.removeExtra(EXTRA_TAB)
        intent.removeExtra(EXTRA_SHORTCUT)
    }

    /** Matches the status/navigation bar icons to the app theme (which can differ from the system's). */
    fun applySystemBars(dark: Boolean) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
            navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
        )
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_LOG = "log"
        const val ACTION_NEW_ROUTE = "new_route"
        private const val EXTRA_SHORTCUT = "shortcut"
        private const val SHORTCUT_LOG = "log"
        private const val SHORTCUT_NEW_ROUTE = "new_route"
        private const val SHORTCUT_PAUSE = "pause_hour"
        private const val HOUR_MS = 60 * 60_000L
        private val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
