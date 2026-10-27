package com.myappstore.hebrewcalendar.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * The month grid container; a horizontal swipe changes the month while taps
 * still reach the day cells. In RTL later months sit to the left, so dragging
 * to the right brings in the next month.
 */
class MonthSwipeLayout(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    /** Called with true for the next month, false for the previous one. */
    var onSwipe: ((toNext: Boolean) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var isSwiping = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        track(event)
        return isSwiping
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        track(event)
        if (event.actionMasked == MotionEvent.ACTION_UP && isSwiping) {
            isSwiping = false
            onSwipe?.invoke(event.x - downX > 0)
        }
        return true
    }

    private fun track(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isSwiping = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                if (dx > touchSlop * 2 && dx > abs(event.y - downY) * 1.5f) isSwiping = true
            }
            MotionEvent.ACTION_CANCEL -> isSwiping = false
        }
    }
}
