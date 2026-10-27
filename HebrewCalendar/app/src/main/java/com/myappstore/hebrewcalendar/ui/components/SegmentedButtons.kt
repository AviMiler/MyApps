package com.myappstore.hebrewcalendar.ui.components

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.theme.Ui.textSp

/**
 * Material 3 Expressive "connected button group": options separated by 2dp gaps,
 * outer corners round, the selected option fully round with a check mark.
 * Built for RTL containers, where the first option is on the right.
 */
object SegmentedButtons {

    fun bind(container: LinearLayout, labels: List<String>, selected: Int, scheme: ColorScheme, onSelect: (Int) -> Unit) {
        val context = container.context
        container.removeAllViews()
        container.orientation = LinearLayout.HORIZONTAL
        labels.forEachIndexed { index, label ->
            val chosen = index == selected
            val button = TextView(context).apply {
                text = label
                gravity = Gravity.CENTER
                textSp(14f)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(if (chosen) scheme.onSecondaryContainer else scheme.onSurfaceVariant)
                background = background(context, index, labels.size, chosen, scheme)
                if (chosen) {
                    val check = Ui.icon(context, R.drawable.ic_check, scheme.onSecondaryContainer)
                    setCompoundDrawablesRelativeWithIntrinsicBounds(check, null, null, null)
                    compoundDrawablePadding = Ui.dp(context, 4f)
                }
                setOnClickListener { if (!chosen) onSelect(index) }
            }
            val params = LinearLayout.LayoutParams(0, Ui.dp(context, 40f), 1f)
            if (index > 0) params.marginStart = Ui.dp(context, 2f)
            container.addView(button, params)
        }
    }

    private fun background(context: Context, index: Int, count: Int, isSelected: Boolean, scheme: ColorScheme) =
        if (isSelected) {
            Ui.rounded(context, scheme.secondaryContainer, 20f)
        } else {
            val outer = 20f
            val inner = 8f
            val isFirst = index == 0
            val isLast = index == count - 1
            // RTL: the first option's outer edge is its right side.
            val right = if (isFirst) outer else inner
            val left = if (isLast) outer else inner
            Ui.rounded(context, scheme.surfaceContainerHigh, left, right, right, left)
        }
}
