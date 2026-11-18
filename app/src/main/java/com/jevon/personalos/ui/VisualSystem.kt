package com.jevon.personalos.ui

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.painterResource
import com.jevon.personalos.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

internal val LocalMarkDraftDirty = staticCompositionLocalOf<() -> Unit> { {} }
internal val LocalValidateFields = staticCompositionLocalOf { false }
internal val LocalEditorError = staticCompositionLocalOf<String?> { null }
internal val LocalEditorDirty = staticCompositionLocalOf { false }

@Composable
internal fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
internal fun PorosMark() {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
        Image(
            painter = painterResource(R.drawable.ic_poros_mark),
            contentDescription = null,
            modifier = Modifier.size(40.dp).padding(4.dp),
        )
    }
}

/** Each Activity/Dialog window owns its bars; reapply after theme changes and focus returns. */
@Composable
internal fun SystemBarAppearance() {
    val view = LocalView.current
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    val dialogWindow = generateSequence(view as android.view.View?) { it.parent as? android.view.View }
        .filterIsInstance<DialogWindowProvider>().firstOrNull()?.window
    val window = dialogWindow ?: run {
        var context = view.context
        while (context is android.content.ContextWrapper && context !is android.app.Activity) context = context.baseContext
        (context as? android.app.Activity)?.window
    }
    fun apply() {
        if (window != null) WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
    // Dialog parameter updates can restore window flags later in the same composition.
    // Apply to the owning decor again after that dispatch, including live theme changes.
    SideEffect { apply(); view.post { apply() } }
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
fun PorosBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), content = content)
    }
}

@Composable
internal fun PorosCard(modifier: Modifier = Modifier, contentPadding: Dp = 20.dp, verticalSpacing: Dp = 8.dp, content: @Composable ColumnScope.() -> Unit) {
    val tones = LocalAtmosphere.current
    Surface(modifier = modifier.fillMaxWidth(), color = tones.panel, contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large, shadowElevation = 0.dp) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(verticalSpacing), content = content)
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
internal fun LedgerRow(icon: ImageVector, title: String, detail: String, amount: String, positive: Boolean = false, dense: Boolean = false, onClick: () -> Unit) {
    val largeText = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Surface(color = LocalAtmosphere.current.panel, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).semantics(mergeDescendants = true) {}) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = if (dense) 10.dp else 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
