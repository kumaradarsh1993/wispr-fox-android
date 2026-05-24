package com.wisprfox.android.core

import android.content.Context
import androidx.core.content.ContextCompat
import com.wisprfox.android.audio.RecordingService
import com.wisprfox.android.history.RecordingRepository
import com.wisprfox.android.provider.DictationMode
import com.wisprfox.android.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * The single entry point every activation surface (overlay avatar, QS tile,
 * notification, in-app button) calls. Owns the start/stop decision and creates
 * the DB row + WAV path; the foreground [RecordingService] does the actual
 * capture and, on stop, finalises the WAV and enqueues the pipeline worker.
 *
 * Android analog of the desktop `Flow` state machine, minus push-to-talk:
 * the gesture model here is tap-to-start / tap-to-stop (a toggle).
 */
class RecordingController(
    context: Context,
    private val recordings: RecordingRepository,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext

    /** Tap handler. Starts if idle; stops+processes if recording; ignores taps mid-pipeline. */
    fun toggle(modeOverride: DictationMode? = null) {
        scope.launch {
            when (AppState.state.value.pipeline) {
                PipelineState.RECORDING -> stop()
                PipelineState.IDLE, PipelineState.DONE, PipelineState.ERROR -> start(modeOverride)
                else -> { /* busy transcribing/cleaning/injecting — ignore */ }
            }
        }
    }

    /** Force-start in a specific mode (used by the long-press mode menu). */
    fun startMode(mode: DictationMode) {
        scope.launch {
            if (AppState.state.value.pipeline == PipelineState.RECORDING) {
                // Switch intent: stop current, the menu pick starts fresh.
                stop(); return@launch
            }
            start(mode)
        }
    }

    private suspend fun start(modeOverride: DictationMode?) {
        val settings = settingsStore.settings.first()
        val mode = modeOverride ?: settings.defaultMode

        val dateDir = File(appContext.getExternalFilesDir(null), "audio/${dateStamp()}").apply { mkdirs() }
        val path = File(dateDir, "${UUID.randomUUID()}.wav").absolutePath
        val id = recordings.newRecording(path, mode)

        AppState.update {
            copy(
                pipeline = PipelineState.RECORDING,
                mode = mode,
                activeRecordingId = id,
                elapsedMs = 0,
                totalBytes = 0,
                message = null,
            )
        }
        ContextCompat.startForegroundService(
            appContext,
            RecordingService.startIntent(appContext, path, id, mode),
        )
    }

    private fun stop() {
        // The service finalises the WAV, sets duration, enqueues the worker,
        // and advances AppState to TRANSCRIBING (or back to IDLE if too short).
        appContext.startService(RecordingService.stopIntent(appContext))
    }

    private fun dateStamp(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
