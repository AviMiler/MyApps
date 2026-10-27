# Dependencies

| Package | Version | Why |
|---|---|---|
| Android Gradle Plugin | `8.5.0` | Same as the other apps in the repo. |
| Kotlin | `1.9.24` | Same as the other apps in the repo. |
| `junit:junit` (test) | `4.13.2` | Unit tests. |
| `com.ibm.icu:icu4j` (test) | `75.1` | Reference implementation for checking Hebrew date conversion. |

No runtime library dependency at all (removed AndroidX on 2026-10-27). No library is used for Hebrew dates or Google access: dates are computed in
`hebrew/HebrewDate.kt` and Google Calendar is reached through Android's `CalendarContract`.
