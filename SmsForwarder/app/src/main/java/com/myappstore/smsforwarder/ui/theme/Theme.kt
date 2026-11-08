package com.myappstore.smsforwarder.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.myappstore.smsforwarder.R

/**
 * "Transit map" palette: warm paper, deep ink, a tangerine signal color and a set of
 * line colors - every route gets one, like a metro line.
 */
@Immutable
data class HalaaColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val sunken: Color,
    val outline: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val brand: Color,
    val brandDeep: Color,
    val onBrand: Color,
    val brandSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val held: Color,
    val heldSoft: Color,
    val night: Color,
    val nightDeep: Color,
    val navBar: Color,
    val navInk: Color,
    val routes: List<Color>,
) {
    fun route(index: Int): Color = routes[Math.floorMod(index, routes.size)]
}

val LightColors = HalaaColors(
    isDark = false,
    background = Color(0xFFF6F2EA),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFFBF8F2),
    sunken = Color(0xFFEFE9DE),
    outline = Color(0xFFE6DED0),
    ink = Color(0xFF1A1D26),
    inkSoft = Color(0xFF5E6372),
    inkFaint = Color(0xFF9AA0AD),
    brand = Color(0xFFFF6A3D),
    brandDeep = Color(0xFFE14E24),
    onBrand = Color(0xFFFFFFFF),
    brandSoft = Color(0xFFFFE7DD),
    success = Color(0xFF1E9E6A),
    successSoft = Color(0xFFDDF3E9),
    warning = Color(0xFFC27A00),
    warningSoft = Color(0xFFFFF0CC),
    danger = Color(0xFFD93A3A),
    dangerSoft = Color(0xFFFDE3E1),
    held = Color(0xFF5B5BD6),
    heldSoft = Color(0xFFE7E7FB),
    night = Color(0xFF2B2F6B),
    nightDeep = Color(0xFF151838),
    navBar = Color(0xFF1A1D26),
    navInk = Color(0xFFB8BDC9),
    routes = listOf(
        Color(0xFFFF6A3D),
        Color(0xFF0FA3A3),
        Color(0xFF3B5BDB),
        Color(0xFF8E44AD),
        Color(0xFFD99A00),
        Color(0xFFD6334A),
        Color(0xFF2B9348),
        Color(0xFF52606D),
    ),
)

val DarkColors = HalaaColors(
    isDark = true,
    background = Color(0xFF0F1117),
    surface = Color(0xFF181B23),
    surfaceAlt = Color(0xFF1E222C),
    sunken = Color(0xFF13161D),
    outline = Color(0xFF2A2F3B),
    ink = Color(0xFFEEF0F4),
    inkSoft = Color(0xFFA6ADBB),
    inkFaint = Color(0xFF6B7280),
    brand = Color(0xFFFF7A4F),
    brandDeep = Color(0xFFFF6A3D),
    onBrand = Color(0xFFFFFFFF),
    brandSoft = Color(0xFF3A2119),
    success = Color(0xFF3CCB8C),
    successSoft = Color(0xFF15302A),
    warning = Color(0xFFF2B33D),
    warningSoft = Color(0xFF33290F),
    danger = Color(0xFFFF6B6B),
    dangerSoft = Color(0xFF3A1A1C),
    held = Color(0xFF8F8CFF),
    heldSoft = Color(0xFF22223F),
    night = Color(0xFF2E3270),
    nightDeep = Color(0xFF12142E),
    navBar = Color(0xFF232833),
    navInk = Color(0xFF9AA1B2),
    routes = listOf(
        Color(0xFFFF7A4F),
        Color(0xFF22C3C3),
        Color(0xFF6C84FF),
        Color(0xFFB36BD6),
        Color(0xFFFFC53D),
        Color(0xFFFF6B7F),
        Color(0xFF45C06A),
        Color(0xFF8A99A8),
    ),
)

val LocalHalaaColors = staticCompositionLocalOf { LightColors }

object Halaa {
    val colors: HalaaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalHalaaColors.current
}

val Rubik = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_semibold, FontWeight.SemiBold),
    Font(R.font.rubik_bold, FontWeight.Bold),
)

/** Display face for big numbers and headlines. */
val Secular = FontFamily(Font(R.font.secular_one, FontWeight.Normal))

private fun rubik(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = Rubik,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
)

private fun secular(size: Int, line: Int) = TextStyle(
    fontFamily = Secular,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = line.sp,
)

val HalaaTypography = Typography(
    displayLarge = secular(64, 68),
    displayMedium = secular(46, 52),
    displaySmall = secular(36, 42),
    headlineLarge = secular(30, 36),
    headlineMedium = secular(26, 32),
    headlineSmall = secular(22, 28),
    titleLarge = rubik(20, 26, FontWeight.SemiBold),
    titleMedium = rubik(16, 22, FontWeight.SemiBold),
    titleSmall = rubik(14, 20, FontWeight.Medium),
    bodyLarge = rubik(16, 24),
    bodyMedium = rubik(14, 21),
    bodySmall = rubik(12, 17),
    labelLarge = rubik(14, 20, FontWeight.Medium),
    labelMedium = rubik(12, 16, FontWeight.Medium),
    labelSmall = rubik(11, 14, FontWeight.Medium),
)

val HalaaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun HalaaColors.toScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = brand,
        onPrimary = onBrand,
        primaryContainer = brandSoft,
        onPrimaryContainer = if (isDark) brand else brandDeep,
        secondary = ink,
        onSecondary = surface,
        secondaryContainer = sunken,
        onSecondaryContainer = ink,
        tertiary = held,
        onTertiary = Color.White,
        tertiaryContainer = heldSoft,
        onTertiaryContainer = held,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceAlt,
        onSurfaceVariant = inkSoft,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = surface,
        inversePrimary = brandSoft,
        error = danger,
        onError = Color.White,
        errorContainer = dangerSoft,
        onErrorContainer = danger,
        outline = outline,
        outlineVariant = outline,
        surfaceBright = surface,
        surfaceDim = sunken,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surfaceAlt,
        surfaceContainer = surfaceAlt,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = sunken,
    )
}

/** The app is Hebrew-first, so it is always laid out right-to-left. */
@Composable
fun HalaaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(
        LocalHalaaColors provides colors,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = colors.toScheme(),
            typography = HalaaTypography,
            shapes = HalaaShapes,
            content = content,
        )
    }
}
