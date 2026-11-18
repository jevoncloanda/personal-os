package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
internal fun PickerTrigger(
    label: String,
    value: String,
    icon: ImageVector,
    enabled: Boolean,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    open: () -> Unit,
) {
    val foreground = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
    val secondary = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else foreground
    Surface(onClick = open, enabled = enabled, shape = MaterialTheme.shapes.small,
        color = containerColor, contentColor = foreground,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = "Choose ${label.lowercase()}" }) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary else foreground)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = secondary)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(AppIcons.ExpandMore, null, Modifier.size(18.dp), tint = secondary)
        }
    }
}

@Composable
internal fun MoreOptions(
    summary: String,
    validationError: Boolean? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val open = expanded || (validationError ?: (LocalEditorError.current != null))
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Surface(onClick = { expanded = !open }, enabled = enabled,
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .alpha(if (enabled) 1f else 0.38f)
                .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }) {
            Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("More options", style = MaterialTheme.typography.titleSmall)
                    Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(AppIcons.ExpandMore, null, Modifier.size(20.dp).rotate(if (open) 180f else 0f), tint = MaterialTheme.colorScheme.primary)
            }
        }
        if (open) content()
    }
}

@Composable
internal fun SearchField(label: String, value: String, change: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = change,
        placeholder = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = LocalAtmosphere.current.panel,
            unfocusedContainerColor = LocalAtmosphere.current.panel,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun FormToggle(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    val dirty = LocalMarkDraftDirty.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = { dirty(); change(it) })
        .semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
