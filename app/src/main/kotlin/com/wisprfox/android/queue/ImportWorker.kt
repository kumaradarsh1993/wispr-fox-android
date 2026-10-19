package com.wisprfox.android.queue

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.PipelineProgress
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.audio.AudioImporter
import com.wisprfox.android.audio.AacM4aSink
import com.wisprfox.android.audio.PcmSink
import com.wisprfox.android.audio.WavSink
import com.wisprfox.android.history.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.BufferedInputStream
import java.io.FileInputStream
import org.json.JSONArray

/**
 * Durable first stage for an imported audio file: decode the picked file (any
 * container/codec the device supports) into the pipeline's mono WAV, then hand
 * off to [TranscribeWorker] exactly as a finished mic recording would. Split
 * from transcription so the decode (CPU/local, no network) and the upload
 * (network, retry ladder) each get the WorkManager constraints that fit them,
 * and so a decode survives process death like the rest of the pipeline.
 */
class ImportWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val container get() = WisprFoxApp.container(applicationContext)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_RECORDING_ID) ?: return@withContext Result.failure()
        val uriStr = inputData.getString(KEY_SOURCE_URI) ?: return@withContext Result.failure()
        val recordings = container.recordings
        val rec = recordings.get(id) ?: return@withContext Result.failure()
        val uri = Uri.parse(uriStr)
        val live = AppState.state.value
        if (live.activeRecordingId == null || live.pipeline in setOf(PipelineState.IDLE, PipelineState.DONE, PipelineState.ERROR)) {
            AppState.update { copy(pipeline = PipelineState.IMPORTING, activeRecordingId = id, message = "Preparing audio…", messageIsError = false) }
        }
        PipelineProgress.report(applicationContext, id, "Preparing audio…", null, rec.isMeeting)
        val onProgress: (Int) -> Unit = { percent ->
            PipelineProgress.report(applicationContext, id, "Preparing audio $percent%", percent, rec.isMeeting)
        }

        val outFile = File(rec.audioPath)
        val result = try {
            decodeSource(id, uri, outFile, onProgress)
        } catch (e: AudioImporter.ImportException) {
            val message = e.message ?: "Couldn't import that audio file."
            recordings.setError(id, message)
            surfaceImportError(id, message)
            releaseUriPermission(uri)
            PipelineProgress.clear(applicationContext, id)
            return@withContext Result.failure()
        } catch (e: Exception) {
            val message = "Couldn't import that audio file."
            recordings.setError(id, message)
            surfaceImportError(id, message)
            releaseUriPermission(uri)
            PipelineProgress.clear(applicationContext, id)
            return@withContext Result.failure()
        }

        // We now own a self-contained audio file; the source grant is no longer needed.
        releaseUriPermission(uri)

        recordings.setDuration(id, result.durationMs)
        recordings.setStatus(id, RecordingStatus.TRANSCRIBING)
        PipelineProgress.report(applicationContext, id, "Queued for upload…", null, rec.isMeeting)
        if (inputData.getBoolean(KEY_AUTO_TRANSCRIBE, true)) {
            TranscribeWorker.enqueue(applicationContext, id, importedPipeline = true)
        }
        Result.success()
    }

    private fun surfaceImportError(recordingId: String, message: String) {
        if (AppState.state.value.activeRecordingId == recordingId) {
            AppState.update { copy(pipeline = PipelineState.ERROR, message = message, messageIsError = true, activeRecordingId = null) }
        }
    }

    /**
     * Decode one source, or a durable app-owned manifest in the chosen order.
     * The output format follows [outFile]'s extension: `.m4a` (AAC, what new
     * imports use) or `.wav` (rows created before the compressed upload).
     */
    private fun decodeSource(recordingId: String, uri: Uri, outFile: File, onProgress: (Int) -> Unit): AudioImporter.Result {
        val compressed = !outFile.name.endsWith(".wav", ignoreCase = true)
        val manifest = uri.takeIf { it.scheme == "file" }
            ?.path?.let(::File)
            ?.takeIf { it.name.endsWith(".wisprfox-merge.json") }
            ?: return if (compressed) AudioImporter.decodeToM4a(applicationContext, uri, outFile, onProgress)
            else AudioImporter.decodeToWav(applicationContext, uri, outFile, onProgress)
        val sources = JSONArray(manifest.readText()).let { json ->
            (0 until json.length()).map { Uri.parse(json.getString(it)) }
        }
        if (sources.isEmpty()) throw AudioImporter.ImportException("The merge list was empty.")

        // Merge: decode each part to a temporary WAV (raw PCM is what lets the
        // parts be joined and resampled to one rate), then stream them into one
        // output file.
        var sink: PcmSink? = null
        var targetRate = 0
        var outputFrames = 0L
        try {
            sources.forEachIndexed { index, source ->
                val decoded = File(applicationContext.cacheDir, "merge-$recordingId-$index.wav")
                try {
                    val result = AudioImporter.decodeToWav(applicationContext, source, decoded) { percent ->
                        onProgress((index * 100 + percent) / sources.size)
                    }
                    if (sink == null) {
                        targetRate = result.sampleRate
                        outFile.parentFile?.mkdirs()
                        sink = (if (compressed) AacM4aSink(outFile) else WavSink(outFile)).also { it.start(targetRate) }
                    }
                    outputFrames += appendPcm(decoded, result.sampleRate, targetRate, requireNotNull(sink))
                } finally {
                    runCatching { decoded.delete() }
                }
            }
        } finally {
            runCatching { sink?.close() }
            // Clean every staged source even when one middle segment fails.
            sources.forEach(::releaseUriPermission)
            runCatching { manifest.delete() }
        }
        if (outputFrames == 0L || targetRate == 0) {
            runCatching { outFile.delete() }
            throw AudioImporter.ImportException("The selected files did not contain decodable audio.")
        }
        return AudioImporter.Result(outputFrames * 1000 / targetRate, targetRate)
    }

    /** Append canonical mono PCM WAV, cheaply resampling unlike source rates. */
    private fun appendPcm(source: File, sourceRate: Int, targetRate: Int, writer: PcmSink): Long {
        var accumulator = 0L
        var written = 0L
        val output = ShortArray(4_096)
        var outputSize = 0
        BufferedInputStream(FileInputStream(source)).use { input ->
            var skipped = 0L
            while (skipped < 44L) {
                val n = input.skip(44L - skipped)
                if (n <= 0) throw AudioImporter.ImportException("A decoded audio segment was incomplete.")
                skipped += n
            }
            while (true) {
                val low = input.read()
                val high = input.read()
                if (low < 0 || high < 0) break
                val sample = ((high shl 8) or low).toShort()
                accumulator += targetRate.toLong()
                while (accumulator >= sourceRate) {
                    output[outputSize++] = sample
                    accumulator -= sourceRate.toLong()
                    written++
                    if (outputSize == output.size) {
                        writer.write(output, 0, outputSize)
                        outputSize = 0
                    }
                }
            }
        }
        if (outputSize > 0) writer.write(output, 0, outputSize)
        return written
    }

    private fun releaseUriPermission(uri: Uri) {
        if (uri.scheme == "file") {
            runCatching { uri.path?.let { File(it).delete() } }
            return
        }
        runCatching {
            applicationContext.contentResolver
                .releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_AUTO_TRANSCRIBE = "auto_transcribe"
        private const val UNIQUE_PREFIX = "import-"
        const val PIPELINE_TAG = "import-audio-pipeline"

        /** Enqueue a durable decode job for a freshly-created import row. */
        fun enqueue(context: Context, recordingId: String, sourceUri: String) {
            val request = request(recordingId, sourceUri)
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_PREFIX$recordingId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        /** Preserve the chosen batch order; each decode/STT completes before the next starts. */
        fun enqueueBatch(context: Context, batchId: String, jobs: List<Pair<String, String>>) {
            if (jobs.isEmpty()) return
            val manager = WorkManager.getInstance(context)
            val first = jobs.first()
            var chain = manager.beginUniqueWork(
                "$UNIQUE_PREFIX-batch-$batchId",
                ExistingWorkPolicy.KEEP,
                request(first.first, first.second, autoTranscribe = false),
            ).then(TranscribeWorker.request(first.first, importedPipeline = true))
            jobs.drop(1).forEach { (recordingId, sourceUri) ->
                chain = chain.then(request(recordingId, sourceUri, autoTranscribe = false))
                    .then(TranscribeWorker.request(recordingId, importedPipeline = true))
            }
            chain.enqueue()
        }

        private fun request(recordingId: String, sourceUri: String, autoTranscribe: Boolean = true) =
            OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(
                    workDataOf(
                        KEY_RECORDING_ID to recordingId,
                        KEY_SOURCE_URI to sourceUri,
                        KEY_AUTO_TRANSCRIBE to autoTranscribe,
                    )
                )
                .addTag(PIPELINE_TAG)
                .build()
    }
}
