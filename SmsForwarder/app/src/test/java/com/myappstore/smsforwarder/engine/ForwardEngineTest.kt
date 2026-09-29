package com.myappstore.smsforwarder.engine

import android.Manifest
import android.app.Application
import androidx.room.Room
import com.myappstore.smsforwarder.contacts.ContactsRepository
import com.myappstore.smsforwarder.core.Origin
import com.myappstore.smsforwarder.data.AppDatabase
import com.myappstore.smsforwarder.data.EventStatus
import com.myappstore.smsforwarder.data.ForwardEvent
import com.myappstore.smsforwarder.data.Party
import com.myappstore.smsforwarder.data.Reason
import com.myappstore.smsforwarder.data.Route
import com.myappstore.smsforwarder.data.SettingsStore
import com.myappstore.smsforwarder.sms.IncomingSms
import com.myappstore.smsforwarder.sms.NotifiedMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Runs the real engine against an in-memory database. A plain [Application] keeps the app's
 * own singletons (used by the screenshot test) out of the way.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ForwardEngineTest {

    private lateinit var db: AppDatabase
    private lateinit var engine: ForwardEngine

    private val bank = Party("Leumi")
    private val yossi = Party("0541112233", "יוסי")
    private var codesRouteId = 0L
    private var billsRouteId = 0L

    @Before
    fun setUp() = runBlocking {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS)
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        engine = ForwardEngine(
            app, db, SettingsStore(app), ContactsRepository(app), Notifier(app), Scheduler(app),
            notificationGraceMs = 0L,
        )
        // Two routes from the same bank to the same person: one for codes, one for bills.
        codesRouteId = db.routes().insert(
            Route(sources = listOf(bank), destinations = listOf(yossi), codesOnly = true, sortOrder = 1),
        )
        billsRouteId = db.routes().insert(
            Route(sources = listOf(bank), destinations = listOf(yossi), includeWords = listOf("חיוב"), sortOrder = 2),
        )
    }

    @After
    fun tearDown() = db.close()

    private fun receive(body: String, at: Long = System.currentTimeMillis()): List<ForwardEvent> = runBlocking {
        engine.onIncoming(IncomingSms(bank.address, body, at, subId = -1))
        db.events().observeRecent(50).first()
    }

    /** The messaging app posts a notification at [postedAt] showing [messages]. */
    private fun notify(postedAt: Long, vararg messages: NotifiedMessage): List<ForwardEvent> = runBlocking {
        engine.onNotifiedMessages("com.samsung.android.messaging", messages.toList(), postedAt)
        db.events().observeRecent(50).first()
    }

    private fun message(name: String, text: String, at: Long) = NotifiedMessage(name, null, text, at)

    @Test
    fun recipientSkippedByOneRouteIsServedByTheNext() {
        val events = receive("חיוב חודשי עבור החשבון שלך")
        assertEquals(1, events.size)
        assertEquals(billsRouteId, events.single().routeId)
        assertNotEquals(EventStatus.SKIPPED, events.single().status)
    }

    @Test
    fun theFirstRouteThatForwardsWins() {
        val events = receive("קוד האימות שלך הוא 482913")
        assertEquals(1, events.size)
        assertEquals(codesRouteId, events.single().routeId)
        assertNotEquals(EventStatus.SKIPPED, events.single().status)
    }

    @Test
    fun theNotificationOfAnSmsAlreadyHandledIsIgnored() {
        val now = System.currentTimeMillis()
        receive("חיוב חודשי עבור החשבון שלך", now)
        val events = notify(now + 2_000, message("Leumi", "חיוב חודשי עבור החשבון שלך", now + 1_000))
        assertEquals(1, events.size)
        assertEquals(Origin.SMS, events.single().origin)
    }

    @Test
    fun anSmsThatNeverArrivedIsForwardedFromItsNotification() {
        val now = System.currentTimeMillis()
        val events = notify(now, message("Leumi", "חיוב חודשי עבור החשבון שלך", now))
        assertEquals(1, events.size)
        assertEquals(billsRouteId, events.single().routeId)
        assertEquals(Origin.NOTIFICATION, events.single().origin)
        assertNotEquals(EventStatus.SKIPPED, events.single().status)
    }

    @Test
    fun earlierMessagesRepeatedInTheNotificationAreNotForwardedAgain() {
        val now = System.currentTimeMillis()
        val first = message("Leumi", "חיוב ראשון בחשבון", now)
        notify(now, first)
        // The app re-posts the conversation with the earlier message when the next one arrives.
        val events = notify(now + 60_000, first, message("Leumi", "חיוב שני בחשבון", now + 60_000))
        assertEquals(2, events.size)
        assertEquals(setOf("חיוב ראשון בחשבון", "חיוב שני בחשבון"), events.map { it.body }.toSet())
    }

    @Test
    fun aSenderShownOnlyByNameMatchesTheContactPickedForTheRoute() = runBlocking {
        val dad = Party("0501234567", "אבא")
        val dadRoute = db.routes().insert(Route(sources = listOf(dad), destinations = listOf(yossi), sortOrder = 3))
        val now = System.currentTimeMillis()
        val events = notify(now, message("אבא", "אני בדרך הביתה", now))
        assertEquals(1, events.size)
        assertEquals(dadRoute, events.single().routeId)
        assertEquals("אבא", events.single().senderName)
    }

    @Test
    fun aSkipIsLoggedOnceWhenNoRouteForwards() {
        val events = receive("שלום, מה נשמע?")
        assertEquals(1, events.size)
        assertEquals(EventStatus.SKIPPED, events.single().status)
        assertEquals(codesRouteId, events.single().routeId)
        assertEquals(Reason.NOT_A_CODE, events.single().reason)
    }
}
