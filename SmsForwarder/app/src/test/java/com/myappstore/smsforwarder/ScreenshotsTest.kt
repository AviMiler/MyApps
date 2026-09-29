package com.myappstore.smsforwarder

import android.Manifest
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.myappstore.smsforwarder.core.Days
import com.myappstore.smsforwarder.core.HoldPolicy
import com.myappstore.smsforwarder.core.MessageTemplate
import com.myappstore.smsforwarder.core.SourceMode
import com.myappstore.smsforwarder.data.EventKind
import com.myappstore.smsforwarder.data.EventStatus
import com.myappstore.smsforwarder.data.ForwardEvent
import com.myappstore.smsforwarder.data.Party
import com.myappstore.smsforwarder.data.Reason
import com.myappstore.smsforwarder.data.Route
import com.myappstore.smsforwarder.ui.MainScaffold
import com.myappstore.smsforwarder.ui.Tab
import com.myappstore.smsforwarder.ui.editor.EditorScreen
import com.myappstore.smsforwarder.ui.editor.PartyPicker
import com.myappstore.smsforwarder.ui.health.HealthScreen
import com.myappstore.smsforwarder.ui.onboarding.OnboardingScreen
import com.myappstore.smsforwarder.ui.theme.Halaa
import com.myappstore.smsforwarder.ui.theme.HalaaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import java.time.LocalTime

