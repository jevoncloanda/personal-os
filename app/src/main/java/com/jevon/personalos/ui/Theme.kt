package com.jevon.personalos.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jevon.personalos.data.Settings
import com.jevon.personalos.data.ThemeMode
import com.jevon.personalos.R

private val LightColors = lightColorScheme(
    primary = Color(0xFF4648D4), onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF), onPrimaryContainer = Color(0xFF29009F),
    secondary = Color(0xFF62549B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEE9FF), onSecondaryContainer = Color(0xFF35266B),
    tertiary = Color(0xFF006C50), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCEFAE9), onTertiaryContainer = Color(0xFF002117),
    surface = Color(0xFFFAF8FF), onSurface = Color(0xFF131B2E),
    background = Color(0xFFFAF8FF), onBackground = Color(0xFF131B2E),
    surfaceContainer = Color(0xFFF2F0FA), surfaceContainerLow = Color(0xFFF6F3FF),
    surfaceContainerLowest = Color.White, surfaceContainerHigh = Color(0xFFEBE8F5), surfaceContainerHighest = Color(0xFFE4E0EF),
    surfaceBright = Color.White, surfaceDim = Color(0xFFE4E0EF),
    surfaceVariant = Color(0xFFEEEBF6), onSurfaceVariant = Color(0xFF585466),
    outline = Color(0xFF767586), outlineVariant = Color(0xFFE3DFEE),
    inverseSurface = Color(0xFF2F3039), inverseOnSurface = Color(0xFFFAF8FF), inversePrimary = Color(0xFFC7BFFF),
    error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7BFFF), onPrimary = Color(0xFF29009F),
    primaryContainer = Color(0xFF8D7FFF), onPrimaryContainer = Color(0xFF23008D),
    secondary = Color(0xFFB8AFFF), onSecondary = Color(0xFF2D2178),
    secondaryContainer = Color(0xFF473C92), onSecondaryContainer = Color(0xFFE4DFFF),
    tertiary = Color(0xFF66DBB3), onTertiary = Color(0xFF003829),
    tertiaryContainer = Color(0xFF164E3D), onTertiaryContainer = Color(0xFF84F8CE),
    surface = Color(0xFF12131B), onSurface = Color(0xFFE3E1ED),
    background = Color(0xFF0D0E16), onBackground = Color(0xFFE3E1ED),
    surfaceContainer = Color(0xFF1E1F28), surfaceContainerLow = Color(0xFF1A1B23),
    surfaceContainerLowest = Color(0xFF0D0E16), surfaceContainerHigh = Color(0xFF292932), surfaceContainerHighest = Color(0xFF33343D),
    surfaceBright = Color(0xFF383842), surfaceDim = Color(0xFF12131B),
    surfaceVariant = Color(0xFF33343D), onSurfaceVariant = Color(0xFFC8C4D7),
    outline = Color(0xFF928EA0), outlineVariant = Color(0xFF353443),
    inverseSurface = Color(0xFFE3E1ED), inverseOnSurface = Color(0xFF2F3039), inversePrimary = Color(0xFF5945D6),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF5E2027), onErrorContainer = Color(0xFFFFDAD6),
)
// One bundled variable face, explicitly instantiated for Android 26+; no network font loading.
@OptIn(ExperimentalTextApi::class)
private val Inter = FontFamily(*listOf(400, 500, 600).map { weight ->
    Font(R.font.inter_variable, weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.opticalSizing(14.sp)))
}.toTypedArray())

private fun type(size: Int, line: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f, figures: Boolean = false) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight,
        letterSpacing = tracking.sp, fontFeatureSettings = if (figures) "tnum" else null)

private val AppTypography = Typography(
    displayLarge = type(48, 56, FontWeight.SemiBold, -1.2f, true),
    displayMedium = type(42, 48, FontWeight.SemiBold, -1.1f, true),
    displaySmall = type(36, 42, FontWeight.SemiBold, -0.9f, true),
    headlineLarge = type(28, 34, FontWeight.SemiBold, -0.7f, true),
    headlineMedium = type(24, 30, FontWeight.SemiBold, -0.5f, true),
    headlineSmall = type(22, 28, FontWeight.SemiBold, -0.4f, true),
    titleLarge = type(20, 26, FontWeight.SemiBold, -0.3f, true),
    titleMedium = type(16, 22, FontWeight.SemiBold, -0.2f, true),
    titleSmall = type(14, 20, FontWeight.Medium, -0.1f, true),
    bodyLarge = type(16, 24), bodyMedium = type(14, 20), bodySmall = type(12, 17, tracking = 0.05f),
    labelLarge = type(14, 20, FontWeight.Medium, -0.1f),
    labelMedium = type(12, 16, FontWeight.Medium, 0.1f), labelSmall = type(11, 16, FontWeight.Medium, 0.1f),
)

@Immutable
data class Atmosphere(val sky: Color, val mist: Color, val ground: Color, val panel: Color, val border: Color, val positive: Color, val warning: Color)
private val DayAtmosphere = Atmosphere(LightColors.background, LightColors.background, LightColors.background, Color.White, LightColors.outlineVariant, Color(0xFF006C50), Color(0xFF805400))
private val NightAtmosphere = Atmosphere(DarkColors.background, DarkColors.background, DarkColors.background, DarkColors.surfaceContainer, DarkColors.outlineVariant, DarkColors.tertiary, Color(0xFFF0CC84))
val LocalAtmosphere = staticCompositionLocalOf { DayAtmosphere }

@Composable
fun PersonalOsTheme(settings: Settings = Settings(), content: @Composable () -> Unit) {
    val dark = when (settings.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val base = if (dark) DarkColors else LightColors
    val colors = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        // Preserve the existing opt-in wallpaper setting without changing Poros primary/surfaces.
        base.copy(secondary = dynamic.secondary, onSecondary = dynamic.onSecondary, secondaryContainer = dynamic.secondaryContainer,
            onSecondaryContainer = dynamic.onSecondaryContainer, tertiary = dynamic.tertiary, onTertiary = dynamic.onTertiary)
    } else base
    CompositionLocalProvider(LocalAtmosphere provides if (dark) NightAtmosphere else DayAtmosphere) {
        MaterialTheme(colorScheme = colors, typography = AppTypography,
            shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(24.dp)), content = content)
    }
}
