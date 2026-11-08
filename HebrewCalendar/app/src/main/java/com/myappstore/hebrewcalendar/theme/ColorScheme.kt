package com.myappstore.hebrewcalendar.theme

/**
 * Material 3 color roles used by the app and the widget. Views are colored at
 * runtime from one of these, so a palette or light/dark change needs no themes.
 */
data class ColorScheme(
    val isDark: Boolean,
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val tertiary: Int,
    val tertiaryContainer: Int,
    val onTertiaryContainer: Int,
    val surface: Int,
    val surfaceContainerLowest: Int,
    val surfaceContainerLow: Int,
    val surfaceContainer: Int,
    val surfaceContainerHigh: Int,
    val surfaceContainerHighest: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val error: Int,
    val errorContainer: Int,
    val onErrorContainer: Int
) {
    companion object {
        /**
         * Builds a scheme from hex strings (RRGGBB) in the order: primary, onPrimary,
         * primaryContainer, onPrimaryContainer, secondaryContainer, onSecondaryContainer,
         * tertiary, tertiaryContainer, onTertiaryContainer, surface, surfaceContainerLowest,
         * surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
         * onSurface, onSurfaceVariant, outline, outlineVariant.
         * Error roles are the standard Material 3 ones.
         */
        fun fromHex(isDark: Boolean, vararg hex: String): ColorScheme {
            require(hex.size == 19) { "Expected 19 colors, got ${hex.size}" }
            val c = hex.map(::opaque)
            return ColorScheme(
                isDark, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], c[8], c[9], c[10], c[11], c[12],
                c[13], c[14], c[15], c[16], c[17], c[18],
                error = opaque(if (isDark) "FFB4AB" else "BA1A1A"),
                errorContainer = opaque(if (isDark) "93000A" else "FFDAD6"),
                onErrorContainer = opaque(if (isDark) "FFDAD6" else "410002")
            )
        }

        /** @return an opaque ARGB int for "RRGGBB". */
        fun opaque(hex: String): Int = (0xFF000000L or hex.toLong(16)).toInt()

        /** @return [from] moved [amount] (0..1) of the way towards [to], per channel. */
        fun blend(from: Int, to: Int, amount: Float): Int {
            fun channel(shift: Int): Int {
                val a = (from shr shift) and 0xFF
                val b = (to shr shift) and 0xFF
                return (a + (b - a) * amount).toInt().coerceIn(0, 255) shl shift
            }
            return (0xFF shl 24) or channel(16) or channel(8) or channel(0)
        }
    }
}
