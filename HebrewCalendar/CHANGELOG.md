# Changelog

## 2026-11-08 (long Hebrew day labels)
- Fixed: Hebrew day labels wider than one digit (כ״א, כ״ד, כ״ה ...) wrapped inside a fixed-width box
  and lost their last letter. The day-number box now stays on one line and grows (`ui/MonthGridRenderer.kt`,
  `res/layout/widget_day_cell.xml`).

## 2026-11-08 (close day details from anywhere)
- Added: with the day details full screen, dragging down anywhere in them (when scrolled to the top)
  returns them to the small sheet (`ui/components/SheetScrollView.kt`), not only from the grey handle.

## 2026-11-08 (widget load fix)
- Fixed: the widget could not be added ("לא ניתן להוסיף widget"): the grid-line views in
  `widget_day_cell.xml` were plain `<View>`, which RemoteViews refuses to inflate. They are
  `ImageView`s now. Widget layouts may only use FrameLayout, LinearLayout, RelativeLayout,
  GridLayout, TextView, ImageView, Button, ImageButton, ProgressBar, Chronometer, TextClock, ViewFlipper.

## 2026-11-08 (updatable builds)
- Changed: debug builds are signed with a committed fixed key (`app/debug.keystore`) and `versionCode`
  comes from `GITHUB_RUN_NUMBER`, so each CI APK installs as an update over the previous one.
  The first install after this change needs an uninstall (the signing key changed once).

## 2026-11-08 (widget lines, bottom edge)
- Changed: widget grid lines are separate 1dp views per cell (above and beside it), so they run
  edge to edge and down to the widget's bottom (no 10dp dead band, no stray line from rounding);
  the grid background is no longer used for lines. No line along the widget's left edge.

## 2026-11-08 (widget lines, day sheet size)
- Changed: grid lines in the widget are brighter and 1dp (`Ui.gridLine`).
- Changed: closed day details are ~30% of the screen height (180-280dp); the month grid rows
  now fill the rest (`MonthGridRenderer.bindGrid(fill = true)`).
- Changed: expanding/collapsing the day details animates the sheet's height over the grid (180ms)
  instead of hiding the grid first.

## 2026-11-08 (colors, full-screen day, Hebrew date picker)
- Changed: Google's vivid turquoise event color is softened to a calm blue (`theme/EventColors.kt`,
  applied in `CalendarRepository`), and chip text switches to dark/white by contrast.
- Added: tap (or drag up) the grey handle above the day details to show them full screen;
  back/tap/drag down returns (`MainActivity.setDayExpanded`, top area is `top_area`).
- Changed: dark mode surfaces are lifted a step from near-black (`ThemeController.lifted`).
- Added: free color per item (accent, background, day sheet, cards, text, holidays, events) in the
  customization screen via `ColorPickerDialog`; long-press an event in the day sheet to color just
  that event (stored per event id in `AppPreferences`).
- Added: the date picker (title tap) has a Hebrew page (day/month/year wheels, leap years aware)
  and a Gregorian page (`ui/components/DatePickerDialogs.kt`).
- Changed: the Hebrew/Gregorian switch is a small button beside "today" in the top bar; the
  button group row under the title is gone.
- Not verified on a device: no Android SDK in the authoring session.

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
