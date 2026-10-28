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
    primary = Color(0xFF2462C3), onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E8FF), onPrimaryContainer = Color(0xFF103566),
    secondary = Color(0xFF4A6283), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0EBF7), onSecondaryContainer = Color(0xFF263E59),
    surface = Color(0xFFF6FAFE), onSurface = Color(0xFF172533),
    background = Color(0xFFE8F3FC), onBackground = Color(0xFF172533),
    surfaceContainer = Color(0xFFEDF4FC), surfaceContainerLow = Color(0xFFF3F8FE),
    surfaceContainerLowest = Color.White, surfaceContainerHigh = Color(0xFFE4EFF9), surfaceContainerHighest = Color(0xFFDCE9F7),
    surfaceBright = Color(0xFFF6FAFE), surfaceDim = Color(0xFFD7E5F2),
    surfaceVariant = Color(0xFFE0EBF5), onSurfaceVariant = Color(0xFF43576A),
    outline = Color(0xFF718599), outlineVariant = Color(0xFFC2D4E5),
    error = Color(0xFFAE302E), onError = Color.White, errorContainer = Color(0xFFFFDAD5), onErrorContainer = Color(0xFF46120F),
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFA8CDFF), onPrimary = Color(0xFF003163),
    primaryContainer = Color(0xFF214B7F), onPrimaryContainer = Color(0xFFDAEBFF),
    secondary = Color(0xFFBCD0EA), onSecondary = Color(0xFF203750),
    secondaryContainer = Color(0xFF304861), onSecondaryContainer = Color(0xFFDDEBFA),
    surface = Color(0xFF12243A), onSurface = Color(0xFFEAF2FD),
    background = Color(0xFF0B1829), onBackground = Color(0xFFEAF2FD),
    surfaceContainer = Color(0xFF1C3049), surfaceContainerLow = Color(0xFF162940),
    surfaceContainerLowest = Color(0xFF0B1829), surfaceContainerHigh = Color(0xFF263C56), surfaceContainerHighest = Color(0xFF304760),
    surfaceBright = Color(0xFF344C66), surfaceDim = Color(0xFF0B1829),
    surfaceVariant = Color(0xFF30455C), onSurfaceVariant = Color(0xFFBACCDD),
    outline = Color(0xFF899FB6), outlineVariant = Color(0xFF3A526C),
    error = Color(0xFFFFB4A9), onError = Color(0xFF690005), errorContainer = Color(0xFF73312E), onErrorContainer = Color(0xFFFFDAD5),
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
private val DayAtmosphere = Atmosphere(Color(0xFFB2DDF8), Color(0xFFE8F3FC), Color(0xFFF2F5FC), Color(0xFFF0F7FD), Color.White.copy(alpha = 0.72f), Color(0xFF176847), Color(0xFF805400))
private val NightAtmosphere = Atmosphere(Color(0xFF173E66), Color(0xFF11273F), Color(0xFF0B1829), Color(0xFF1C334D), Color(0xFFB2DDF8).copy(alpha = 0.14f), Color(0xFF90DAB7), Color(0xFFF0CC84))
val LocalAtmosphere = staticCompositionLocalOf { DayAtmosphere }

@Composable
fun PersonalOsTheme(settings: Settings = Settings(), content: @Composable () -> Unit) {
    val dark = when (settings.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val base = if (dark) DarkColors else LightColors
    val colors = if (settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val dynamic = if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        // Wallpaper personalizes supporting roles; brand blue and readable atmosphere stay stable.
        base.copy(secondary = dynamic.secondary, onSecondary = dynamic.onSecondary, secondaryContainer = dynamic.secondaryContainer,
            onSecondaryContainer = dynamic.onSecondaryContainer, tertiary = dynamic.tertiary, onTertiary = dynamic.onTertiary)
    } else base
    CompositionLocalProvider(LocalAtmosphere provides if (dark) NightAtmosphere else DayAtmosphere) {
        MaterialTheme(colorScheme = colors, typography = AppTypography,
            shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(32.dp)), content = content)
    }
}
