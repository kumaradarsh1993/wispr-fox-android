package com.wisprfox.android.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.wisprfox.android.history.RecordingRepository
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.queue.ImportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/** The models + output style chosen on the import sheet for a batch of files. */
data class ImportConfig(
    val sttProvider: String,
    val sttModel: String,
    val mode: DictationMode,
    val llmProvider: String,
    val llmModel: String,
)

/**
 * Entry point for "import an audio file": takes the SAF-picked content Uris,
 * persists read access to them, creates an IMPORTING History row per file with
 * the chosen models stored as overrides, and enqueues an [ImportWorker] to
 * decode + hand off to the transcription pipeline. Deliberately parallel to
 * [RecordingController] (the mic entry point) so imports and live dictation
 * share the same durable, retryable pipeline.
 */
class ImportController(
    context: Context,
    private val recordings: RecordingRepository,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    fun import(uris: List<Uri>, config: ImportConfig) {
        if (uris.isEmpty()) return
        scope.launch {
            var queued = 0
            for (uri in uris) {
                // Persist read access so the (possibly-later, cross-process)
                // worker can still open the file. Best-effort: some providers
                // don't grant persistable permission, in which case the worker
                // still succeeds if it runs while the transient grant is alive.
                runCatching {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                val path = File(importDir(), "${UUID.randomUUID()}.wav").absolutePath
                val id = recordings.newImportRecording(
                    audioPath = path,
                    mode = config.mode,
                    sttProvider = config.sttProvider,
                    sttModel = config.sttModel,
                    llmProvider = config.llmProvider,
                    llmModel = config.llmModel,
                )
                ImportWorker.enqueue(appContext, id, uri.toString())
                queued++
            }
            AppState.toast(
                if (queued == 1) "Importing 1 file — it'll appear in History"
                else "Importing $queued files — they'll appear in History",
            )
        }
    }

    private fun importDir(): File =
        File(appContext.getExternalFilesDir(null), "audio/import").apply { mkdirs() }
}
