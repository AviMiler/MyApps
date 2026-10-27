package com.myappstore.hebrewcalendar.theme

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.settings.TodayShape

/** Small helpers for building Material 3 looking views in code. */
object Ui {

    fun dp(context: Context, value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics).toInt()

    /** A filled rounded rectangle; [radiusDp] large enough gives a pill. */
    fun rounded(context: Context, color: Int, radiusDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(context, radiusDp).toFloat()
        }

    /**
     * A filled rectangle with per-corner radii in dp, given as the corners appear on
     * screen: top-left, top-right, bottom-right, bottom-left.
     */
    fun rounded(context: Context, color: Int, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float) =
        GradientDrawable().apply {
            setColor(color)
            val r = listOf(topLeft, topRight, bottomRight, bottomLeft).map { dp(context, it).toFloat() }
            cornerRadii = floatArrayOf(r[0], r[0], r[1], r[1], r[2], r[2], r[3], r[3])
        }

    /** @return a white vector drawable from res tinted with [color]. */
    fun icon(context: Context, res: Int, color: Int): Drawable =
        requireNotNull(context.getDrawable(res)) { "Missing drawable $res" }.mutate().apply { setTint(color) }

    fun todayShapeRes(shape: TodayShape): Int = when (shape) {
        TodayShape.COOKIE -> R.drawable.shape_cookie
        TodayShape.CIRCLE -> R.drawable.shape_circle
        TodayShape.SQUARE -> R.drawable.shape_square
        TodayShape.UNDERLINE -> R.drawable.shape_underline
    }

    /** Sets a text size in sp multiplied by the user's text-size setting. */
    fun TextView.textSp(size: Float, scale: Float = 1f) = setTextSize(TypedValue.COMPLEX_UNIT_SP, size * scale)

    /** Background of a card/list item resting on a surface: lighter than the surface in both modes. */
    val ColorScheme.cardColor: Int get() = if (isDark) surfaceContainerHigh else surfaceContainerLowest
}
