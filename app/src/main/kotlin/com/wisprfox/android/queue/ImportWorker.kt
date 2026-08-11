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
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.audio.AudioImporter
import com.wisprfox.android.audio.WavWriter
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

        val outFile = File(rec.audioPath)
        val result = try {
            decodeSource(id, uri, outFile)
        } catch (e: AudioImporter.ImportException) {
            val message = e.message ?: "Couldn't import that audio file."
            recordings.setError(id, message)
            surfaceImportError(id, message)
            releaseUriPermission(uri)
            return@withContext Result.failure()
        } catch (e: Exception) {
            val message = "Couldn't import that audio file."
            recordings.setError(id, message)
            surfaceImportError(id, message)
            releaseUriPermission(uri)
            return@withContext Result.failure()
        }

        // We now own a self-contained WAV; the source grant is no longer needed.
        releaseUriPermission(uri)

        recordings.setDuration(id, result.durationMs)
        recordings.setStatus(id, RecordingStatus.TRANSCRIBING)
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

    /** Decode one source, or a durable app-owned manifest in the chosen order. */
    private fun decodeSource(recordingId: String, uri: Uri, outFile: File): AudioImporter.Result {
        val manifest = uri.takeIf { it.scheme == "file" }
            ?.path?.let(::File)
            ?.takeIf { it.name.endsWith(".wisprfox-merge.json") }
            ?: return AudioImporter.decodeToWav(applicationContext, uri, outFile)
        val sources = JSONArray(manifest.readText()).let { json ->
            (0 until json.length()).map { Uri.parse(json.getString(it)) }
        }
        if (sources.isEmpty()) throw AudioImporter.ImportException("The merge list was empty.")

        var writer: WavWriter? = null
        var targetRate = 0
        var outputFrames = 0L
        try {
            sources.forEachIndexed { index, source ->
                val decoded = File(applicationContext.cacheDir, "merge-$recordingId-$index.wav")
                try {
                    val result = AudioImporter.decodeToWav(applicationContext, source, decoded)
                    if (writer == null) {
                        targetRate = result.sampleRate
                        outFile.parentFile?.mkdirs()
                        writer = WavWriter(outFile, targetRate)
                    }
                    outputFrames += appendPcm(decoded, result.sampleRate, targetRate, requireNotNull(writer))
                } finally {
                    runCatching { decoded.delete() }
                }
            }
        } finally {
            runCatching { writer?.close() }
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
    private fun appendPcm(source: File, sourceRate: Int, targetRate: Int, writer: WavWriter): Long {
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
