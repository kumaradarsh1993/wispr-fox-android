package com.wisprfox.android.queue

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.MissingKeyException
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.delivery.DeliveryManager
import com.wisprfox.android.history.AltKind
import com.wisprfox.android.history.RecordingStatus
import com.wisprfox.android.provider.CleanupOrchestrator
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.provider.SttError
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Durable pipeline: Whisper STT → optional LLM cleanup (per mode) → deliver.
 * Ported conceptually from the desktop `do_pipeline`, but as a WorkManager job
 * so it survives process death and retries on the flaky Indian-mobile network
 * (retry ladder via exponential backoff + a network constraint).
 *
 * The Room row's `status` is the durable truth; [AppState] is updated in
 * parallel so the live avatar animates while the worker runs in-process.
 */
class TranscribeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val container get() = WisprFoxApp.container(applicationContext)

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_RECORDING_ID) ?: return Result.failure()
        val recordings = container.recordings
        val rec = recordings.get(id) ?: return Result.failure()
        val settings = container.currentSettings()

        // ── STT ────────────────────────────────────────────────────────────
        recordings.setStatus(id, RecordingStatus.TRANSCRIBING)
        if (AppState.state.value.activeRecordingId == id || AppState.state.value.pipeline != PipelineState.IDLE) {
            AppState.setPipeline(PipelineState.TRANSCRIBING)
        }

        val transcript = try {
            val stt = container.providerFactory.stt(settings.sttModel)
            stt.transcribe(rec.audioPath, settings.languageHint).also {
                recordings.setTranscript(id, it.text, stt.name)
            }
        } catch (e: MissingKeyException) {
            return fail(id, e.message ?: "API key missing")
        } catch (e: SttError) {
            // Transient (network/timeout/5xx) → retry; permanent → surface.
            return if (e.isTransient()) retryOrFail(id, e.message ?: "transcription failed")
            else fail(id, friendlySttError(e))
        } catch (e: Exception) {
            return retryOrFail(id, e.message ?: "transcription failed")
        }

        // ── LLM cleanup / drafting (skipped for RAW) ─────────────────────────
        val finalText: String = if (rec.mode.usesLlm) {
            recordings.setStatus(id, RecordingStatus.CLEANING)
            AppState.setPipeline(PipelineState.CLEANING)
            try {
                val llm = container.providerFactory.llm(settings)
                val result = CleanupOrchestrator().clean(
                    raw = transcript.text,
                    mode = rec.mode,
                    systemOverride = null,
                    contextHint = null,
                    provider = llm,
                )
                val altKind = if (rec.mode == DictationMode.REFORMATTED) AltKind.DRAFTED else AltKind.CLEANED
                recordings.setAlt(id, altKind, result.text, llm.name, result.usedLlm, result.note)
                result.text
            } catch (e: MissingKeyException) {
                // No LLM key — degrade gracefully to the raw transcript.
                AppState.toast("Using raw transcript — ${e.providerLabel} key missing")
                transcript.text
            }
        } else {
            transcript.text
        }

        // ── Deliver ──────────────────────────────────────────────────────────
        recordings.setStatus(id, RecordingStatus.INJECTING)
        AppState.setPipeline(PipelineState.INJECTING)
        val channel = container.delivery.deliver(finalText, autoPaste = settings.autoPasteEnabled)
        recordings.setStatus(id, RecordingStatus.DONE)

        AppState.update {
            copy(
                pipeline = PipelineState.DONE,
                message = if (channel == DeliveryManager.Channel.ACCESSIBILITY) "Pasted" else "Copied — paste anywhere",
                messageIsError = false,
                activeRecordingId = null,
            )
        }
        scheduleIdleReset()
        return Result.success()
    }

    /** After a brief success/error dwell, return the avatar to idle (so it
     *  reverts to its sitting art and can hide again with the keyboard). */
    private fun scheduleIdleReset() {
        container.applicationScope.launch {
            kotlinx.coroutines.delay(1800)
            val p = AppState.state.value.pipeline
            if (p == PipelineState.DONE || p == PipelineState.ERROR) {
                AppState.update { copy(pipeline = PipelineState.IDLE, message = null) }
            }
        }
    }

    private suspend fun retryOrFail(id: String, message: String): Result {
        return if (runAttemptCount < MAX_ATTEMPTS) {
            container.recordings.bumpRetry(id)
            Result.retry()
        } else {
            fail(id, message)
        }
    }

    private suspend fun fail(id: String, message: String): Result {
        container.recordings.setError(id, message)
        AppState.update {
            copy(pipeline = PipelineState.ERROR, message = message, messageIsError = true, activeRecordingId = null)
        }
        scheduleIdleReset()
        return Result.failure()
    }

    private fun SttError.isTransient(): Boolean = when (this) {
        is SttError.Network, is SttError.Timeout -> true
        is SttError.Http -> status in 500..599 || status == 429
        else -> false
    }

    private fun friendlySttError(e: SttError): String = when (e) {
        is SttError.Http -> when (e.status) {
            401, 403 -> "Groq key rejected — check Settings."
            413 -> "Recording too large to transcribe."
            else -> "Transcription failed (HTTP ${e.status})."
        }
        is SttError.FileTooLarge -> "Recording too large to transcribe."
        else -> "Transcription failed."
    }

    companion object {
        const val KEY_RECORDING_ID = "recording_id"
        private const val MAX_ATTEMPTS = 5
        private const val UNIQUE_PREFIX = "transcribe-"

        /** Enqueue a durable transcription job for a finished recording. */
        fun enqueue(context: Context, recordingId: String) {
            val request = OneTimeWorkRequestBuilder<TranscribeWorker>()
                .setInputData(workDataOf(KEY_RECORDING_ID to recordingId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                // 5s → 30s → ~2m → ~10m-ish ladder (clamped by WorkManager).
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$UNIQUE_PREFIX$recordingId",
                androidx.work.ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
