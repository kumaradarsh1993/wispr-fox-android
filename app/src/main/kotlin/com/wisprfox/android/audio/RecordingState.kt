package com.wisprfox.android.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Process-wide observable state for the spike. Production code will
 * replace this with a proper repository, but the spike just needs the
 * UI and service to share visibility.
 */
object RecordingState {
    data class Snapshot(
        val isRecording: Boolean = false,
        val elapsedMs: Long = 0,
        val totalBytes: Long = 0,
        val outputPath: String? = null,
    )

    private val _state = MutableStateFlow(Snapshot())
    val state: StateFlow<Snapshot> = _state

    fun update(transform: Snapshot.() -> Snapshot) {
        _state.value = _state.value.transform()
    }

    fun reset() {
        _state.value = Snapshot()
    }
}
