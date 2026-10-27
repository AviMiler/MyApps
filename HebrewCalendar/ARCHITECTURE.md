# Architecture
_Last updated: 2026-10-27_

## Overview
A single-activity View-based app plus an AppWidgetProvider. Pure-Kotlin calendar
logic (`hebrew/`, `model/`) feeds two renderers: the app grid and the widget's
RemoteViews. Google Calendar data comes from Android's calendar provider.

## Data Flow
1. `MainActivity` / `WidgetRenderer` pick a `MonthPage` (mode from `AppPreferences`).
2. `CalendarRepository.eventsByDay(page)` queries `CalendarContract.Instances`
   (recurring events already expanded) and `EventDays` groups them by day.
3. `MonthModel.build(page, today, counts)` adds Hebrew labels and holidays → `DayCell`s.
4. `MonthGridRenderer` (app) or `WidgetRenderer` (widget) draws cells with the user's `DisplaySettings` and `ColorScheme`.
5. Changes: the app observes `CalendarContract.CONTENT_URI` while visible; widgets
   are redrawn by `CalendarChangeJobService` (content-trigger job), a midnight alarm,
   time/zone broadcasts, and `updatePeriodMillis` (1 h).
6. Adding an event: `AddEventDialog` → `CalendarRepository.insertEvents` → provider →
   Google sync adapter uploads it.

## Key Decisions
| Decision | Why |
|---|---|
| Sync via `CalendarContract`, not the Google Calendar REST API | No OAuth client / API keys / signing-key registration; two-way sync is done by the system's Google account. |
| Own Hebrew date arithmetic instead of `android.icu.util.HebrewCalendar` | ICU applies BeTUTaKPaT after a lo-ADU postponement and puts Rosh Hashana 5807 on 2046-10-02 instead of 10-01. Pure Kotlin is also JVM-testable. |
| Holidays per Israel custom | The user is in Israel (Hebrew UI). One-day Yom Tov, Simchat Torah on 22 Tishrei. |
| Yearly-by-Hebrew-date = 10 separate events | Google Calendar RRULEs cannot follow the Hebrew calendar. |
| Widget grid via nested `RemoteViews.addView` | Simpler than a RemoteViewsService/collection, and allows per-day click intents. |
| Framework Views, no AppCompat/Material library | Zero dependencies; DeviceDefault dialogs look native; everything type-checks against the platform alone. |
| Runtime `ColorScheme` instead of XML themes | Lets the user pick palette/light/dark live, and the widget use the same colors. |
| Material You from `@android:color/system_accent*` | The wallpaper palette without the Material library; presets are Material 3 tonal schemes. |
| Custom `M3Switch`, `PaletteSwatch`, `SegmentedButtons` | Material 3 Expressive look without the library. |
| Widget light+dark via `RemoteViews.setColorInt` (API 31+) | Follows night mode without a re-render. |

## Patterns
- Pure core: `hebrew/` and `model/` never import Android.
- Repository: `calendar/CalendarRepository.kt` is the only provider access; it checks
  permissions and logs `SecurityException`s instead of crashing.
- Stale-load guard: `MainActivity.loadGeneration` drops results of superseded loads.
