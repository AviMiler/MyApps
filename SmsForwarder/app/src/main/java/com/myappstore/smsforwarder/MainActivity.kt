package com.myappstore.smsforwarder

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
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

    /** A tab requested from outside (e.g. a notification opening the log). */
    val requestedTab = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestedTab.value = intent?.getStringExtra(EXTRA_TAB)
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
        requestedTab.value = intent.getStringExtra(EXTRA_TAB)
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
        private val LIGHT_SCRIM = Color.argb(0xE6, 0xFF, 0xFF, 0xFF)
        private val DARK_SCRIM = Color.argb(0x80, 0x1B, 0x1B, 0x1B)
    }
}
