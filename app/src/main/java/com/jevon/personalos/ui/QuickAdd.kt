package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private data class QuickAction(val label: String, val route: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickAddSheet(dismiss: () -> Unit, open: (String) -> Unit) {
    val primary = listOf(QuickAction("Add expense", "EXPENSE", AppIcons.Payments),
        QuickAction("New reminder", "new-reminder", AppIcons.RadioButtonUnchecked),
        QuickAction("New event", "new-event", AppIcons.CalendarMonth))
    val other = listOf(QuickAction("Add income", "INCOME", AppIcons.SouthWest), QuickAction("Transfer", "TRANSFER", AppIcons.SwapHoriz),
        QuickAction("New subscription", "subscription/new", AppIcons.Autorenew), QuickAction("Add to Library", "library/new", AppIcons.MenuBook))
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Quick add", style = MaterialTheme.typography.headlineSmall)
            Text("Make a little room for what’s next.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 3
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = columns) {
                primary.forEachIndexed { i, action ->
                    Surface(onClick = { open(action.route) }, modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium, color = if (i == 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(12.dp).heightIn(min = 88.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                            Icon(action.icon, null, Modifier.size(24.dp), tint = if (i == 0) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary)
                            Text(action.label, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
            SectionTitle("More ways to add")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = if (columns == 1) 1 else 2) {
            other.forEach { action ->
                Surface(onClick = { open(action.route) }, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(Modifier.padding(12.dp).heightIn(min = 40.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(action.icon, positive = action.route == "INCOME")
                        Text(action.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Icon(AppIcons.ChevronRight, null, Modifier.size(18.dp))
                    }
                }
            }
            }
        }
    }
}
