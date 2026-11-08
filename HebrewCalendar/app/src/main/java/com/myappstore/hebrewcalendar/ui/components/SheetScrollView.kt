package com.myappstore.hebrewcalendar.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ScrollView

/**
 * The day details' scroll area. While [canPullDown] is true and the content is at its
 * top, dragging down anywhere on it (also on an event or a zman) calls [onPullDown]
 * instead of scrolling, so the full-screen details close from any spot.
 */
class SheetScrollView(context: Context, attrs: AttributeSet?) : ScrollView(context, attrs) {

    var canPullDown: () -> Boolean = { false }
    var onPullDown: () -> Unit = {}

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var triggered = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                triggered = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = event.rawY - downY
                val dx = event.rawX - downX
                if (!triggered && scrollY == 0 && canPullDown() && dy > touchSlop * 2 && dy > Math.abs(dx) * 1.5f) {
                    triggered = true
                    onPullDown()
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }
}
