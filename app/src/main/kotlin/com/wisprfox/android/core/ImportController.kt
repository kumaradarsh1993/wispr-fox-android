package com.wisprfox.android.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.wisprfox.android.history.RecordingRepository
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.queue.ImportWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** The models + output style chosen on the import sheet for a batch of files. */
data class ImportConfig(
    val sttProvider: String,
    val sttModel: String,
    val mode: DictationMode,
    val llmProvider: String,
    val llmModel: String,
    val diarizationEnabled: Boolean = false,
    val generateCleaned: Boolean = true,
    val generateDraft: Boolean = false,
    val generateMeetingNotes: Boolean = false,
    val strongProvider: String = com.wisprfox.android.provider.ProviderCatalog.LLM_GROQ,
    val strongModel: String = com.wisprfox.android.provider.ProviderCatalog.defaultStrongLlmModel(
        com.wisprfox.android.provider.ProviderCatalog.LLM_GROQ,
    ),
    val merge: Boolean = false,
)

data class StagedAudio(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val modifiedAt: Long?,
    val bytes: Long,
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
    private val _staged = MutableStateFlow(loadStagedQueue())
    val staged: StateFlow<List<StagedAudio>> = _staged.asStateFlow()

    /** ACTION_SEND grants can vanish immediately; copy before showing Upload. */
    fun stageSharedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            val stagedDir = File(appContext.filesDir, "shared-audio").apply { mkdirs() }
            val additions = uris.mapNotNull { uri ->
                runCatching {
                    val meta = queryMeta(uri)
                    val id = UUID.randomUUID().toString()
                    val safeName = meta.first.replace(Regex("[^A-Za-z0-9._ -]"), "_").takeLast(100)
                    val target = File(stagedDir, "$id-$safeName")
                    appContext.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Unable to open shared audio" }
                        target.outputStream().use(input::copyTo)
                    }
                    StagedAudio(id, Uri.fromFile(target), meta.first, meta.second, target.length())
                }.getOrNull()
            }
            _staged.value = _staged.value + additions
            persistStagedQueue()
        }
    }

    fun removeStaged(id: String) {
        _staged.value.firstOrNull { it.id == id }?.uri?.path?.let { runCatching { File(it).delete() } }
        _staged.value = _staged.value.filterNot { it.id == id }
        persistStagedQueue()
    }

    fun reorderStaged(from: Int, to: Int) {
        val list = _staged.value.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
        _staged.value = list
        persistStagedQueue()
    }

    fun processStaged(config: ImportConfig) {
        val files = _staged.value
        if (files.isEmpty()) return
        scope.launch {
            importStaged(files, config)
            _staged.value = emptyList()
            persistStagedQueue()
        }
    }

    fun stagedStorage(): Pair<Int, Long> = _staged.value.size to _staged.value.sumOf { it.bytes }

    fun clearStaged() {
        _staged.value.forEach { it.uri.path?.let { path -> runCatching { File(path).delete() } } }
        _staged.value = emptyList()
        persistStagedQueue()
    }

    fun import(uris: List<Uri>, config: ImportConfig) {
        if (uris.isEmpty()) return
        scope.launch {
            var queued = 0
            val batchId = UUID.randomUUID().toString()
            val batchCreatedAt = System.currentTimeMillis()
            val jobs = mutableListOf<Pair<String, String>>()
            for ((index, uri) in uris.withIndex()) {
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
                    diarizationEnabled = config.diarizationEnabled,
                    requestedOutputs = config.requestedOutputs(),
                    strongProvider = config.strongProvider,
                    strongModel = config.strongModel,
                    sourceName = queryMeta(uri).first,
                    sourceModifiedAt = queryMeta(uri).second,
                    batchId = batchId,
                    batchOrder = index,
                    createdAt = batchCreatedAt,
                )
                jobs += id to uri.toString()
                queued++
            }
            ImportWorker.enqueueBatch(appContext, batchId, jobs)
            AppState.toast(
                if (queued == 1) "Importing 1 file — it'll appear in History"
                else "Importing $queued files — they'll appear in History",
            )
        }
    }

    private suspend fun importStaged(files: List<StagedAudio>, config: ImportConfig) {
            val batchId = UUID.randomUUID().toString()
            val batchCreatedAt = System.currentTimeMillis()
            if (config.merge && files.size > 1) {
                val manifest = File(appContext.filesDir, "shared-audio/$batchId.wisprfox-merge.json")
                manifest.writeText(JSONArray(files.map { it.uri.toString() }).toString())
                val path = File(importDir(), "${UUID.randomUUID()}.wav").absolutePath
                val id = recordings.newImportRecording(
                    audioPath = path,
                    mode = config.outputMode(),
                    sttProvider = config.sttProvider,
                    sttModel = config.sttModel,
                    llmProvider = config.llmProvider,
                    llmModel = config.llmModel,
                    diarizationEnabled = config.diarizationEnabled,
                    requestedOutputs = config.requestedOutputs(),
                    strongProvider = config.strongProvider,
                    strongModel = config.strongModel,
                    sourceName = "${files.first().displayName} + ${files.size - 1} more",
                    sourceModifiedAt = files.mapNotNull { it.modifiedAt }.minOrNull(),
                    batchId = batchId,
                    batchOrder = 0,
                    createdAt = batchCreatedAt,
                )
                ImportWorker.enqueue(appContext, id, Uri.fromFile(manifest).toString())
                AppState.toast("Joining ${files.size} recordings in your chosen order")
                return
            }
            val jobs = mutableListOf<Pair<String, String>>()
            for ((index, staged) in files.withIndex()) {
                val path = File(importDir(), "${UUID.randomUUID()}.wav").absolutePath
                val id = recordings.newImportRecording(
                    audioPath = path,
                    mode = config.outputMode(),
                    sttProvider = config.sttProvider,
                    sttModel = config.sttModel,
                    llmProvider = config.llmProvider,
                    llmModel = config.llmModel,
                    diarizationEnabled = config.diarizationEnabled,
                    requestedOutputs = config.requestedOutputs(),
                    strongProvider = config.strongProvider,
                    strongModel = config.strongModel,
                    sourceName = staged.displayName,
                    sourceModifiedAt = staged.modifiedAt,
                    batchId = batchId,
                    batchOrder = index,
                    createdAt = batchCreatedAt,
                )
                jobs += id to staged.uri.toString()
            }
            ImportWorker.enqueueBatch(appContext, batchId, jobs)
            AppState.toast("Processing ${files.size} shared recording${if (files.size == 1) "" else "s"}")
    }

    private fun queryMeta(uri: Uri): Pair<String, Long?> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "shared-audio"
        var modified: Long? = null
        appContext.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex("last_modified").takeIf { it >= 0 }?.let { modified = c.getLong(it).takeIf { v -> v > 0 } }
            }
        }
        return name to modified
    }

    private fun importDir(): File =
        File(appContext.getExternalFilesDir(null), "audio/import").apply { mkdirs() }

    private val stagedQueueFile: File get() = File(appContext.filesDir, "shared-audio/staged-queue.json")

    private fun loadStagedQueue(): List<StagedAudio> = runCatching {
        val file = stagedQueueFile
        if (!file.isFile) return@runCatching emptyList()
        val json = JSONArray(file.readText())
        (0 until json.length()).mapNotNull { index ->
            val item = json.getJSONObject(index)
            val path = item.getString("path")
            val audio = File(path)
            if (!audio.isFile) null else StagedAudio(
                id = item.getString("id"),
                uri = Uri.fromFile(audio),
                displayName = item.optString("name", audio.name),
                modifiedAt = item.optLong("modified", 0L).takeIf { it > 0 },
                bytes = audio.length(),
            )
        }
    }.getOrDefault(emptyList())

    private fun persistStagedQueue() {
        runCatching {
            stagedQueueFile.parentFile?.mkdirs()
            val json = JSONArray().apply {
                _staged.value.forEach { item ->
                    put(JSONObject().apply {
                        put("id", item.id)
                        put("path", item.uri.path)
                        put("name", item.displayName)
                        item.modifiedAt?.let { put("modified", it) }
                        put("bytes", item.bytes)
                    })
                }
            }
            val temp = File(stagedQueueFile.parentFile, "staged-queue.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(stagedQueueFile)) {
                stagedQueueFile.writeText(json.toString())
                temp.delete()
            }
        }
    }
}

private fun ImportConfig.requestedOutputs(): String = buildList {
    if (generateCleaned) add("cleaned")
    if (generateDraft) add("drafted")
    if (generateMeetingNotes) add("meeting")
}.joinToString(",")

private fun ImportConfig.outputMode(): DictationMode = when {
    generateMeetingNotes -> DictationMode.CLEANED
    generateDraft -> DictationMode.REFORMATTED
    generateCleaned -> DictationMode.CLEANED
    else -> DictationMode.RAW
}
