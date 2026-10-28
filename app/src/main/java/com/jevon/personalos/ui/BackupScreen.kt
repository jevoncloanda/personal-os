package com.jevon.personalos.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jevon.personalos.backup.BackupViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
internal fun BackupScreen(model: BackupViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    var acknowledged by rememberSaveable { mutableStateOf(false) }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if (uri == null) model.pickerCanceled() else model.export(uri) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri == null) model.pickerCanceled() else model.inspect(uri) }
    BackHandler(state.working != null && state.working != "Restoring records…") { model.cancel() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Keep a copy. Recover with confidence.", style = MaterialTheme.typography.headlineMedium)
        Text("Back up all stored accounts, transactions, categories, budgets and other personal records, including archived history. Appearance stays on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FrostedPanel {
            Text("Your backup file", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("Backups are unencrypted JSON. Anyone who can read the file can read your financial and personal information. Choose a trusted location.", style = MaterialTheme.typography.bodyMedium)
            Text("A checksum checks for damage; it does not verify who created a backup. No cloud account or storage permission is required.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = state.working == null, onClick = {
                create.launch(DateTimeFormatter.ofPattern("'personal-os-'uuuuMMdd-HHmmss'.json'").withZone(ZoneOffset.UTC).format(Instant.now()))
            }, shape = MaterialTheme.shapes.medium, modifier = Modifier.heightIn(min = 48.dp)) { Text("Export backup") }
            OutlinedButton(enabled = state.working == null, onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, shape = MaterialTheme.shapes.medium, modifier = Modifier.heightIn(min = 48.dp)) { Text("Restore backup") }
        }
        if (state.working != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp)); Text(state.working!!, Modifier.weight(1f))
                if (state.working != "Restoring records…") TextButton(onClick = model::cancel) { Text("Cancel") }
            }
        }
        state.message?.let { message ->
            Surface(color = if (state.error) MaterialTheme.colorScheme.errorContainer else LocalAtmosphere.current.panel, shape = MaterialTheme.shapes.medium) {
                Text(message, Modifier.fillMaxWidth().padding(16.dp), color = if (state.error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface)
            }
        }
        state.preview?.let { preview ->
            FrostedPanel {
                Text("Restore preview", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                Text("Created ${preview.createdAt} · app ${preview.appVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                preview.data.counts.forEach { (label, count) -> Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text(count.toString(), style = MaterialTheme.typography.titleSmall) } }
                Text("Restoring replaces all supported personal records on this device. It does not merge records. Export your current data first so you can return to it.", style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = state.working == null, onClick = { acknowledged = false; confirm = true }, shape = MaterialTheme.shapes.medium) { Text("Review replacement") }
                    TextButton(enabled = state.working == null, onClick = model::dismissPreview) { Text("Cancel restore") }
                }
            }
        }
    }
    if (confirm && state.preview != null) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Replace local records?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("All supported local personal records will be replaced by this backup, including transactions and archived history. This cannot be undone without a current backup. Export one first if you have not already.")
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(acknowledged, role = Role.Checkbox, onValueChange = { acknowledged = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(acknowledged, onCheckedChange = null)
                Text("I understand this replaces my local data", Modifier.padding(start = 8.dp))
            }
        } },
        confirmButton = { Button(enabled = acknowledged && state.working == null, onClick = { confirm = false; model.restore() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) { Text("Replace local data") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Keep current data") } })
}
