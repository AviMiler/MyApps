# Changelog

## 2026-10-27 (zmanim, widget titles)
- Added: the day's zmanim in the day sheet (`hebrew/Zmanim.kt`): dawn, misheyakir, sunrise,
  latest Shema/Tefilla (MGA and GRA), chatzot, mincha gedola/ketana, plag, sunset, nightfall,
  plus candle lighting on erev Shabbat/Yom Tov (by city custom) and the end of Shabbat/Yom Tov
  (8.5° and Rabbeinu Tam). City picker under "זמני היום" in the customization screen.
- Changed: the widget shows event titles as colored chips (plus the holiday tag and "+N"),
  as many lines as fit its current height; dots remain when titles are off or there is no room.
  Resizing the widget redraws it.
- Added: a small עברי/לועזי button in the widget header that switches the view mode for the
  app and all widgets (each widget returns to the current month).

## 2026-10-27
- Changed: redesigned in Android's Material 3 Expressive style: top app bar, connected
  Hebrew/Gregorian button group, Google-Calendar-like month grid (event titles as colored
  chips, holiday tags, swipe between months), bottom sheet for the day, extended FAB,
  "cookie"-shaped today marker, Material 3 tonal widget.
- Added: customization screen (`ui/SettingsActivity.kt`): wallpaper colors (Android 12+) or
  5 palettes, system/light/dark, today marker shape, secondary date / event titles /
  holidays / grid lines toggles, text size. App and widget follow it.
- Added: yearly-by-Hebrew-date events repeat for a chosen number of years (1-120), not a fixed 10.
- Removed: AndroidX dependencies (core, appcompat); framework `Activity` + DeviceDefault theme.

## 2026-10-26
- Added: CI publishes the APK to the `hebrew-calendar-latest` Release for a direct
  phone download link (Actions artifacts need a desktop login).
- Changed: grid lines between day cells in the app and the widget (cells are opaque
  tiles on a `grid_line`-colored container; highlight drawables are now square).
- Added: Hebrew calendar app with Hebrew/Gregorian month views, Jewish holidays,
  Google Calendar events via the Android calendar provider, adding events (with
  yearly recurrence by Hebrew date), manual sync, and a monthly home-screen widget.
- Added: JVM unit tests, including a 200-year comparison with ICU4J.
