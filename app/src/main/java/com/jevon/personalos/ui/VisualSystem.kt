package com.jevon.personalos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

internal val LocalMarkDraftDirty = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalValidateFields = staticCompositionLocalOf { false }

/** Each Activity/Dialog window owns its bars; reapply after theme changes and focus returns. */
@Composable
internal fun SystemBarAppearance() {
    val view = LocalView.current
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val window = (view.parent as? DialogWindowProvider)?.window ?: run {
        var context = view.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) context = context.baseContext
        (context as? android.app.Activity)?.window
    }
    fun apply() {
        if (window != null && view.hasWindowFocus()) WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
    SideEffect { apply() }
    DisposableEffect(view, window, light) {
        val update = Runnable { apply() }
        // Run after the native focus dispatch (including Activity/Dialog bar restoration),
        // rather than allowing a later listener in that dispatch to restore the old style.
        val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) view.post(update) }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        view.post(update)
        onDispose { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener); view.removeCallbacks(update) }
    }
}

@Composable
fun AtmosphericBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val tones = LocalAtmosphere.current
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier.fillMaxSize().background(Brush.verticalGradient(0f to tones.sky, 0.40f to tones.mist, 1f to tones.ground)), content = content)
    }
}

@Composable
internal fun FrostedPanel(modifier: Modifier = Modifier, contentPadding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val tones = LocalAtmosphere.current
    Surface(modifier = modifier.fillMaxWidth(), color = tones.panel, contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large, border = BorderStroke(1.dp, tones.border), shadowElevation = 0.5.dp) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun IconBadge(icon: ImageVector, positive: Boolean = false, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.size(36.dp), shape = MaterialTheme.shapes.small,
        color = if (positive) LocalAtmosphere.current.positive.copy(alpha = 0.10f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = if (positive) LocalAtmosphere.current.positive else MaterialTheme.colorScheme.primary) }
    }
}

@Composable
internal fun LedgerRow(icon: ImageVector, title: String, detail: String, amount: String, positive: Boolean = false, onClick: () -> Unit) {
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Surface(color = LocalAtmosphere.current.panel.copy(alpha = 0.60f), shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, positive)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (largeText) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(amount, style = MaterialTheme.typography.titleMedium, color = if (positive) LocalAtmosphere.current.positive else MaterialTheme.colorScheme.onSurface)
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(amount, style = MaterialTheme.typography.titleSmall, modifier = Modifier.widthIn(max = 170.dp), color = if (positive) LocalAtmosphere.current.positive else MaterialTheme.colorScheme.onSurface)
                }
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun FinancialProgress(fraction: Float, over: Boolean, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(7.dp),
            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), drawStopIndicator = {})
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
