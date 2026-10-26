# Project Map
_Last updated: 2026-10-26_

Gradle wrapper files (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`) are omitted.
Paths under `app/src/main/java/com/myappstore/hebrewcalendar/` are shortened to `…/`.

## File Descriptions
| File | Description |
|---|---|
| `build.gradle`, `settings.gradle`, `gradle.properties` | Gradle project setup (same as the other apps). |
| `app/build.gradle` | App module: SDK levels, AndroidX, JUnit + ICU4J for tests. |
| `app/src/main/AndroidManifest.xml` | Calendar permissions, activity, widget receiver, job service. |
| `…/hebrew/HebrewDate.kt` | Hebrew date type and Gregorian <-> Hebrew arithmetic. |
| `…/hebrew/HebrewNumerals.kt` | Gematria formatting (ט״ו, תשפ״ז). |
| `…/hebrew/DateLabels.kt` | Hebrew names of months/weekdays and full date strings. |
| `…/hebrew/JewishHolidays.kt` | Holidays, fasts and Rosh Chodesh per Hebrew year (Israel). |
| `…/hebrew/HebrewAnniversary.kt` | Dates of a yearly-by-Hebrew-date event. |
| `…/hebrew/MonthPage.kt` | A Hebrew or Gregorian month page: range, titles, weeks, navigation. |
| `…/model/CalendarEvent.kt` | Event occurrence and writable-calendar data classes. |
| `…/model/EventDays.kt` | Spreads events over the days they cover. |
| `…/model/MonthModel.kt` | `DayCell` and the builder of a page's cell rows. |
| `…/calendar/CalendarRepository.kt` | Calendar provider I/O: read, list calendars, insert, request sync. |
| `…/settings/AppPreferences.kt` | View mode and per-widget month offsets. |
| `…/ui/MainActivity.kt` | Month screen: navigation, permissions, loading, actions. |
| `…/ui/MonthGridBinder.kt` | Draws the app's weekday header and month grid. |
| `…/ui/DayDetailsBinder.kt` | Selected day's dates, holidays and events list. |
| `…/ui/AddEventDialog.kt` | Dialog that adds an event (optionally yearly by Hebrew date). |
| `…/ui/CellStyle.kt` | Cell colors/backgrounds/markers shared with the widget. |
| `…/widget/MonthWidgetProvider.kt` | AppWidgetProvider: navigation actions, refresh entry point. |
| `…/widget/WidgetRenderer.kt` | Builds the widget RemoteViews (nested rows/cells). |
| `…/widget/WidgetRefreshScheduler.kt` | Midnight alarm + calendar content-trigger job. |
| `…/widget/CalendarChangeJobService.kt` | Job that redraws widgets on calendar changes. |
| `app/src/main/res/layout/activity_main.xml` | Screen layout (header, grid, day details). |
| `app/src/main/res/layout/item_day_cell.xml` | App grid cell. |
| `app/src/main/res/layout/item_event.xml` | Event row in the day details. |
| `app/src/main/res/layout/dialog_add_event.xml` | Add-event dialog body. |
| `app/src/main/res/layout/widget_month.xml` | Widget root: header, weekday letters, grid container. |
| `app/src/main/res/layout/widget_week_row.xml` | Widget week row (cells added at runtime). |
| `app/src/main/res/layout/widget_day_cell.xml` | Widget day cell. |
| `app/src/main/res/xml/month_widget_info.xml` | Widget metadata (size, update period). |
| `app/src/main/res/values*/` | Strings (Hebrew), light/dark colors, theme. |
| `app/src/main/res/drawable/` | Cell highlights, widget background, launcher foreground. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive launcher icon. |
| `app/src/test/java/…/hebrew/*Test.kt` | JVM tests: dates vs ICU4J, gematria, holidays, pages, anniversaries. |
| `app/src/test/java/…/model/*Test.kt` | JVM tests: event day spreading, cell model. |
