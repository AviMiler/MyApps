package com.myappstore.claudecam

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Shows a captured photo and lets the user annotate it with finger-drawn
 * strokes. Rotation and stroke history are tracked separately from the
 * bitmap so undo/rotate stay cheap; [renderFlattened] bakes everything
 * into one bitmap at share time.
 */
class DrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var photo: Bitmap? = null
    private var rotationDegrees = 0
    private val strokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null
    private var currentColor = Color.RED

    private val displayMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val imageBounds = RectF()

    private class Stroke(val color: Int) {
        val path = Path()
    }

    fun setPhoto(bitmap: Bitmap) {
        photo = bitmap
        rotationDegrees = 0
        strokes.clear()
        currentStroke = null
        requestLayout()
        invalidate()
    }

    fun setColor(color: Int) {
        currentColor = color
    }

    fun rotate90() {
        rotationDegrees = (rotationDegrees + 90) % 360
        strokes.clear()
        invalidate()
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            invalidate()
        }
    }

    fun clearStrokes() {
        strokes.clear()
        invalidate()
    }

    /** Returns a new bitmap with rotation and all strokes baked in, at full photo resolution. */
    fun renderFlattened(): Bitmap? {
        val bitmap = photo ?: return null

        val rotated = if (rotationDegrees != 0) {
            val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        } else {
            bitmap
        }

        val output = rotated.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val scale = output.width.toFloat() / imageBounds.width().coerceAtLeast(1f)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 10f * scale
        }

        for (stroke in strokes) {
            val scaledPath = Path(stroke.path)
            val toBitmap = Matrix()
            inverseMatrix.invert(toBitmap)
            // strokes are stored in view coordinates; map view -> image -> bitmap
            scaledPath.transform(inverseMatrix)
            strokePaint.color = stroke.color
            canvas.drawPath(scaledPath, strokePaint)
        }
        return output
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recomputeMatrix()
    }

    private fun recomputeMatrix() {
        val bitmap = photo ?: return
        val srcW: Float
        val srcH: Float
        if (rotationDegrees == 90 || rotationDegrees == 270) {
            srcW = bitmap.height.toFloat()
            srcH = bitmap.width.toFloat()
        } else {
            srcW = bitmap.width.toFloat()
            srcH = bitmap.height.toFloat()
        }
        if (width == 0 || height == 0 || srcW == 0f || srcH == 0f) return

        val scale = minOf(width / srcW, height / srcH)
        val dispW = srcW * scale
        val dispH = srcH * scale
        val left = (width - dispW) / 2f
        val top = (height - dispH) / 2f
        imageBounds.set(left, top, left + dispW, top + dispH)

        displayMatrix.reset()
        displayMatrix.postScale(scale, scale)
        displayMatrix.postTranslate(left, top)
        displayMatrix.invert(inverseMatrix)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = photo ?: return
        recomputeMatrix()

        canvas.save()
        canvas.concat(displayMatrix)
        val m = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            when (rotationDegrees) {
                90 -> postTranslate(bitmap.height.toFloat(), 0f)
                180 -> postTranslate(bitmap.width.toFloat(), bitmap.height.toFloat())
                270 -> postTranslate(0f, bitmap.width.toFloat())
            }
        }
        canvas.drawBitmap(bitmap, m, null)
        canvas.restore()

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 10f
        }
        for (stroke in strokes) {
            strokePaint.color = stroke.color
            canvas.drawPath(stroke.path, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (photo == null) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val stroke = Stroke(currentColor)
                stroke.path.moveTo(event.x, event.y)
                currentStroke = stroke
                strokes.add(stroke)
            }
            MotionEvent.ACTION_MOVE -> {
                currentStroke?.path?.lineTo(event.x, event.y)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentStroke = null
            }
            else -> return false
        }
        invalidate()
        return true
    }
}
