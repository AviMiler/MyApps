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
import android.view.ScaleGestureDetector
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

    /** Extra pinch-zoom + pan applied on top of the fit-to-screen mapping — purely a viewing aid. */
    private val zoomMatrix = Matrix()
    private var zoomScale = 1f
    private val minZoom = 1f
    private val maxZoom = 6f
    private var lastFocusX = 0f
    private var lastFocusY = 0f
    private var lastPanX = 0f
    private var lastPanY = 0f

    private val scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newScale = (zoomScale * detector.scaleFactor).coerceIn(minZoom, maxZoom)
            val factor = if (zoomScale == 0f) 1f else newScale / zoomScale
            zoomScale = newScale
            zoomMatrix.postScale(factor, factor, detector.focusX, detector.focusY)
            zoomMatrix.postTranslate(detector.focusX - lastFocusX, detector.focusY - lastFocusY)
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            clampZoomMatrix()
            invalidate()
            return true
        }
    })

    /** Keeps the zoomed image from being dragged so far it leaves an empty gap in the view. */
    private fun clampZoomMatrix() {
        if (imageBounds.isEmpty || width == 0 || height == 0) return
        val rect = RectF(imageBounds)
        zoomMatrix.mapRect(rect)

        val dx = if (rect.width() <= width) {
            (width - rect.width()) / 2f - rect.left
        } else {
            when {
                rect.left > 0 -> -rect.left
                rect.right < width -> width - rect.right
                else -> 0f
            }
        }
        val dy = if (rect.height() <= height) {
            (height - rect.height()) / 2f - rect.top
        } else {
            when {
                rect.top > 0 -> -rect.top
                rect.bottom < height -> height - rect.bottom
                else -> 0f
            }
        }
        zoomMatrix.postTranslate(dx, dy)
    }

    private class Stroke(val color: Int, val alpha: Int, val widthDp: Float) {
        val path = Path()
    }

    fun hasPhoto(): Boolean = photo != null

    fun resetZoom() {
        zoomMatrix.reset()
        zoomScale = 1f
        invalidate()
    }

    fun setPhoto(bitmap: Bitmap) {
        photo = bitmap
        rotationDegrees = 0
        strokes.clear()
        currentStroke = null
        resetZoom()
        requestLayout()
        invalidate()
    }

    fun rotate90() {
        rotationDegrees = (rotationDegrees + 90) % 360
        resetZoom()
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

    /** Full transform from original bitmap pixel space straight to view (screen) coordinates, including pinch-zoom. */
    private fun bitmapToViewMatrix(): Matrix? {
        val bitmap = photo ?: return null
        val combined = Matrix(zoomMatrix)
        combined.preConcat(imageToViewMatrix)
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
        canvas.concat(zoomMatrix)
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
        if (photo == null) return false
        // Always consume DOWN (and everything after) so a pinch that starts as a single finger
        // still gets its second pointer delivered here — returning false on DOWN drops the
        // whole gesture from this view's dispatch chain, not just that one event.
        scaleGestureDetector.onTouchEvent(event)

        if (event.pointerCount > 1) {
            // A second finger joined mid-stroke: it's a pinch, not a draw — drop the in-progress stroke.
            currentStroke = null
            touchToBitmapMatrix = null
            invalidate()
            return true
        }
        if (mode == DrawMode.NONE) {
            // No tool selected: a single finger pans the (possibly zoomed) view instead of drawing.
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastPanX = event.x
                    lastPanY = event.y
                }
                MotionEvent.ACTION_MOVE -> {
                    zoomMatrix.postTranslate(event.x - lastPanX, event.y - lastPanY)
                    lastPanX = event.x
                    lastPanY = event.y
                    clampZoomMatrix()
                    invalidate()
                }
                else -> {}
            }
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val toBitmap = Matrix()
                if (bitmapToViewMatrix()?.invert(toBitmap) != true) return true
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
                val toBitmap = touchToBitmapMatrix ?: return true
                val pt = floatArrayOf(event.x, event.y)
                toBitmap.mapPoints(pt)
                currentStroke?.path?.lineTo(pt[0], pt[1])
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                currentStroke = null
                touchToBitmapMatrix = null
            }
            else -> return true
        }
        invalidate()
        return true
    }
}
