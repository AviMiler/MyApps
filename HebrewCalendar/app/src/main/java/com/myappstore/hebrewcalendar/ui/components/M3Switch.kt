package com.myappstore.hebrewcalendar.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.Ui

/**
 * A Material 3 switch: 52x32dp track; off = outlined track with a small thumb,
 * on = filled track with a large thumb showing a check mark. Mirrors in RTL.
 */
class M3Switch(context: Context, private val scheme: ColorScheme) : View(context) {

    var isChecked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Called after the user toggles the switch. */
    var onCheckedChange: ((Boolean) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private val check = Path()

    init {
        isClickable = true
        setOnClickListener {
            isChecked = !isChecked
            onCheckedChange?.invoke(isChecked)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) =
        setMeasuredDimension(Ui.dp(context, 52f), Ui.dp(context, 32f))

    override fun onDraw(canvas: Canvas) {
        val stroke = Ui.dp(context, 2f).toFloat()
        val height = height.toFloat()
        track.set(stroke / 2, stroke / 2, width - stroke / 2, height - stroke / 2)
        val radius = height / 2

        paint.style = Paint.Style.FILL
        paint.color = if (isChecked) scheme.primary else scheme.surfaceContainerHighest
        canvas.drawRoundRect(track, radius, radius, paint)
        if (!isChecked) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = scheme.outline
            canvas.drawRoundRect(track, radius, radius, paint)
        }

        // The thumb sits at the "end" when on: left in RTL, right in LTR.
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val atEnd = isChecked
        val onLeft = atEnd == isRtl
        val centerX = if (onLeft) radius else width - radius
        val centerY = height / 2
        paint.style = Paint.Style.FILL
        paint.color = if (isChecked) scheme.onPrimary else scheme.outline
        canvas.drawCircle(centerX, centerY, Ui.dp(context, if (isChecked) 12f else 8f).toFloat(), paint)

        if (isChecked) {
            val unit = Ui.dp(context, 1f).toFloat()
            check.reset()
            check.moveTo(centerX - 5 * unit, centerY)
            check.lineTo(centerX - 1.5f * unit, centerY + 3.5f * unit)
            check.lineTo(centerX + 5 * unit, centerY - 3.5f * unit)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = scheme.onPrimaryContainer
            canvas.drawPath(check, paint)
        }
    }
}
