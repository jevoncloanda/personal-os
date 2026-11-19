package com.jevon.personalos.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.jevon.personalos.R

internal val widgetFrameRadius = 24.dp
internal val widgetPanelRadius = 12.dp

@Composable
internal fun WidgetTitle(title: String, compact: Boolean = false) {
    Text(
        text = title,
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(if (compact) R.color.widget_muted else R.color.widget_accent),
            fontSize = if (compact) 11.sp else 14.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}
