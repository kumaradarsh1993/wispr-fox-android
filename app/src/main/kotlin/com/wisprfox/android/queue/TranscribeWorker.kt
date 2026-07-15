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
import com.wisprfox.android.provider.ProviderCatalog
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
        // Imported files carry the models the user picked on the import sheet as
        // per-recording overrides; live dictation leaves them null and this is a
        // no-op that returns the global settings unchanged.
        val settings = container.currentSettings().withRecordingOverrides(rec)

        // ── STT ────────────────────────────────────────────────────────────
        recordings.setStatus(id, RecordingStatus.TRANSCRIBING)
        // RC-1.3: only drive the live avatar if THIS recording owns the live
        // state. On a background retry we've freed the fox to IDLE (and the user
        // may have started a fresh dictation), so we must NOT flip their new
        // recording's avatar back to TRANSCRIBING. The durable Room status above
        // is the source of truth for History regardless.
        if (AppState.state.value.activeRecordingId == id) {
            AppState.setPipeline(PipelineState.TRANSCRIBING)
        }

        val transcript = try {
            val stt = container.providerFactory.stt(settings)
            stt.transcribe(rec.audioPath, settings.languageHint).also {
                recordings.setTranscript(id, it.text, stt.name)
            }
        } catch (e: MissingKeyException) {
            return fail(id, e.message ?: "API key missing")
        } catch (e: SttError) {
            // Transient (network/timeout/5xx) → retry; permanent → surface.
            return if (e.isTransient()) retryOrFail(id, friendlySttError(e, settings.sttProvider))
            else fail(id, friendlySttError(e, settings.sttProvider))
        } catch (e: Exception) {
            return retryOrFail(id, "Transcription failed - check your connection and retry from History.")
        }

        // ── LLM cleanup / drafting (skipped for RAW) ─────────────────────────
        val finalText: String = if (rec.mode.usesLlm) {
            recordings.setStatus(id, RecordingStatus.CLEANING)
            if (AppState.state.value.activeRecordingId == id) {
                AppState.setPipeline(PipelineState.CLEANING)
            }
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
                AppState.toast("Using raw transcript - ${e.providerLabel} key missing")
                val altKind = if (rec.mode == DictationMode.REFORMATTED) AltKind.DRAFTED else AltKind.CLEANED
                recordings.setAlt(id, altKind, transcript.text, settings.llmProvider, false, "clippy_missing_key")
                transcript.text
            }
        } else {
            transcript.text
        }

        // ── Deliver ──────────────────────────────────────────────────────────
        // RC-1.3: if this delivery is the tail of a BACKGROUND retry (the fox
        // was already freed to IDLE on the first retry, and the user has moved
        // on — possibly into a different app or mid-typing), a surprise
        // auto-paste would be jarring and land in the wrong place. So a retried
        // delivery is clipboard-only + a "Transcript ready" notification
        // (posted by DeliveryManager when the app isn't foreground).
        val isBackgroundRetry = runAttemptCount > 0
        // Only drive the live pipeline if THIS recording still owns it. If the
        // user started a fresh dictation while we retried, don't hijack the
        // avatar away from their new recording.
        val ownsLiveState = AppState.state.value.activeRecordingId == id
        if (ownsLiveState && !isBackgroundRetry) {
            recordings.setStatus(id, RecordingStatus.INJECTING)
            AppState.setPipeline(PipelineState.INJECTING)
        }
        // Imported files are never auto-pasted: the user wasn't in a text field
        // when they picked the file, so a surprise paste would land in the wrong
        // place. Their result is clipboard + a "ready" notification, viewable in
        // History (same path as a background retry).
        val channel = container.delivery.deliver(
            text = finalText,
            autoPaste = settings.autoPasteEnabled && !isBackgroundRetry && !rec.imported,
            expectedPackage = rec.targetPackage,
        )
        recordings.setStatus(id, RecordingStatus.DONE)

        if (ownsLiveState && !isBackgroundRetry) {
            AppState.update {
                copy(
                    pipeline = PipelineState.DONE,
                    message = if (channel == DeliveryManager.Channel.ACCESSIBILITY) "Pasted" else "Copied — paste anywhere",
                    messageIsError = false,
                    activeRecordingId = null,
                    targetPackage = null,
                )
            }
            scheduleIdleReset()
        }
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
            // RC-1.3: DON'T hold the avatar/pipeline hostage to the WorkManager
            // retry ladder — on a flaky network that ladder can run for many
            // minutes. Free the fox back to IDLE (only if THIS recording is the
            // one currently driving the live state) and toast that we'll retry
            // in the background. The durable Room status stays TRANSCRIBING, so
            // History still reads as "in flight" and the row is recoverable.
            if (AppState.state.value.activeRecordingId == id) {
                AppState.update {
                    copy(
                        pipeline = PipelineState.IDLE,
                        activeRecordingId = null,
                        targetPackage = null,
                        message = "Network hiccup — will retry in background",
                        messageIsError = false,
                    )
                }
            }
            Result.retry()
        } else {
            fail(id, message)
        }
    }

    private suspend fun fail(id: String, message: String): Result {
        container.recordings.setError(id, message)
        // Only surface the error on the live avatar if THIS recording still owns
        // it. A background retry that finally exhausts its attempts must not
        // yank a fresh recording's avatar into ERROR (RC-1.3); the row is marked
        // errored in Room and shows in History with a Retry affordance.
        if (AppState.state.value.activeRecordingId == id) {
            AppState.update {
                copy(pipeline = PipelineState.ERROR, message = message, messageIsError = true, activeRecordingId = null, targetPackage = null)
            }
            scheduleIdleReset()
        }
        return Result.failure()
    }

    private fun SttError.isTransient(): Boolean = when (this) {
        is SttError.Network, is SttError.Timeout -> true
        is SttError.Http -> status in 500..599 || status == 429
        else -> false
    }

    private fun friendlySttError(e: SttError, provider: String): String {
        val label = ProviderCatalog.label(provider)
        return when (e) {
            is SttError.Http -> when (e.status) {
                401, 403 -> "$label key rejected - check Settings."
                413 -> "Recording too large to transcribe."
                429 -> "$label rate limit - wait a minute and retry from History."
                in 500..599 -> "$label had a server hiccup - retry from History."
                else -> "Transcription failed with $label (HTTP ${e.status})."
            }
            is SttError.FileTooLarge -> "Recording too large to transcribe."
            is SttError.Network -> "Transcription failed with $label - check your connection."
            is SttError.Timeout -> "Transcription took too long with $label - retry from History."
            else -> "Transcription failed with $label."
        }
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
