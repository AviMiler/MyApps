package com.myappstore.hebrewcalendar.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.hebrew.Cities
import com.myappstore.hebrewcalendar.hebrew.MonthPage
import com.myappstore.hebrewcalendar.model.MonthModel
import com.myappstore.hebrewcalendar.settings.AppPreferences
import com.myappstore.hebrewcalendar.settings.DisplaySettings
import com.myappstore.hebrewcalendar.settings.ThemeMode
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.ThemeController
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.ui.components.SegmentedButtons
import com.myappstore.hebrewcalendar.widget.MonthWidgetProvider
import java.time.LocalDate

/**
 * "התאמה אישית": colors, light/dark, today's marker, what the grid shows and
 * text size and the zmanim city, laid out like Android's own Settings (grouped rounded lists).
 * Every change is saved at once and the screen is rebuilt in the new colors.
 */
class SettingsActivity : Activity() {

    private lateinit var preferences: AppPreferences
    private lateinit var settings: DisplaySettings
    private lateinit var scheme: ColorScheme
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = AppPreferences(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(0, 0, 0, Ui.dp(context, 32f))
        }
        setContentView(ScrollView(this).apply { addView(content) })
        build()
    }

    private fun build() {
        settings = preferences.display
        scheme = ThemeController.scheme(this, settings)
        SystemBars.apply(this, scheme, navigationColor = scheme.surface)
        content.removeAllViews()
        val items = SettingsItems(this, scheme)

        content.addView(topBar())
        content.addView(items.title(getString(R.string.customize)))
        content.addView(preview())

        content.addView(items.section(getString(R.string.section_colors)))
        content.addView(items.group(items.paletteRow(settings.palette) { update(settings.copy(palette = it)) }))
        content.addView(items.section(getString(R.string.section_theme)))
        content.addView(items.group(segmented(
            listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark), settings.themeMode.ordinal
        ) { update(settings.copy(themeMode = ThemeMode.values()[it])) }))

        content.addView(items.section(getString(R.string.section_calendar)))
        content.addView(items.group(
            items.shapePicker(settings.todayShape) { update(settings.copy(todayShape = it)) },
            items.switchRow(getString(R.string.show_secondary_date), null, settings.showSecondaryDate) {
                update(settings.copy(showSecondaryDate = it))
            },
            items.switchRow(getString(R.string.show_event_titles), getString(R.string.show_event_titles_hint), settings.showEventTitles) {
                update(settings.copy(showEventTitles = it))
            },
            items.switchRow(getString(R.string.show_holidays), null, settings.showHolidays) {
                update(settings.copy(showHolidays = it))
            },
            items.switchRow(getString(R.string.show_grid_lines), null, settings.showGridLines) {
                update(settings.copy(showGridLines = it))
            }
        ))

        content.addView(items.section(getString(R.string.section_text)))
        content.addView(items.group(items.textSizeSlider(settings.textScaleIndex) { update(settings.copy(textScaleIndex = it)) }))

        content.addView(items.section(getString(R.string.section_zmanim)))
        content.addView(items.group(
            items.valueRow(getString(R.string.zmanim_city), preferences.zmanimCity.name, getString(R.string.zmanim_city_hint), ::pickCity)
        ))
    }

    private fun update(newSettings: DisplaySettings) {
        preferences.display = newSettings
        build()
        MonthWidgetProvider.refreshAll(this)
    }

    private fun pickCity() {
        val current = Cities.ALL.indexOf(preferences.zmanimCity)
        AlertDialog.Builder(this, SystemBars.dialogTheme(scheme))
            .setTitle(R.string.zmanim_city)
            .setSingleChoiceItems(Cities.ALL.map { it.name }.toTypedArray(), current) { dialog, which ->
                preferences.zmanimCity = Cities.ALL[which]
                dialog.dismiss()
                build()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun topBar(): View = LinearLayout(this).apply {
        setPadding(Ui.dp(context, 4f), Ui.dp(context, 8f), Ui.dp(context, 4f), 0)
        addView(ImageView(context).apply {
            setImageDrawable(Ui.icon(context, R.drawable.ic_arrow_back, scheme.onSurface))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = getString(R.string.back)
            setBackgroundResource(android.R.drawable.list_selector_background)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(Ui.dp(context, 48f), Ui.dp(context, 48f)))
    }

    /** Two weeks of the current month drawn with the chosen settings. */
    private fun preview(): View {
        val page = MonthPage.containing(LocalDate.now(), preferences.viewMode)
        val weeks = MonthModel.build(page, LocalDate.now(), emptyMap())
        val todayWeek = weeks.indexOfFirst { week -> week.any { it?.isToday == true } }.coerceAtLeast(0)
        val renderer = MonthGridRenderer(this, scheme, settings)
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = Ui.rounded(context, scheme.surface, 20f)
            clipToOutline = true
            setPadding(0, Ui.dp(context, 8f), 0, 0)
        }
        val header = LinearLayout(this).also { renderer.bindWeekdays(it) }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        renderer.bindGrid(grid, weeks.drop(todayWeek).take(2), null, null)
        card.addView(header)
        card.addView(grid)
        return LinearLayout(this).apply {
            setPadding(Ui.dp(context, 16f), 0, Ui.dp(context, 16f), 0)
            addView(card, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun segmented(labels: List<Int>, selected: Int, onSelect: (Int) -> Unit): View {
        val group = LinearLayout(this)
        SegmentedButtons.bind(group, labels.map(::getString), selected, scheme, onSelect)
        return LinearLayout(this).apply {
            setPadding(Ui.dp(context, 12f), Ui.dp(context, 12f), Ui.dp(context, 12f), Ui.dp(context, 12f))
            addView(group, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

}
