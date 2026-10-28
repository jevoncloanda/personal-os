package com.jevon.personalos.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jevon.personalos.data.PersonalDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class BackupState(val working: String? = null, val preview: PreparedBackup? = null, val message: String? = null, val error: Boolean = false)

internal class BackupViewModel(database: PersonalDatabase, private val resolver: ContentResolver, private val appVersion: String,
    private val io: CoroutineDispatcher = Dispatchers.IO) : ViewModel() {
    private val store = BackupStore(database)
    private val mutable = MutableStateFlow(BackupState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun pickerCanceled() { if (mutable.value.working == null) mutable.value = mutable.value.copy(message = "Canceled. Your local records were not changed.", error = false) }
    fun dismissPreview() { if (mutable.value.working == null) mutable.value = BackupState(message = "Restore canceled. Your local records were not changed.") }
    fun cancel() { if (mutable.value.working != "Restoring records…") job?.cancel() }

    fun export(uri: Uri) = run("Writing backup…", failedOutput = uri) {
        val bytes = BackupCodec.encode(store.snapshot(), appVersion)
        currentCoroutineContext().ensureActive()
        requireNotNull(resolver.openOutputStream(uri, "wt")) { "The destination could not be opened." }.use { output ->
            output.write(bytes); output.flush()
        }
        currentCoroutineContext().ensureActive()
        BackupState(message = "Backup saved. Store this unencrypted file in a trusted location.")
    }
    fun inspect(uri: Uri) = run("Checking backup…") {
        val prepared = requireNotNull(resolver.openInputStream(uri)) { "The backup could not be opened." }.use(BackupCodec::decode)
        currentCoroutineContext().ensureActive()
        BackupState(preview = prepared)
    }
    fun restore() {
        val prepared = mutable.value.preview ?: return
        run("Restoring records…") {
            withContext(NonCancellable) {
                store.replace(prepared)
                val complete = BackupState(message = "Restore complete. Money and Today now use the restored records. Appearance was kept.")
                // Commit is a short, noncancelable UI operation. Publish here before dispatching back,
                // so a canceled screen scope cannot report an already committed restore as canceled.
                mutable.value = complete
                complete
            }
        }
    }
    private fun run(label: String, failedOutput: Uri? = null, action: suspend () -> BackupState) {
        if (job?.isActive == true || mutable.value.working != null) return
        mutable.value = mutable.value.copy(working = label, message = null, error = false)
        // Enter cleanup ownership synchronously, before an IO dispatch can be canceled.
        job = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Work and state publication stay inside the IO block so cancellation on dispatch back
                // cannot misreport a transaction that has already committed as a canceled restore.
                withContext(io) { mutable.value = action() }
            } catch (e: Exception) {
                if (mutable.value.working == null) return@launch // A committed success was already published.
                // Cleanup and its final feedback share a noncancelable boundary. Canceling the
                // original job must not discard the result when IO dispatches back to Main.
                withContext(NonCancellable) {
                    val incompleteOutput = failedOutput != null && !withContext(Dispatchers.IO) {
                        runCatching { DocumentsContract.deleteDocument(resolver, failedOutput) }.getOrDefault(false)
                    }
                    val details = when (e.message) {
                        "This is not a Personal OS backup.", "This backup format is not supported. Use an app version that supports it.",
                        "This backup uses an unsupported data schema.", "Backup checksum failed. Choose an undamaged backup.",
                        "Backup exceeds the 8 MiB limit.", "Backup exceeds the 20,000-record limit.", "Too many backup records." -> e.message
                        else -> null
                    }
                    mutable.value = BackupState(message = if (e is CancellationException && incompleteOutput)
                        "Canceled. An incomplete file may remain at the chosen destination; delete it. Local records were not replaced."
                        else if (e is CancellationException) "Canceled. Your local records were not changed."
                        else if (incompleteOutput)
                        "The backup could not be saved. An incomplete file may remain at the chosen destination; delete it. Your local records were not changed."
                        else if (label == "Writing backup…") "Could not save the backup. Check the destination and available space, then try again. Your records were not changed."
                        else "Could not use this backup. ${details ?: "Choose a complete, compatible Personal OS backup and try again."} Existing records were preserved.", error = e !is CancellationException)
                }
            }
        }
    }
}
