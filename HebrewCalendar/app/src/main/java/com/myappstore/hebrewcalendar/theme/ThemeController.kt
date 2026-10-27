package com.myappstore.hebrewcalendar.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.myappstore.hebrewcalendar.settings.AppPreferences
import com.myappstore.hebrewcalendar.settings.DisplaySettings
import com.myappstore.hebrewcalendar.settings.Palette
import com.myappstore.hebrewcalendar.settings.ThemeMode

/** Resolves the [ColorScheme] for the current settings, night mode and wallpaper. */
object ThemeController {

    fun scheme(context: Context): ColorScheme = scheme(context, AppPreferences(context).display)

    fun scheme(context: Context, settings: DisplaySettings): ColorScheme =
        scheme(context, settings, isDark(context, settings.themeMode))

    /** @return the scheme for [settings] in light or dark, regardless of the theme mode. */
    fun scheme(context: Context, settings: DisplaySettings, isDark: Boolean): ColorScheme {
        if (settings.palette == Palette.WALLPAPER && supportsWallpaperColors()) {
            return wallpaperScheme(context, isDark)
        }
        return PresetSchemes.of(settings.palette, isDark)
    }

    fun supportsWallpaperColors(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isDark(context: Context, mode: ThemeMode): Boolean = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Maps the system's Material You tonal palettes (accent1-3, neutral1-2; shade
     * 0 = white ... 1000 = black) onto the Material 3 roles, as dynamic color does.
     */
    private fun wallpaperScheme(context: Context, isDark: Boolean): ColorScheme {
        val p = SystemPalette(context)
        return if (isDark) {
            ColorScheme(
                isDark = true,
                primary = p.accent1(200), onPrimary = p.accent1(800),
                primaryContainer = p.accent1(700), onPrimaryContainer = p.accent1(100),
                secondaryContainer = p.accent2(700), onSecondaryContainer = p.accent2(100),
                tertiary = p.accent3(200), tertiaryContainer = p.accent3(700), onTertiaryContainer = p.accent3(100),
                surface = ColorScheme.blend(p.neutral1(900), BLACK, 0.35f),
                surfaceContainerLowest = ColorScheme.blend(p.neutral1(900), BLACK, 0.55f),
                surfaceContainerLow = p.neutral1(900),
                surfaceContainer = ColorScheme.blend(p.neutral1(900), p.neutral1(800), 0.2f),
                surfaceContainerHigh = ColorScheme.blend(p.neutral1(900), p.neutral1(800), 0.6f),
                surfaceContainerHighest = p.neutral1(800),
                onSurface = p.neutral1(100), onSurfaceVariant = p.neutral2(200),
                outline = p.neutral2(400), outlineVariant = p.neutral2(700),
                error = ColorScheme.opaque("FFB4AB"), errorContainer = ColorScheme.opaque("93000A"),
                onErrorContainer = ColorScheme.opaque("FFDAD6")
            )
        } else {
            ColorScheme(
                isDark = false,
                primary = p.accent1(600), onPrimary = p.accent1(0),
                primaryContainer = p.accent1(100), onPrimaryContainer = p.accent1(900),
                secondaryContainer = p.accent2(100), onSecondaryContainer = p.accent2(900),
                tertiary = p.accent3(600), tertiaryContainer = p.accent3(100), onTertiaryContainer = p.accent3(900),
                surface = p.neutral1(10),
                surfaceContainerLowest = p.neutral1(0),
                surfaceContainerLow = p.neutral1(50),
                surfaceContainer = ColorScheme.blend(p.neutral1(50), p.neutral1(100), 0.3f),
                surfaceContainerHigh = ColorScheme.blend(p.neutral1(50), p.neutral1(100), 0.6f),
                surfaceContainerHighest = p.neutral1(100),
                onSurface = p.neutral1(900), onSurfaceVariant = p.neutral2(700),
                outline = p.neutral2(500), outlineVariant = p.neutral2(200),
                error = ColorScheme.opaque("BA1A1A"), errorContainer = ColorScheme.opaque("FFDAD6"),
                onErrorContainer = ColorScheme.opaque("410002")
            )
        }
    }

    private val BLACK = ColorScheme.opaque("000000")
}
