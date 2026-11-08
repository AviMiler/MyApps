package com.myappstore.hebrewcalendar.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.myappstore.hebrewcalendar.settings.AppPreferences
import com.myappstore.hebrewcalendar.settings.ColorItem
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
        val base = if (settings.palette == Palette.WALLPAPER && supportsWallpaperColors()) {
            wallpaperScheme(context, isDark)
        } else {
            PresetSchemes.of(settings.palette, isDark)
        }
        return withOverrides(if (isDark) lifted(base) else base, settings.colors)
    }

    /**
     * Dark surfaces straight from Material are nearly black; this moves them a
     * step towards white (the higher the surface role, the more) for a softer dark mode.
     */
    private fun lifted(s: ColorScheme): ColorScheme = s.copy(
        surface = ColorScheme.blend(s.surface, WHITE, 0.08f),
        surfaceContainerLowest = ColorScheme.blend(s.surfaceContainerLowest, WHITE, 0.04f),
        surfaceContainerLow = ColorScheme.blend(s.surfaceContainerLow, WHITE, 0.10f),
        surfaceContainer = ColorScheme.blend(s.surfaceContainer, WHITE, 0.12f),
        surfaceContainerHigh = ColorScheme.blend(s.surfaceContainerHigh, WHITE, 0.15f),
        surfaceContainerHighest = ColorScheme.blend(s.surfaceContainerHighest, WHITE, 0.18f)
    )

    /** Applies the user's free per-item colors on top of the palette, keeping text readable. */
    private fun withOverrides(base: ColorScheme, colors: Map<ColorItem, Int>): ColorScheme {
        var s = base
        colors[ColorItem.BACKGROUND]?.let { bg ->
            val dark = EventColors.luminance(bg) < 0.5f
            val text = if (dark) ColorScheme.opaque("E6E6EA") else ColorScheme.opaque("1B1B1F")
            s = s.copy(
                isDark = dark, surface = bg, onSurface = text,
                onSurfaceVariant = ColorScheme.blend(text, bg, 0.3f),
                outline = ColorScheme.blend(text, bg, 0.5f), outlineVariant = ColorScheme.blend(text, bg, 0.8f)
            )
        }
        colors[ColorItem.SHEET]?.let { s = s.copy(surfaceContainerLow = it) }
        colors[ColorItem.CARDS]?.let {
            s = if (s.isDark) s.copy(surfaceContainerHigh = it) else s.copy(surfaceContainerLowest = it)
        }
        colors[ColorItem.TEXT]?.let {
            s = s.copy(onSurface = it, onSurfaceVariant = ColorScheme.blend(it, s.surface, 0.25f))
        }
        colors[ColorItem.HOLIDAY]?.let {
            s = s.copy(
                tertiary = it,
                tertiaryContainer = ColorScheme.blend(s.surface, it, if (s.isDark) 0.35f else 0.2f)
            )
            s = s.copy(onTertiaryContainer = EventColors.onColor(s.tertiaryContainer))
        }
        colors[ColorItem.ACCENT]?.let {
            val container = ColorScheme.blend(s.surface, it, if (s.isDark) 0.38f else 0.22f)
            val secondary = ColorScheme.blend(s.surface, it, if (s.isDark) 0.28f else 0.14f)
            s = s.copy(
                primary = it, onPrimary = EventColors.onColor(it),
                primaryContainer = container, onPrimaryContainer = EventColors.onColor(container),
                secondaryContainer = secondary, onSecondaryContainer = EventColors.onColor(secondary)
            )
        }
        return s
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
    private val WHITE = ColorScheme.opaque("FFFFFF")
}
