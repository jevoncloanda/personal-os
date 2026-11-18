package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
    val primary = listOf(
        QuickAction("Expense", "EXPENSE", AppIcons.Payments),
        QuickAction("Reminder", "new-reminder", AppIcons.Notifications),
        QuickAction("Event", "new-event", AppIcons.CalendarMonth),
    )
    val other = listOf(
        QuickAction("Income", "INCOME", AppIcons.SouthWest),
        QuickAction("Transfer", "TRANSFER", AppIcons.SwapHoriz),
        QuickAction("Subscription", "subscription/new", AppIcons.Autorenew),
        QuickAction("Library", "library/new", AppIcons.MenuBook),
    )
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Quick add", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                    IconButton(onClick = dismiss) { Icon(AppIcons.Close, "Close Quick add") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (stacked) 1 else 3) {
                    primary.forEach { action ->
                        QuickPrimaryAction(action, stacked, Modifier.weight(1f), open)
                    }
                }
                Text("More ways to add", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (stacked) 1 else 2) {
                    other.forEach { action ->
                        Surface(onClick = { open(action.route) }, modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.small, color = LocalAtmosphere.current.panel) {
                            Row(Modifier.padding(12.dp).heightIn(min = 40.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(action.icon, positive = action.route == "INCOME")
                                Text(action.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPrimaryAction(
    action: QuickAction,
    stacked: Boolean,
    modifier: Modifier,
    open: (String) -> Unit,
) {
    val expense = action.route == "EXPENSE"
    val iconContent: @Composable () -> Unit = {
        Surface(Modifier.size(40.dp), shape = CircleShape,
            color = if (expense) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(contentAlignment = Alignment.Center) {
                Icon(action.icon, null, Modifier.size(22.dp), tint = when (action.route) {
                    "EXPENSE" -> MaterialTheme.colorScheme.onPrimary
                    "new-reminder" -> MaterialTheme.colorScheme.error
                    else -> LocalAtmosphere.current.positive
                })
            }
        }
    }
    Surface(onClick = { open(action.route) }, modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (expense) MaterialTheme.colorScheme.secondaryContainer else LocalAtmosphere.current.panel,
        contentColor = if (expense) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface) {
        if (stacked) Row(Modifier.padding(12.dp).heightIn(min = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            iconContent()
            Text(action.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        } else Column(Modifier.padding(12.dp).heightIn(min = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            iconContent()
            Text(action.label, style = MaterialTheme.typography.labelLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