/**
 * Renders every screen with sample data into app/build/screenshots, using Robolectric's
 * native graphics. Run with `./gradlew testDebugUnitTest` to refresh the images.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xhdpi")
class ScreenshotsTest {

    private val out = File("build/screenshots").apply { mkdirs() }

    @Test
    fun renderScreens() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS,
            Manifest.permission.POST_NOTIFICATIONS,
        )
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        val now = System.currentTimeMillis()
        seed(now)
        Graph.settings.update { it.copy(onboardingDone = true) }

        shot("01-home") { Main(Tab.ROUTES) }
        shot("02-home-dark", dark = true) { Main(Tab.ROUTES) }
        shot("03-log") { Main(Tab.LOG) }
        shot("04-log-dark", dark = true) { Main(Tab.LOG) }
        shot("05-settings", heightDp = 3300) { Main(Tab.SETTINGS) }
        shot("06-editor", heightDp = 3000) { EditorScreen(routeId = 2L, onClose = {}) }
        shot("07-editor-dark", dark = true, heightDp = 1500) { EditorScreen(routeId = 1L, onClose = {}) }
        shot("08-picker") {
            PartyPicker(title = "ממי להעביר?", initial = listOf(dad, grandpa), onDone = {}, onDismiss = {})
        }
        shot("09-onboarding") { OnboardingScreen(onFinish = {}) }
        shot("10-health", heightDp = 1700) { HealthScreen(onBack = {}) }

        runBlocking { addWaiting(now) }
        Graph.settings.update { it.copy(pausedUntil = now + 2 * 60 * 60_000L) }
        shot("11-home-paused") { Main(Tab.ROUTES) }

        val minute = LocalTime.now().let { it.hour * 60 + it.minute }
        Graph.settings.update {
            it.copy(
                pausedUntil = 0L,
                quietEnabled = true,
                quietStart = (minute + 1440 - 60) % 1440,
                quietEnd = (minute + 120) % 1440,
            )
        }
        shot("12-home-quiet-dark", dark = true) { Main(Tab.ROUTES) }
        Graph.settings.update { it.copy(quietEnabled = false, masterEnabled = false, pausedUntil = HoldPolicy.FOREVER) }
        shot("13-home-off") { Main(Tab.ROUTES) }
        Graph.settings.update { it.copy(masterEnabled = true, pausedUntil = 0L) }
    }

    @Composable
    private fun Main(tab: Tab) = MainScaffold(tab, {}, {}, {}, {}, {})

    private fun shot(name: String, dark: Boolean = false, heightDp: Int = 915, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("w412dp-h${heightDp}dp-xhdpi")
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        controller.get().setTheme(R.style.Theme_Halaa)
        controller.setup()
        val activity = controller.get()
        activity.setContent {
            HalaaTheme(dark = dark) {
                Box(Modifier.fillMaxSize().background(Halaa.colors.background)) { content() }
            }
        }
        // Let composition, Room queries (on background threads) and animations settle.
        repeat(6) {
            Thread.sleep(150)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(400))
        }
        val root = activity.findViewById<View>(android.R.id.content)
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        controller.pause().stop().destroy()
    }

    // ---------------------------------------------------------------- sample data

    private val dad = Party("0501234567", "אבא")
    private val grandpa = Party("0527654321", "סבא")
    private val yossi = Party("0541112233", "יוסי")
    private val moshe = Party("0509998877", "משה")
    private val office = Party("0733334444", "המשרד")

    private fun seed(now: Long) = runBlocking {
        val routes = Graph.db.routes()
        routes.insert(Route(colorIndex = 0, sources = listOf(dad, grandpa), destinations = listOf(yossi), replyRelay = true, sortOrder = 1))
        routes.insert(
            Route(
                name = "קודים מהבנק",
                colorIndex = 2,
                sources = listOf(Party("Leumi"), Party("Hapoalim"), Party("Max")),
                destinations = listOf(yossi, moshe),
                codesOnly = true,
                template = MessageTemplate.Preset.CODE_FIRST.template,
                sortOrder = 2,
            ),
        )
        routes.insert(
            Route(
                name = "טלפון העבודה",
                colorIndex = 1,
                sourceMode = SourceMode.UNKNOWN,
                destinations = listOf(office),
                scheduleEnabled = true,
                scheduleDays = Days.SUN_TO_THU,
                delaySeconds = 30,
                sortOrder = 3,
            ),
        )
        routes.insert(
            Route(
                name = "חשבונות",
                colorIndex = 6,
                enabled = false,
                sources = listOf(Party("Bezeq"), Party("IEC")),
                destinations = listOf(moshe),
                includeWords = listOf("חשבון", "תשלום"),
                sortOrder = 4,
            ),
        )
        val events = Graph.db.events()
        val minute = 60_000L
        fun event(
            ago: Long,
            route: Long,
            color: Int,
            sender: Party,
            recipient: Party,
            body: String,
            status: Int,
            reason: Int = Reason.NONE,
            code: String? = null,
            kind: Int = EventKind.FORWARD,
        ) = ForwardEvent(
            kind = kind,
            routeId = route,
            routeColor = color,
            sender = sender.address,
            senderName = sender.name,
            recipient = recipient.address,
            recipientName = recipient.name,
            body = body,
            outgoing = "📩 ${sender.label}\n$body",
            code = code,
            receivedAt = now - ago,
            scheduledAt = now - ago,
            status = status,
            reason = reason,
            sentAt = if (status in 2..4) now - ago + 2_000 else 0,
            deliveredAt = if (status == EventStatus.DELIVERED) now - ago + 5_000 else 0,
        )
        listOf(
            event(4 * minute, 1, 0, dad, yossi, "אני בדרך, אגיע בעוד רבע שעה", EventStatus.DELIVERED),
            event(38 * minute, 2, 2, Party("Leumi"), yossi, "קוד האימות שלך הוא 482913. אין למסור אותו לאף אחד.", EventStatus.DELIVERED, code = "482913"),
            event(52 * minute, 1, 0, yossi, dad, "#מעולה, מחכים לך", EventStatus.SENT, kind = EventKind.REPLY),
            event(120 * minute, 3, 1, Party("0523456789"), office, "שלום, רציתי לברר לגבי ההזמנה", EventStatus.FAILED, Reason.SEND_ERROR),
            event(180 * minute, 2, 2, Party("Hapoalim"), yossi, "עדכון: חשבונך חויב ב-120 ש״ח", EventStatus.SKIPPED, Reason.NOT_A_CODE),
            event(300 * minute, 1, 0, grandpa, yossi, "תזכורת: מחר בשמונה בבוקר", EventStatus.DELIVERED),
            event(26 * 60 * minute, 3, 1, Party("0587770000"), office, "הטכנאי יגיע בין 10 ל-12", EventStatus.SKIPPED, Reason.OUT_OF_SCHEDULE),
            event(27 * 60 * minute, 1, 0, dad, yossi, "מה שלומך?", EventStatus.DELIVERED),
        ).forEach { events.insert(it) }
    }

    private suspend fun addWaiting(now: Long) {
        val events = Graph.db.events()
        val later = now + 2 * 60 * 60_000L
        events.insert(
            ForwardEvent(
                routeId = 1, routeColor = 0, sender = dad.address, senderName = dad.name,
                recipient = yossi.address, recipientName = yossi.name, body = "תתקשר כשאתה יכול",
                outgoing = "📩 אבא\nתתקשר כשאתה יכול", receivedAt = now - 12 * 60_000L, scheduledAt = later,
                status = EventStatus.HELD, reason = Reason.PAUSED,
            ),
        )
        events.insert(
            ForwardEvent(
                routeId = 2, routeColor = 2, sender = "Max", recipient = moshe.address, recipientName = moshe.name,
                body = "הקוד שלך: 771204", outgoing = "🔑 771204 · Max\nהקוד שלך: 771204", code = "771204",
                receivedAt = now - 20 * 60_000L, scheduledAt = later, status = EventStatus.HELD, reason = Reason.PAUSED,
            ),
        )
    }
}
