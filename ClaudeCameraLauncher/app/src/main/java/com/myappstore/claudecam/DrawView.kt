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

enum class DrawMode { NONE, MARKER, BLACKOUT }

/**
 * Shows a captured photo and lets the user annotate it with finger-drawn
 * strokes (a translucent marker, or an opaque blackout/redaction bar).
 *
 * Strokes are stored in the *original, unrotated* photo's pixel space
 * (not screen coordinates), so they stay correctly placed on the photo
 * across rotate90() calls instead of being invalidated by them.
 * [renderFlattened] bakes rotation + strokes into one bitmap at share
 * (or crop) time.
 */
class DrawView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var photo: Bitmap? = null
    private var rotationDegrees = 0
    private val strokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null
    private var touchToBitmapMatrix: Matrix? = null

    var mode: DrawMode = DrawMode.NONE
    var markerColor: Int = Color.YELLOW

    val imageToViewMatrix = Matrix()
    val viewToImageMatrix = Matrix()
    val imageBounds = RectF()

    private class Stroke(val color: Int, val alpha: Int, val widthDp: Float) {
        val path = Path()
    }

    fun hasPhoto(): Boolean = photo != null

    fun setPhoto(bitmap: Bitmap) {
        photo = bitmap
        rotationDegrees = 0
        strokes.clear()
        currentStroke = null
        requestLayout()
        invalidate()
    }

    fun rotate90() {
        rotationDegrees = (rotationDegrees + 90) % 360
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

    /** Rotation-only transform, from original bitmap pixel space to the current rotated orientation. */
    private fun rotationMatrix(bitmap: Bitmap): Matrix {
        return Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            when (rotationDegrees) {
                90 -> postTranslate(bitmap.height.toFloat(), 0f)
                180 -> postTranslate(bitmap.width.toFloat(), bitmap.height.toFloat())
                270 -> postTranslate(0f, bitmap.width.toFloat())
            }
        }
    }

    /** Full transform from original bitmap pixel space straight to view (screen) coordinates. */
    private fun bitmapToViewMatrix(): Matrix? {
        val bitmap = photo ?: return null
        val combined = Matrix(imageToViewMatrix)
        combined.preConcat(rotationMatrix(bitmap))
        return combined
    }

    /** Returns a new bitmap with rotation and all strokes baked in, at full photo resolution. */
    fun renderFlattened(): Bitmap? {
        val bitmap = photo ?: return null
        recomputeMatrix()

        val rotation = rotationMatrix(bitmap)
        val rotated = if (rotationDegrees != 0) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true)
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
        }

        for (stroke in strokes) {
            val rotatedPath = Path(stroke.path)
            rotatedPath.transform(rotation)
            strokePaint.color = stroke.color
            strokePaint.alpha = stroke.alpha
            strokePaint.strokeWidth = stroke.widthDp * scale
            canvas.drawPath(rotatedPath, strokePaint)
        }
        return output
    }

    /** Crops using the current on-screen rendering (view coordinates), baking in rotation/strokes first. */
    fun applyCrop(cropRectInViewCoords: RectF) {
        val flattened = renderFlattened() ?: return
        val pts = floatArrayOf(
            cropRectInViewCoords.left, cropRectInViewCoords.top,
            cropRectInViewCoords.right, cropRectInViewCoords.bottom
        )
        viewToImageMatrix.mapPoints(pts)
        val left = pts[0].coerceIn(0f, flattened.width.toFloat())
        val top = pts[1].coerceIn(0f, flattened.height.toFloat())
        val right = pts[2].coerceIn(0f, flattened.width.toFloat())
        val bottom = pts[3].coerceIn(0f, flattened.height.toFloat())
        if (right - left < 4f || bottom - top < 4f) return

        val cropped = Bitmap.createBitmap(
            flattened, left.toInt(), top.toInt(), (right - left).toInt(), (bottom - top).toInt()
        )
        setPhoto(cropped)
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

        imageToViewMatrix.reset()
        imageToViewMatrix.postScale(scale, scale)
        imageToViewMatrix.postTranslate(left, top)
        imageToViewMatrix.invert(viewToImageMatrix)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmap = photo ?: return
        recomputeMatrix()
        val combined = bitmapToViewMatrix() ?: return

        canvas.save()
        canvas.concat(imageToViewMatrix)
        canvas.drawBitmap(bitmap, rotationMatrix(bitmap), null)
        canvas.restore()

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        for (stroke in strokes) {
            val viewPath = Path(stroke.path)
            viewPath.transform(combined)
            strokePaint.color = stroke.color
            strokePaint.alpha = stroke.alpha
            strokePaint.strokeWidth = stroke.widthDp
            canvas.drawPath(viewPath, strokePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (photo == null || mode == DrawMode.NONE) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val toBitmap = Matrix()
                if (bitmapToViewMatrix()?.invert(toBitmap) != true) return false
                touchToBitmapMatrix = toBitmap

                val stroke = when (mode) {
                    DrawMode.BLACKOUT -> Stroke(Color.BLACK, 255, 34f)
                    else -> Stroke(markerColor, 170, 26f)
                }
                val pt = floatArrayOf(event.x, event.y)
                toBitmap.mapPoints(pt)
                stroke.path.moveTo(pt[0], pt[1])
                currentStroke = stroke
                strokes.add(stroke)
            }
            MotionEvent.ACTION_MOVE -> {
                val toBitmap = touchToBitmapMatrix ?: return false
                val pt = floatArrayOf(event.x, event.y)
                toBitmap.mapPoints(pt)
                currentStroke?.path?.lineTo(pt[0], pt[1])
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentStroke = null
                touchToBitmapMatrix = null
            }
            else -> return false
        }
        invalidate()
        return true
    }
}
