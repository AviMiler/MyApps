package com.myappstore.hebrewcalendar.theme

import com.myappstore.hebrewcalendar.settings.Palette

/**
 * Material 3 tonal schemes for the fixed palettes (as the Material Theme Builder
 * generates them from each seed color). [Palette.WALLPAPER] is resolved by
 * ThemeController from the system palette instead; here it falls back to blue.
 */
object PresetSchemes {

    fun of(palette: Palette, isDark: Boolean): ColorScheme = when (palette) {
        Palette.WALLPAPER, Palette.BLUE -> if (isDark) BLUE_DARK else BLUE_LIGHT
        Palette.GREEN -> if (isDark) GREEN_DARK else GREEN_LIGHT
        Palette.TERRACOTTA -> if (isDark) TERRACOTTA_DARK else TERRACOTTA_LIGHT
        Palette.VIOLET -> if (isDark) VIOLET_DARK else VIOLET_LIGHT
        Palette.TEAL -> if (isDark) TEAL_DARK else TEAL_LIGHT
    }

    private val BLUE_LIGHT = ColorScheme.fromHex(
        false, "36618E", "FFFFFF", "D1E4FF", "001D36", "D7E3F7", "101C2B", "6B5778", "F2DAFF", "251431",
        "F8F9FF", "FFFFFF", "F2F3FA", "ECEEF4", "E6E8EE", "E1E2E8", "191C20", "43474E", "73777F", "C3C6CF"
    )
    private val BLUE_DARK = ColorScheme.fromHex(
        true, "A0CAFD", "003258", "194975", "D1E4FF", "3B4858", "D7E3F7", "D7BEE4", "523F5F", "F2DAFF",
        "111418", "0C0E13", "191C20", "1D2024", "282A2F", "32353A", "E1E2E8", "C3C6CF", "8D9199", "43474E"
    )
    private val GREEN_LIGHT = ColorScheme.fromHex(
        false, "4C662B", "FFFFFF", "CDEDA3", "102000", "DCE7C8", "131F0D", "386663", "BCECE7", "00201E",
        "F9FAEF", "FFFFFF", "F3F4E9", "EEEFE3", "E8E9DE", "E2E3D8", "1A1C16", "44483D", "75796C", "C5C8BA"
    )
    private val GREEN_DARK = ColorScheme.fromHex(
        true, "B1D18A", "1F3701", "354E16", "CDEDA3", "3F4A34", "DCE7C8", "A0D0CB", "1F4E4B", "BCECE7",
        "12140E", "0C0F09", "1A1C16", "1E201A", "282B24", "33362E", "E2E3D8", "C5C8BA", "8F9285", "44483D"
    )
    private val TERRACOTTA_LIGHT = ColorScheme.fromHex(
        false, "8F4C38", "FFFFFF", "FFDBD1", "3A0B01", "FFDBD1", "2C150F", "6C5D2F", "F5E1A7", "231B00",
        "FFF8F6", "FFFFFF", "FFF1ED", "FCEAE5", "F7E4E0", "F1DFDA", "231917", "53433F", "85736E", "D8C2BC"
    )
    private val TERRACOTTA_DARK = ColorScheme.fromHex(
        true, "FFB5A0", "561F0F", "723523", "FFDBD1", "5D4037", "FFDBD1", "D8C58D", "534619", "F5E1A7",
        "1A110F", "140C0A", "231917", "271D1B", "322825", "3D322F", "F1DFDA", "D8C2BC", "A08C87", "53433F"
    )
    private val VIOLET_LIGHT = ColorScheme.fromHex(
        false, "6750A4", "FFFFFF", "EADDFF", "21005D", "E8DEF8", "1D192B", "7D5260", "FFD8E4", "31111D",
        "FEF7FF", "FFFFFF", "F7F2FA", "F3EDF7", "ECE6F0", "E6E0E9", "1D1B20", "49454F", "79747E", "CAC4D0"
    )
    private val VIOLET_DARK = ColorScheme.fromHex(
        true, "D0BCFF", "381E72", "4F378B", "EADDFF", "4A4458", "E8DEF8", "EFB8C8", "633B48", "FFD8E4",
        "141218", "0F0D13", "1D1B20", "211F26", "2B2930", "36343B", "E6E0E9", "CAC4D0", "938F99", "49454F"
    )
    private val TEAL_LIGHT = ColorScheme.fromHex(
        false, "006A6A", "FFFFFF", "9CF1F0", "002020", "CCE8E7", "051F1F", "4B607C", "D3E4FF", "041C35",
        "F4FBFA", "FFFFFF", "EFF5F4", "E9EFEE", "E3E9E9", "DDE4E3", "161D1D", "3F4948", "6F7979", "BEC9C8"
    )
    private val TEAL_DARK = ColorScheme.fromHex(
        true, "80D5D4", "003737", "004F4F", "9CF1F0", "324B4B", "CCE8E7", "B3C8E8", "334863", "D3E4FF",
        "0E1514", "090F0F", "161D1D", "1A2121", "252B2B", "2F3636", "DDE4E3", "BEC9C8", "889392", "3F4948"
    )
}
