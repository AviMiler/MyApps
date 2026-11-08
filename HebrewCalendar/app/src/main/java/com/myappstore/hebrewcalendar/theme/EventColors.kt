package com.myappstore.hebrewcalendar.theme

import android.graphics.Color

/** Calm event colors: softens Google's vivid calendar colors and picks readable text on any color. */
object EventColors {

    /** Muted swatches offered first in the color picker (all readable with white text). */
    val PALETTE: List<Int> = listOf(
        "3F6FB5", "5B7FBF", "7986CB", "8E6FB5", "B65C7A", "C9604F",
        "D07F3F", "B38F2E", "6B9A5B", "3F8F82", "6F7F8C", "8D6E63"
    ).map(ColorScheme::opaque)

    /**
     * Google's default turquoise/cyan (and other loud tones) become a quieter blue:
     * hue 160-205 is moved to blue, saturation and brightness are capped so
     * white titles on the color stay readable.
     */
    fun soften(color: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        if (hsv[0] in 160f..205f) hsv[0] = 214f
        hsv[1] = hsv[1].coerceAtMost(0.55f)
        hsv[2] = hsv[2].coerceIn(0.45f, 0.74f)
        return Color.HSVToColor(hsv)
    }

    /** @return white or near-black, whichever reads better on [background]. */
    fun onColor(background: Int): Int =
        if (luminance(background) > 0.55f) ColorScheme.opaque("1B1B1F") else ColorScheme.opaque("FFFFFF")

    /** Perceived brightness 0..1 (Rec. 601 weights). */
    fun luminance(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }
}
