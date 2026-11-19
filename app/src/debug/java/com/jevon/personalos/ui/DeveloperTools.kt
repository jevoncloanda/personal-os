package com.jevon.personalos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.withTransaction
import com.jevon.personalos.PersonalOsApplication
import com.jevon.personalos.money.TestRecordCandidate
import com.jevon.personalos.money.reviewTestRecords
import com.jevon.personalos.today.DataMode
import com.jevon.personalos.today.TodayViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

@Composable
internal fun DeveloperPreviewAction(model: TodayViewModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { open = true }) { Icon(AppIcons.Code, "Developer previews") }
    if (open) {
        val mode by model.mode.collectAsStateWithLifecycle()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Developer previews") },
            text = { DemoControls(mode) { model.setMode(it); open = false } },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Done") } },
        )
    }
}

@Composable
internal fun DeveloperPreviewBanner(model: TodayViewModel) {
    val mode by model.mode.collectAsStateWithLifecycle()
    if (mode != DataMode.PERSONAL) {
        Text("Developer preview · sample data, never saved", Modifier.padding(horizontal = 24.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun DemoControls(mode: DataMode, setMode: (DataMode) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(if (mode == DataMode.PERSONAL) "Personal data" else "Demo · sample data, never saved", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(DataMode.DEMO to "Populated", DataMode.EMPTY_DEMO to "Empty", DataMode.PERSONAL to "Personal").forEach { (value, label) ->
                    FilterChip(selected = mode == value, onClick = { setMode(value) }, label = { Text(label) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun LazyListScope.developerMoneyTools(section: String, selectSection: (String) -> Unit) {
    if (section !in listOf("Accounts", "History", "Budgets", "Categories")) return
    item {
        var showTestReview by rememberSaveable { mutableStateOf(false) }
        var expandedCandidate by rememberSaveable { mutableStateOf<String?>(null) }
        TextButton(onClick = { showTestReview = true }) { Text("Review possible test records") }
        val db = (LocalContext.current.applicationContext as PersonalOsApplication).database
        val diagnosticResults = remember(db) {
            db.invalidationTracker.createFlow("accounts", "entries", "categories", "budgets", "subscriptions", "subscription_payments")
                .map {
                    db.withTransaction {
                        val dao = db.backupDao()
                        reviewTestRecords(dao.accounts(), dao.entries(), dao.categories(), dao.budgets(), dao.subscriptions(), dao.payments())
                    }
                }
                .map<List<TestRecordCandidate>, Result<List<TestRecordCandidate>>> { Result.success(it) }
                .catch { emit(Result.failure(it)) }
        }
        if (showTestReview) ModalBottomSheet(onDismissRequest = { showTestReview = false }) {
            val review by diagnosticResults.collectAsStateWithLifecycle(initialValue = null)
            LazyColumn(Modifier.fillMaxWidth().testTag("TestRecordReviewList"), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Possible test records", style = MaterialTheme.typography.titleLarge)
                    Text("Only exact P1 test / P15 restore labels from earlier device tests are flagged. A label is a hint, not proof. Related records may be personal. Nothing is deleted here.")
                    Text("Use the existing editors to archive accounts/categories or review individual deletions. Subscription payments must be corrected in Subscriptions. There is no bulk cleanup.", style = MaterialTheme.typography.bodyMedium)
                }
                val result = review
                when {
                    result == null -> item { CircularProgressIndicator() }
                    result.isFailure -> item { Text("Could not load the review. Close and reopen it to try again. Your records are unchanged.") }
                    result.getOrThrow().isEmpty() -> item { Text("No records with these known development labels.") }
                    else -> result.getOrThrow().forEach { candidate ->
                        val candidateKey = "${candidate.section}/${candidate.id}"
                        item(key = candidateKey) {
                            Text(candidate.title, style = MaterialTheme.typography.titleMedium)
                            Text("${candidate.section} · ${candidate.id}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { expandedCandidate = if (expandedCandidate == candidateKey) null else candidateKey }) {
                                Text(if (expandedCandidate == candidateKey) "Hide details" else "Review details and relationships (${candidate.details.size})")
                            }
                        }
                        if (expandedCandidate == candidateKey) {
                            items(candidate.details.size, key = { "$candidateKey/detail/$it" }) { index ->
                                Text(candidate.details[index], style = MaterialTheme.typography.bodyMedium)
                            }
                            item(key = "$candidateKey/open") {
                                TextButton(onClick = { selectSection(candidate.section); showTestReview = false }) { Text("Open ${candidate.section}") }
                            }
                        }
                        item(key = "$candidateKey/divider") { HorizontalDivider() }
                    }
                }
                item { TextButton(onClick = { showTestReview = false }) { Text("Close review") } }
            }
        }
    }
}
