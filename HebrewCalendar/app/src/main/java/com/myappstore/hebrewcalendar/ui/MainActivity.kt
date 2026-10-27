package com.myappstore.hebrewcalendar.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
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
import android.widget.TextView
import android.widget.Toast
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.calendar.CalendarRepository
import com.myappstore.hebrewcalendar.hebrew.MonthPage
import com.myappstore.hebrewcalendar.hebrew.ViewMode
import com.myappstore.hebrewcalendar.model.CalendarEvent
import com.myappstore.hebrewcalendar.model.MonthModel
import com.myappstore.hebrewcalendar.settings.AppPreferences
import com.myappstore.hebrewcalendar.settings.DisplaySettings
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.ThemeController
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.ui.components.MonthSwipeLayout
import com.myappstore.hebrewcalendar.widget.MonthWidgetProvider
import java.time.LocalDate
import java.util.concurrent.Executors

/**
 * The month screen: top app bar, Hebrew/Gregorian month grid and a bottom sheet
 * with the selected day. Calendar I/O runs on [executor]; colors come from the
 * user's [ColorScheme] and are re-applied in onStart (after Settings, too).
 */
class MainActivity : Activity() {

    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var preferences: AppPreferences
    private lateinit var repository: CalendarRepository
    private lateinit var topBar: TopBarBinder
    private lateinit var sheet: DaySheetBinder
    private lateinit var page: MonthPage
    private lateinit var selectedDate: LocalDate
    private lateinit var settings: DisplaySettings
    private lateinit var scheme: ColorScheme
    private var eventsByDay: Map<LocalDate, List<CalendarEvent>> = emptyMap()
    /** Bumped on every load so a slow, outdated load never overwrites a newer one. */
    private var loadGeneration = 0
    private var isObservingCalendar = false
    /** Set by the widget's "+"; handled in onStart once colors are known. */
    private var pendingAddEvent = false

    /** Fires when the Google sync adapter (or anyone) changes calendar data. */
    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preferences = AppPreferences(this)
        repository = CalendarRepository(this)
        topBar = TopBarBinder(this)
        sheet = DaySheetBinder(this, ::openEvent)
        selectedDate = dateFrom(intent) ?: LocalDate.now()
        page = MonthPage.containing(selectedDate, preferences.viewMode)

        findViewById<View>(R.id.button_previous).setOnClickListener { showPage(page.previous(), slideFromNext = false) }
        findViewById<View>(R.id.button_next).setOnClickListener { showPage(page.next(), slideFromNext = true) }
        findViewById<MonthSwipeLayout>(R.id.month_grid).onSwipe = { toNext ->
            showPage(if (toNext) page.next() else page.previous(), slideFromNext = toNext)
        }
        findViewById<View>(R.id.title_area).setOnClickListener { pickDate() }
        findViewById<View>(R.id.button_today).setOnClickListener { select(LocalDate.now()) }
        findViewById<View>(R.id.button_sync).setOnClickListener { syncNow() }
        findViewById<View>(R.id.button_settings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<View>(R.id.fab).setOnClickListener { addEvent(selectedDate) }
        findViewById<View>(R.id.permission_card).setOnClickListener { onPermissionCardClick() }

        if (savedInstanceState == null && !repository.canRead()) requestCalendarPermissions()
        pendingAddEvent = intent.getBooleanExtra(EXTRA_ADD_EVENT, false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        dateFrom(intent)?.let(::select)
        // onNewIntent runs before onStart when the activity comes back to the front.
        pendingAddEvent = intent.getBooleanExtra(EXTRA_ADD_EVENT, false)
    }

    override fun onStart() {
        super.onStart()
        settings = preferences.display
        scheme = ThemeController.scheme(this, settings)
        SystemBars.apply(this, scheme)
        startObservingCalendar()
        render()
        reload()
        if (pendingAddEvent) {
            pendingAddEvent = false
            addEvent(selectedDate)
        }
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startObservingCalendar()
        render()
        reload()
        MonthWidgetProvider.refreshAll(this)
    }

    private fun select(date: LocalDate) {
        selectedDate = date
        if (date in page) render() else showPage(MonthPage.containing(date, page.mode), slideFromNext = date > page.lastDay)
    }

    private fun showPage(newPage: MonthPage, slideFromNext: Boolean) {
        page = newPage
        val today = LocalDate.now()
        if (selectedDate !in page) selectedDate = if (today in page) today else page.firstDay
        eventsByDay = emptyMap()
        render()
        reload()
        // RTL: the next month enters from the left.
        val grid = findViewById<View>(R.id.month_grid)
        grid.translationX = grid.width * 0.12f * (if (slideFromNext) -1 else 1)
        grid.alpha = 0f
        grid.animate().translationX(0f).alpha(1f).setDuration(220).start()
    }

    private fun render() {
        topBar.bind(page, scheme) { mode -> changeViewMode(mode) }
        val renderer = MonthGridRenderer(this, scheme, settings)
        renderer.bindWeekdays(findViewById(R.id.weekday_header))
        renderer.bindGrid(findViewById(R.id.month_grid), MonthModel.build(page, LocalDate.now(), eventsByDay), selectedDate, ::select)
        val canRead = repository.canRead()
        sheet.bind(selectedDate, eventsByDay[selectedDate].orEmpty(), canRead, preferences.zmanimCity, scheme, settings.textScale)
        findViewById<TextView>(R.id.permission_card).apply {
            visibility = if (canRead) View.GONE else View.VISIBLE
            setText(if (isPermissionBlocked()) R.string.permission_settings else R.string.permission_banner)
            setTextColor(scheme.onSecondaryContainer)
            background = Ui.rounded(context, scheme.secondaryContainer, 20f)
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

    private fun changeViewMode(mode: ViewMode) {
        preferences.viewMode = mode
        showPage(MonthPage.containing(selectedDate, mode), slideFromNext = true)
        MonthWidgetProvider.refreshAll(this)
    }

    private fun pickDate() {
        val date = selectedDate
        DatePickerDialog(this, SystemBars.dialogTheme(scheme), { _, year, month, day ->
            select(LocalDate.of(year, month + 1, day))
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
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

    private fun addEvent(date: LocalDate) {
        if (!repository.canWrite()) return requestCalendarPermissions()
        executor.execute {
            val calendars = repository.writableCalendars()
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                AddEventDialog(this, repository, executor, scheme) { reload(); MonthWidgetProvider.refreshAll(this) }
                    .show(date, calendars)
            }
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

    private fun onPermissionCardClick() {
        if (isPermissionBlocked()) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            requestCalendarPermissions()
        }
    }

    /** After "don't ask again" the system dialog no longer appears; only Settings can grant. */
    private fun isPermissionBlocked(): Boolean =
        preferences.hasAskedCalendarPermission && !shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR)

    private fun requestCalendarPermissions() {
        preferences.hasAskedCalendarPermission = true
        requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR), 1)
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
        /** Opens the add-event dialog (the widget's "+" button). */
        const val EXTRA_ADD_EVENT = "com.myappstore.hebrewcalendar.EXTRA_ADD_EVENT"
        private const val TAG = "MainActivity"
    }
}
