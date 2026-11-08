package com.myappstore.claudecam

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Darkens everything outside a draggable crop rectangle, with corner
 * handles for resizing and a drag-anywhere-inside to move it.
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val rect = RectF()
    private val bounds = RectF()
    private val handleRadiusPx = 28f * resources.displayMetrics.density
    private val touchSlopPx = 40f * resources.displayMetrics.density

    private var dragHandle = Handle.NONE
    private var lastX = 0f
    private var lastY = 0f

    private enum class Handle { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private val dimPaint = Paint().apply { color = Color.parseColor("#99000000") }
    private val rectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    fun startCrop(imageBounds: RectF) {
        bounds.set(imageBounds)
        val inset = minOf(imageBounds.width(), imageBounds.height()) * 0.1f
        rect.set(
            imageBounds.left + inset, imageBounds.top + inset,
            imageBounds.right - inset, imageBounds.bottom - inset
        )
        visibility = VISIBLE
        invalidate()
    }

    fun currentRect(): RectF = RectF(rect)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (bounds.isEmpty) return

        val path = Path()
        path.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        path.addRect(rect, Path.Direction.CCW)
        canvas.drawPath(path, dimPaint)

        canvas.drawRect(rect, rectPaint)
        canvas.drawCircle(rect.left, rect.top, handleRadiusPx / 2, handlePaint)
        canvas.drawCircle(rect.right, rect.top, handleRadiusPx / 2, handlePaint)
        canvas.drawCircle(rect.left, rect.bottom, handleRadiusPx / 2, handlePaint)
        canvas.drawCircle(rect.right, rect.bottom, handleRadiusPx / 2, handlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (bounds.isEmpty) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragHandle = hitTest(event.x, event.y)
                lastX = event.x
                lastY = event.y
                return dragHandle != Handle.NONE
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                applyDrag(dx, dy)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragHandle = Handle.NONE
                return true
            }
        }
        return false
    }

    private fun hitTest(x: Float, y: Float): Handle {
        fun near(px: Float, py: Float) = (x - px) * (x - px) + (y - py) * (y - py) <= touchSlopPx * touchSlopPx
        return when {
            near(rect.left, rect.top) -> Handle.TOP_LEFT
            near(rect.right, rect.top) -> Handle.TOP_RIGHT
            near(rect.left, rect.bottom) -> Handle.BOTTOM_LEFT
            near(rect.right, rect.bottom) -> Handle.BOTTOM_RIGHT
            rect.contains(x, y) -> Handle.MOVE
            else -> Handle.NONE
        }
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val minSize = handleRadiusPx * 2
        when (dragHandle) {
            Handle.MOVE -> {
                var newLeft = rect.left + dx
                var newTop = rect.top + dy
                newLeft = newLeft.coerceIn(bounds.left, bounds.right - rect.width())
                newTop = newTop.coerceIn(bounds.top, bounds.bottom - rect.height())
                rect.offsetTo(newLeft, newTop)
            }
            Handle.TOP_LEFT -> {
                rect.left = (rect.left + dx).coerceIn(bounds.left, rect.right - minSize)
                rect.top = (rect.top + dy).coerceIn(bounds.top, rect.bottom - minSize)
            }
            Handle.TOP_RIGHT -> {
                rect.right = (rect.right + dx).coerceIn(rect.left + minSize, bounds.right)
                rect.top = (rect.top + dy).coerceIn(bounds.top, rect.bottom - minSize)
            }
            Handle.BOTTOM_LEFT -> {
                rect.left = (rect.left + dx).coerceIn(bounds.left, rect.right - minSize)
                rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSize, bounds.bottom)
            }
            Handle.BOTTOM_RIGHT -> {
                rect.right = (rect.right + dx).coerceIn(rect.left + minSize, bounds.right)
                rect.bottom = (rect.bottom + dy).coerceIn(rect.top + minSize, bounds.bottom)
            }
            Handle.NONE -> {}
        }
    }
}
