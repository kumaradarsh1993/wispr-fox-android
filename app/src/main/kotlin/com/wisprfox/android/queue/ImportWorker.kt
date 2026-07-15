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
import com.wisprfox.android.audio.AudioImporter
import com.wisprfox.android.history.RecordingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

        val outFile = File(rec.audioPath)
        val result = try {
            AudioImporter.decodeToWav(applicationContext, uri, outFile)
        } catch (e: AudioImporter.ImportException) {
            recordings.setError(id, e.message ?: "Couldn't import that audio file.")
            releaseUriPermission(uri)
            return@withContext Result.failure()
        } catch (e: Exception) {
            recordings.setError(id, "Couldn't import that audio file.")
            releaseUriPermission(uri)
            return@withContext Result.failure()
        }

        // We now own a self-contained WAV; the source grant is no longer needed.
        releaseUriPermission(uri)

        recordings.setDuration(id, result.durationMs)
        recordings.setStatus(id, RecordingStatus.TRANSCRIBING)
        TranscribeWorker.enqueue(applicationContext, id)
        Result.success()
    }

    private fun releaseUriPermission(uri: Uri) {
        runCatching {
            applicationContext.contentResolver
                .releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        const val KEY_SOURCE_URI = "source_uri"
        private const val UNIQUE_PREFIX = "import-"

        /** Enqueue a durable decode job for a freshly-created import row. */
        fun enqueue(context: Context, recordingId: String, sourceUri: String) {
            val request = OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(
                    workDataOf(
                        KEY_RECORDING_ID to recordingId,
                        KEY_SOURCE_URI to sourceUri,
                    )
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_PREFIX$recordingId",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
