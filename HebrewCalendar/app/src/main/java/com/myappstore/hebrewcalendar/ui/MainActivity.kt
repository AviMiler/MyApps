package com.myappstore.hebrewcalendar.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.calendar.CalendarRepository
import com.myappstore.hebrewcalendar.hebrew.MonthPage
import com.myappstore.hebrewcalendar.hebrew.ViewMode
import com.myappstore.hebrewcalendar.model.CalendarEvent
import com.myappstore.hebrewcalendar.model.MonthModel
import com.myappstore.hebrewcalendar.settings.AppPreferences
import com.myappstore.hebrewcalendar.widget.MonthWidgetProvider
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * The month screen: Hebrew/Gregorian month grid, the selected day's details and
 * events from the device calendar (= the user's synced Google Calendar).
 * Calendar I/O runs on [executor]; results are applied on the main thread.
 */
class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var preferences: AppPreferences
    private lateinit var repository: CalendarRepository
    private lateinit var grid: MonthGridBinder
    private lateinit var details: DayDetailsBinder
    private lateinit var addEventDialog: AddEventDialog
    private lateinit var page: MonthPage
    private lateinit var selectedDate: LocalDate
    private var eventsByDay: Map<LocalDate, List<CalendarEvent>> = emptyMap()
    /** Bumped on every load so a slow, outdated load never overwrites a newer one. */
    private var loadGeneration = 0
    private var isObservingCalendar = false
    private var hasAskedPermission = false

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        startObservingCalendar()
        render()
        reload()
        MonthWidgetProvider.refreshAll(this)
    }

    /** Fires when the Google sync adapter (or anyone) changes calendar data. */
    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preferences = AppPreferences(this)
        repository = CalendarRepository(this)
        grid = MonthGridBinder(findViewById(R.id.weekday_header), findViewById(R.id.month_grid), ::select)
        details = DayDetailsBinder(this, ::openEvent)
        addEventDialog = AddEventDialog(this, repository, executor) { reload(); MonthWidgetProvider.refreshAll(this) }
        selectedDate = dateFrom(intent) ?: LocalDate.now()
        page = MonthPage.containing(selectedDate, preferences.viewMode)

        findViewById<View>(R.id.button_previous).setOnClickListener { showPage(page.previous()) }
        findViewById<View>(R.id.button_next).setOnClickListener { showPage(page.next()) }
        findViewById<View>(R.id.header_titles).setOnClickListener { select(LocalDate.now()) }
        findViewById<View>(R.id.button_today).setOnClickListener { select(LocalDate.now()) }
        findViewById<View>(R.id.button_mode).setOnClickListener { toggleViewMode() }
        findViewById<View>(R.id.button_sync).setOnClickListener { syncNow() }
        findViewById<View>(R.id.button_add_event).setOnClickListener { addEvent() }
        findViewById<View>(R.id.permission_banner).setOnClickListener { onPermissionBannerClick() }

        if (savedInstanceState == null && !repository.canRead()) requestCalendarPermissions()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dateFrom(intent)?.let(::select)
    }

    override fun onStart() {
        super.onStart()
        startObservingCalendar()
        render()
        reload()
    }

    override fun onStop() {
        super.onStop()
        if (isObservingCalendar) contentResolver.unregisterContentObserver(calendarObserver)
        isObservingCalendar = false
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun select(date: LocalDate) {
        selectedDate = date
        if (date in page) render() else showPage(MonthPage.containing(date, page.mode))
    }

    private fun showPage(newPage: MonthPage) {
        page = newPage
        val today = LocalDate.now()
        if (selectedDate !in page) selectedDate = if (today in page) today else page.firstDay
        eventsByDay = emptyMap()
        render()
        reload()
    }

    private fun render() {
        findViewById<TextView>(R.id.text_title).text = page.title
        findViewById<TextView>(R.id.text_subtitle).text = page.subtitle
        findViewById<Button>(R.id.button_mode).setText(
            if (page.mode == ViewMode.HEBREW) R.string.switch_to_gregorian else R.string.switch_to_hebrew
        )
        val counts = eventsByDay.mapValues { it.value.size }
        grid.bind(MonthModel.build(page, LocalDate.now(), counts), selectedDate)
        val canRead = repository.canRead()
        details.bind(selectedDate, eventsByDay[selectedDate].orEmpty(), canRead)
        findViewById<TextView>(R.id.permission_banner).apply {
            visibility = if (canRead) View.GONE else View.VISIBLE
            setText(if (isPermissionBlocked()) R.string.permission_settings else R.string.permission_banner)
        }
    }

    private fun reload() {
        if (executor.isShutdown) return
        val target = page
        val generation = ++loadGeneration
        executor.execute {
            val events = repository.eventsByDay(target.firstDay, target.lastDay)
            runOnUiThread {
                if (generation == loadGeneration && !isDestroyed) {
                    eventsByDay = events
                    render()
                }
            }
        }
    }

    private fun toggleViewMode() {
        val mode = if (page.mode == ViewMode.HEBREW) ViewMode.GREGORIAN else ViewMode.HEBREW
        preferences.viewMode = mode
        showPage(MonthPage.containing(selectedDate, mode))
        MonthWidgetProvider.refreshAll(this)
    }

    private fun syncNow() {
        if (!repository.canRead()) return requestCalendarPermissions()
        executor.execute {
            val accounts = repository.requestGoogleSync()
            runOnUiThread {
                val message = if (accounts > 0) getString(R.string.sync_requested, accounts) else getString(R.string.sync_no_accounts)
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addEvent() {
        if (!repository.canWrite()) return requestCalendarPermissions()
        val date = selectedDate
        executor.execute {
            val calendars = repository.writableCalendars()
            runOnUiThread { if (!isDestroyed) addEventDialog.show(date, calendars) }
        }
    }

    private fun openEvent(event: CalendarEvent) {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.beginMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No calendar app can show event ${event.id}", e)
        }
    }

    private fun onPermissionBannerClick() {
        if (isPermissionBlocked()) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            requestCalendarPermissions()
        }
    }

    /** After "don't ask again" the system dialog no longer appears; only Settings can grant. */
    private fun isPermissionBlocked(): Boolean =
        hasAskedPermission && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)

    private fun requestCalendarPermissions() {
        hasAskedPermission = true
        permissionRequest.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    private fun startObservingCalendar() {
        if (isObservingCalendar || !repository.canRead()) return
        contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, calendarObserver)
        isObservingCalendar = true
    }

    /** @return the day a widget cell asked to open, if this intent came from one. */
    private fun dateFrom(intent: Intent?): LocalDate? =
        intent?.getStringExtra(EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    companion object {
        /** ISO date (yyyy-MM-dd) to select when opened from the widget. */
        const val EXTRA_DATE = "com.myappstore.hebrewcalendar.EXTRA_DATE"
        private const val TAG = "MainActivity"
    }
}
