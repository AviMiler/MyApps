package com.myappstore.hebrewcalendar.ui.components

import android.app.Activity
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.EventColors
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.theme.Ui.textSp
import com.myappstore.hebrewcalendar.ui.SystemBars

/**
 * A real color picker: ready swatches plus hue / saturation / brightness sliders,
 * with a live preview that shows the hex value. Any color can be chosen.
 */
class ColorPickerDialog(private val activity: Activity, private val scheme: ColorScheme) {

    /**
     * @param withNeutrals also offer white/gray/black (for backgrounds and text).
     * @param onReset shown as a neutral "reset" button when not null.
     */
    fun show(title: String, initial: Int, withNeutrals: Boolean, onPick: (Int) -> Unit, onReset: (() -> Unit)? = null) {
        val hsv = FloatArray(3).also { Color.colorToHSV(initial, it) }
        val preview = TextView(activity).apply {
            gravity = Gravity.CENTER
            textSp(16f)
        }
        val sliders = mutableListOf<SeekBar>()

        fun current() = Color.HSVToColor(hsv)
        fun refresh() {
            val color = current()
            preview.text = "#%06X".format(color and 0xFFFFFF)
            preview.setTextColor(EventColors.onColor(color))
            preview.background = Ui.rounded(activity, color, 16f)
        }
        fun syncSliders() {
            sliders[0].progress = hsv[0].toInt()
            sliders[1].progress = (hsv[1] * 100).toInt()
            sliders[2].progress = (hsv[2] * 100).toInt()
        }

        fun slider(label: Int, max: Int, onValue: (Int) -> Unit): View {
            val bar = SeekBar(activity).apply {
                this.max = max
                progressTintList = ColorStateList.valueOf(scheme.primary)
                thumbTintList = ColorStateList.valueOf(scheme.primary)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                        if (!fromUser) return
                        onValue(value)
                        refresh()
                    }
                    override fun onStartTrackingTouch(bar: SeekBar) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar) = Unit
                })
            }
            sliders += bar
            return LinearLayout(activity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(activity).apply {
                    setText(label)
                    textSp(13f)
                    setTextColor(scheme.onSurfaceVariant)
                }, LinearLayout.LayoutParams(Ui.dp(activity, 64f), LayoutParams.WRAP_CONTENT))
                addView(bar, LinearLayout.LayoutParams(0, Ui.dp(activity, 44f), 1f))
            }
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(Ui.dp(activity, 20f), Ui.dp(activity, 8f), Ui.dp(activity, 20f), 0)
        }
        content.addView(preview, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(activity, 56f)))

        val colors = EventColors.PALETTE + if (withNeutrals) NEUTRALS else emptyList()
        colors.chunked(6).forEach { rowColors ->
            val row = LinearLayout(activity).apply { gravity = Gravity.CENTER }
            rowColors.forEach { color ->
                row.addView(View(activity).apply {
                    background = Ui.rounded(activity, color, 20f)
                    setOnClickListener {
                        Color.colorToHSV(color, hsv)
                        syncSliders()
                        refresh()
                    }
                }, LinearLayout.LayoutParams(0, Ui.dp(activity, 40f), 1f).apply {
                    setMargins(Ui.dp(activity, 4f), Ui.dp(activity, 6f), Ui.dp(activity, 4f), Ui.dp(activity, 2f))
                })
            }
            content.addView(row)
        }
        content.addView(slider(R.string.hue, 360) { hsv[0] = it.toFloat() })
        content.addView(slider(R.string.saturation, 100) { hsv[1] = it / 100f })
        content.addView(slider(R.string.brightness, 100) { hsv[2] = it / 100f })
        syncSliders()
        refresh()

        val builder = AlertDialog.Builder(activity, SystemBars.dialogTheme(scheme))
            .setTitle(title)
            .setView(content)
            .setPositiveButton(R.string.choose) { _, _ -> onPick(current()) }
            .setNegativeButton(R.string.cancel, null)
        if (onReset != null) builder.setNeutralButton(R.string.reset) { _, _ -> onReset() }
        builder.show()
    }

    private companion object {
        val NEUTRALS = listOf("FFFFFF", "F2F2F2", "BDBDBD", "757575", "2E3135", "17181B").map(ColorScheme::opaque)
    }
}
