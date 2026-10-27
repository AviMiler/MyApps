# Project Map
_Last updated: 2026-10-27_

Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) are omitted.
Paths under `app/src/main/java/com/myappstore/hebrewcalendar/` are shortened to `…/`.

## File Descriptions
| File | Description |
|---|---|
| `build.gradle`, `settings.gradle`, `gradle.properties` | Gradle project setup (same as the other apps). |
| `app/build.gradle` | App module: SDK levels; JUnit + ICU4J for tests only. |
| `app/src/main/AndroidManifest.xml` | Calendar permissions, activities, widget receiver, job service. |
| `…/calendar/CalendarRepository.kt` | Calendar provider I/O: read, list calendars, insert, request sync. |
| `…/hebrew/DateLabels.kt` | Hebrew names of months/weekdays and full date strings. |
| `…/hebrew/HebrewAnniversary.kt` | Dates of a yearly-by-Hebrew-date event for N years. |
| `…/hebrew/HebrewDate.kt` | Hebrew date type and Gregorian <-> Hebrew arithmetic. |
| `…/hebrew/HebrewNumerals.kt` | Gematria formatting (ט״ו, תשפ״ז). |
| `…/hebrew/JewishHolidays.kt` | Holidays, fasts and Rosh Chodesh per Hebrew year (Israel). |
| `…/hebrew/Zmanim.kt` | Israeli cities and the day's zmanim (NOAA solar formulas, sea level). |
| `…/hebrew/MonthPage.kt` | A Hebrew or Gregorian month page: range, titles, weeks, navigation. |
| `…/model/CalendarEvent.kt` | Event occurrence and writable-calendar data classes. |
| `…/model/EventDays.kt` | Spreads events over the days they cover. |
| `…/model/MonthModel.kt` | `DayCell` and the builder of a page's cell rows. |
| `…/settings/AppPreferences.kt` | Persists view mode, display options, zmanim city, permission flag, widget offsets. |
| `…/settings/DisplaySettings.kt` | Palette/ThemeMode/TodayShape enums and the display options. |
| `…/theme/ColorScheme.kt` | Material 3 color roles + hex/blend helpers. |
| `…/theme/PresetSchemes.kt` | Tonal schemes for the fixed palettes (light/dark). |
| `…/theme/SystemPalette.kt` | Reads Android 12+ system_accent/neutral palette colors. |
| `…/theme/ThemeController.kt` | Resolves the scheme from settings, night mode and wallpaper. |
| `…/theme/Ui.kt` | dp, rounded backgrounds, tinted icons, today-shape drawables, text size. |
| `…/ui/AddEventDialog.kt` | Add-event dialog (optionally yearly by Hebrew date for N years). |
| `…/ui/DaySheetBinder.kt` | Bottom sheet: selected day dates, holidays, event list, zmanim. |
| `…/ui/HolidayNames.kt` | Short holiday names for day cells. |
| `…/ui/MainActivity.kt` | Month screen: navigation (buttons, swipe, date picker), loading, actions, permissions. |
| `…/ui/MonthGridRenderer.kt` | Draws weekday header and month grid cells (chips, holiday tags, today shape). |
| `…/ui/SettingsActivity.kt` | Customization screen (incl. zmanim city picker); saves and rebuilds on every change. |
| `…/ui/SettingsItems.kt` | Settings building blocks: sections, grouped lists, palette row, shape picker, switches, value row, slider. |
| `…/ui/SystemBars.kt` | Status/navigation bar colors and the DeviceDefault dialog theme. |
| `…/ui/TopBarBinder.kt` | Top app bar, today icon, Hebrew/Gregorian button group, FAB colors. |
| `…/ui/components/M3Switch.kt` | Material 3 switch drawn on a Canvas. |
| `…/ui/components/MonthSwipeLayout.kt` | Grid container that turns horizontal swipes into month changes. |
| `…/ui/components/PaletteSwatch.kt` | Color option tile like Android's "Wallpaper & style". |
| `…/ui/components/SegmentedButtons.kt` | Material 3 Expressive connected button group. |
| `…/widget/CalendarChangeJobService.kt` | Job that redraws widgets on calendar changes. |
| `…/widget/MonthWidgetProvider.kt` | AppWidgetProvider: navigation and mode-toggle actions, resize redraw, refresh entry point. |
| `…/widget/WidgetColors.kt` | Applies color roles to RemoteViews (light+dark on Android 12+). |
| `…/widget/WidgetRefreshScheduler.kt` | Midnight alarm + calendar content-trigger job. |
| `…/widget/WidgetRenderer.kt` | Builds the widget RemoteViews (nested rows/cells, event-title chips sized to the widget). |
| `app/src/main/res/layout/activity_main.xml` | Main screen structure (colors set at runtime). |
| `app/src/main/res/layout/dialog_add_event.xml` | Add-event dialog body (incl. repeat-years row). |
| `app/src/main/res/layout/widget_month.xml` | Widget root: tinted background, header (incl. mode button), weekdays, grid container. |
| `app/src/main/res/layout/widget_week_row.xml` | Widget week row (cells added at runtime). |
| `app/src/main/res/layout/widget_day_cell.xml` | Widget day cell: today shape, number, secondary date, chip container, dots. |
| `app/src/main/res/layout/widget_event_chip.xml` | One event title / holiday / "+N" line in a widget cell. |
| `app/src/main/res/xml/month_widget_info.xml` | Widget metadata (size, update period). |
| `app/src/main/res/drawable/ic_*.xml` | Material icons (white, tinted at runtime). |
| `app/src/main/res/drawable/shape_*.xml` | Today markers: cookie, circle, square, underline; `shape_pill` for the widget mode button. |
| `app/src/main/res/drawable/widget_shape.xml` | Widget background (tinted via color filter). |
| `app/src/main/res/values*/` | Hebrew strings, first-frame colors, DeviceDefault theme. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive launcher icon. |
| `app/src/test/java/…/hebrew/*Test.kt` | JVM tests: dates vs ICU4J, gematria, holidays, pages, anniversaries, zmanim. |
| `app/src/test/java/…/model/*Test.kt` | JVM tests: event day spreading, cell model. |
