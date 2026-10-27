# Agent Context
_Last updated: 2026-10-27 after adding zmanim and widget event titles_

## What This Project Is
An Android Hebrew-calendar app (Hebrew UI, RTL) for one user. It shows a month
grid by Hebrew (or Gregorian) months with Jewish holidays (Israel custom), shows
and adds events in the user's Google Calendar via the Android calendar provider,
and ships a monthly home-screen widget. Part of the `MyApps` multi-app repo; built
by the shared `.github/workflows/build-apk.yml`.

## Current State
- ✅ Working: Hebrew date arithmetic (checked against ICU4J for 1900-2100), gematria,
  holidays with postponements, month pages (Hebrew/Gregorian), event reading via
  `CalendarContract.Instances`, adding events (incl. yearly-by-Hebrew-date), manual
  sync request, monthly widget with navigation and event-title chips, midnight +
  calendar-change refresh, daily zmanim per chosen Israeli city in the day sheet.
- 🚧 WIP: nothing in progress.
- ❌ Known Issues: not yet run on a device/emulator (the session that wrote it had
  no Android SDK; sources were type-checked against the Android 14 framework).

## Last Changes
- 2026-10-27 — Zmanim in the day sheet (`hebrew/Zmanim.kt`, city in settings); event titles in the widget. See CHANGELOG.md.
- 2026-10-27 — Material 3 Expressive redesign, customization screen, configurable Hebrew-date repeat years, AndroidX removed. See CHANGELOG.md.
- 2026-10-26 — Grid lines between day cells (`ui/CellStyle.kt`, cell layouts/drawables, `grid_line` color).
- 2026-10-26 — Created the app (all files). See CHANGELOG.md.

## Entry Points
- `app/src/main/java/com/myappstore/hebrewcalendar/ui/MainActivity.kt` — the screen.
- `app/src/main/java/com/myappstore/hebrewcalendar/widget/MonthWidgetProvider.kt` — widget.
- `app/src/main/AndroidManifest.xml` — permissions, activity, widget receiver, job.

## Key Patterns Used
- `hebrew/` and `model/` are pure Kotlin (java.time only) and unit tested on the JVM;
  keep Android imports out of them.
- All calendar-provider I/O goes through `calendar/CalendarRepository.kt`.
- `model/MonthModel.kt` defines what a day cell holds; the app grid (`ui/MonthGridRenderer.kt`)
  and the widget (`widget/WidgetRenderer.kt`) draw it.
- No XML colors: every screen is colored at runtime from `theme/ColorScheme` resolved by
  `theme/ThemeController.kt` (wallpaper palette on Android 12+, or `theme/PresetSchemes.kt`).
- Display options are `settings/DisplaySettings.kt`, persisted by `settings/AppPreferences.kt`.
- Widget colors go through `widget/WidgetColors.kt` (light+dark pair on Android 12+).
- No AndroidX: framework `Activity`, DeviceDefault theme/dialogs, custom M3 components in `ui/components/`.

## Next Steps
- [ ] Run on a device; check the widget at several sizes and in dark mode.
- [ ] Optional: weekly Torah portion, diaspora mode, zmanim by GPS location / elevation.
- [ ] Optional: a draggable bottom sheet; month/year picker in Hebrew dates.

## Gotchas / Watch Out
- Do NOT switch to `android.icu.util.HebrewCalendar`: it gets Rosh Hashana 5807
  (2046-10-01) wrong (see `HebrewDateTest.roshHashana5807`).
- Month index 5 (`ADAR_I`) exists only in leap years; `ADAR` (6) is Adar II then.
- All-day events are UTC midnights in the provider (`model/EventDays.kt`).
- Grid lines come from the container background showing through 0.5dp cell margins:
  every cell background must be opaque.
- Layouts are RTL (`layoutDirection=rtl`); corner/arrow logic assumes it.
- Widget layouts may only use RemoteViews-safe views (LinearLayout, TextView...).
- Widget chip count comes from `OPTION_APPWIDGET_MAX_HEIGHT` and the dp constants at the
  bottom of `WidgetRenderer`; keep them in sync with `widget_month.xml`/`widget_day_cell.xml`.
- Zmanim are computed at sea level in `Asia/Jerusalem`; the city list is Israeli only.
