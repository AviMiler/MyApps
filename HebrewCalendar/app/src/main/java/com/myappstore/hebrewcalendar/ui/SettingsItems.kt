package com.myappstore.hebrewcalendar.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.myappstore.hebrewcalendar.R
import com.myappstore.hebrewcalendar.settings.DisplaySettings
import com.myappstore.hebrewcalendar.settings.Palette
import com.myappstore.hebrewcalendar.settings.TodayShape
import com.myappstore.hebrewcalendar.theme.ColorScheme
import com.myappstore.hebrewcalendar.theme.PresetSchemes
import com.myappstore.hebrewcalendar.theme.SystemPalette
import com.myappstore.hebrewcalendar.theme.ThemeController
import com.myappstore.hebrewcalendar.theme.Ui
import com.myappstore.hebrewcalendar.theme.Ui.cardColor
import com.myappstore.hebrewcalendar.theme.Ui.textSp
import com.myappstore.hebrewcalendar.ui.components.M3Switch
import com.myappstore.hebrewcalendar.ui.components.PaletteSwatch

/** Building blocks of the settings screen in Android 16 Settings style. */
class SettingsItems(private val context: Context, private val scheme: ColorScheme) {

    fun title(value: String): View = text(value, 32f).apply {
        setPadding(dp(20f), dp(8f), dp(20f), dp(16f))
    }

    fun section(value: String): View = text(value, 14f, scheme.primary).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(32f), dp(20f), dp(32f), dp(8f))
    }

    /** Stacks [items] with 2dp gaps: large outer corners, small inner ones. */
    fun group(vararg items: View): View {
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), 0, dp(16f), 0)
        }
        items.forEachIndexed { index, item ->
            val top = if (index == 0) 20f else 4f
            val bottom = if (index == items.lastIndex) 20f else 4f
            item.background = Ui.rounded(context, scheme.cardColor, top, top, bottom, bottom)
            val params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            if (index > 0) params.topMargin = dp(2f)
            group.addView(item, params)
        }
        return group
    }

    fun paletteRow(selected: Palette, onSelect: (Palette) -> Unit): View {
        val row = LinearLayout(context).apply { gravity = Gravity.CENTER }
        val palettes = Palette.values().filter { it != Palette.WALLPAPER || ThemeController.supportsWallpaperColors() }
        palettes.forEach { palette ->
            val (main, light, accent) = swatchColors(palette)
            val swatch = PaletteSwatch(context, scheme, main, light, accent).apply {
                isChosen = palette == selected
                contentDescription = context.getString(PALETTE_NAMES.getValue(palette))
                setOnClickListener { onSelect(palette) }
            }
            row.addView(swatch, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }
        val hint = if (selected == Palette.WALLPAPER) R.string.palette_wallpaper_hint else PALETTE_NAMES.getValue(selected)
        return column(row, text(context.getString(hint), 13f, scheme.onSurfaceVariant).apply { setPadding(dp(4f), dp(10f), 0, 0) })
    }

    fun shapePicker(selected: TodayShape, onSelect: (TodayShape) -> Unit): View {
        val row = LinearLayout(context).apply { setPadding(0, dp(10f), 0, 0) }
        TodayShape.values().forEach { shape ->
            val chosen = shape == selected
            val option = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(8f), 0, dp(8f))
                if (chosen) background = Ui.rounded(context, scheme.secondaryContainer, 14f)
                setOnClickListener { onSelect(shape) }
            }
            val sample = text("26", 13f, if (shape == TodayShape.UNDERLINE) scheme.primary else scheme.onPrimary).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = Ui.icon(context, Ui.todayShapeRes(shape), scheme.primary)
            }
            option.addView(sample, LinearLayout.LayoutParams(dp(32f), dp(32f)))
            option.addView(text(context.getString(SHAPE_NAMES.getValue(shape)), 12f,
                if (chosen) scheme.onSecondaryContainer else scheme.onSurfaceVariant).apply { setPadding(0, dp(4f), 0, 0) })
            row.addView(option, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4f) })
        }
        return column(text(context.getString(R.string.today_marker), 16f), row)
    }

    fun switchRow(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit): View {
        val toggle = M3Switch(context, scheme).apply {
            isChecked = checked
            contentDescription = label
            onCheckedChange = onChange
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(text(label, 16f))
        hint?.let { texts.addView(text(it, 13f, scheme.onSurfaceVariant)) }
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64f)
            setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
            addView(texts, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle)
            setOnClickListener { toggle.performClick() }
        }
    }

    fun textSizeSlider(index: Int, onChange: (Int) -> Unit): View {
        val slider = SeekBar(context).apply {
            max = DisplaySettings.TEXT_SCALES.lastIndex
            progress = index
            progressTintList = ColorStateList.valueOf(scheme.primary)
            progressBackgroundTintList = ColorStateList.valueOf(scheme.secondaryContainer)
            thumbTintList = ColorStateList.valueOf(scheme.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) = Unit
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = onChange(bar.progress)
            })
        }
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(text("א", 12f, scheme.onSurfaceVariant))
            addView(slider, LinearLayout.LayoutParams(0, dp(48f), 1f))
            addView(text("א", 20f, scheme.onSurfaceVariant))
        }
        return column(text(context.getString(R.string.text_size), 16f), row)
    }

    private fun column(vararg views: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
        views.forEach { addView(it) }
    }

    private fun swatchColors(palette: Palette): Triple<Int, Int, Int> {
        if (palette == Palette.WALLPAPER) {
            val system = SystemPalette(context)
            return Triple(system.accent1(500), system.accent1(200), system.accent3(400))
        }
        val light = PresetSchemes.of(palette, isDark = false)
        return Triple(light.primary, light.primaryContainer, light.tertiary)
    }

    private fun text(value: String, size: Float, color: Int = scheme.onSurface) = TextView(context).apply {
        text = value
        textSp(size)
        setTextColor(color)
    }

    private fun dp(value: Float) = Ui.dp(context, value)

    private companion object {
        val PALETTE_NAMES = mapOf(
            Palette.WALLPAPER to R.string.palette_wallpaper, Palette.BLUE to R.string.palette_blue,
            Palette.GREEN to R.string.palette_green, Palette.TERRACOTTA to R.string.palette_terracotta,
            Palette.VIOLET to R.string.palette_violet, Palette.TEAL to R.string.palette_teal
        )
        val SHAPE_NAMES = mapOf(
            TodayShape.COOKIE to R.string.shape_cookie, TodayShape.CIRCLE to R.string.shape_circle,
            TodayShape.SQUARE to R.string.shape_square, TodayShape.UNDERLINE to R.string.shape_underline
        )
    }
}
