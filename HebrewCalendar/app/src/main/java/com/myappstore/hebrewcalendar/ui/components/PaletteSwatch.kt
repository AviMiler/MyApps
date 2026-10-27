package com.myappstore.hebrewcalendar.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.Ui

/**
 * A color option like Android's "Wallpaper & style" picker: a circle split into
 * the palette's main tones, on a rounded tile that is tinted when selected.
 */
class PaletteSwatch(
    context: Context,
    private val scheme: ColorScheme,
    private val main: Int,
    private val light: Int,
    private val accent: Int
) : View(context) {

    var isChosen: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = Ui.dp(context, 52f)
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val size = width.toFloat()
        rect.set(0f, 0f, size, size)
        paint.color = if (isChosen) scheme.secondaryContainer else scheme.surfaceContainerHigh
        val tile = Ui.dp(context, 16f).toFloat()
        canvas.drawRoundRect(rect, tile, tile, paint)

        val inset = Ui.dp(context, 7f).toFloat()
        rect.set(inset, inset, size - inset, size - inset)
        paint.color = main
        canvas.drawArc(rect, 90f, 180f, true, paint)
        paint.color = light
        canvas.drawArc(rect, -90f, 90f, true, paint)
        paint.color = accent
        canvas.drawArc(rect, 0f, 90f, true, paint)

        if (isChosen) {
            val badge = Ui.dp(context, 9f).toFloat()
            val center = Ui.dp(context, 11f).toFloat()
            paint.color = scheme.primary
            canvas.drawCircle(center, size - center, badge, paint)
            paint.color = scheme.onPrimary
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = Ui.dp(context, 2f).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            val unit = Ui.dp(context, 1f).toFloat()
            val cy = size - center
            canvas.drawLine(center - 4 * unit, cy, center - unit, cy + 3 * unit, paint)
            canvas.drawLine(center - unit, cy + 3 * unit, center + 4 * unit, cy - 3 * unit, paint)
            paint.style = Paint.Style.FILL
        }
    }
}
