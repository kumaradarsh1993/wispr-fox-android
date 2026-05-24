package com.wisprfox.android.ui

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File

/** Tiny remembered MediaPlayer for previewing a recording's WAV in History. */
class AudioPlayerState {
    var playingPath by mutableStateOf<String?>(null)
        internal set

    internal var player: MediaPlayer? = null

    fun toggle(path: String) {
        if (playingPath == path) {
            stop()
            return
        }
        stop()
        if (!File(path).exists()) return
        player = MediaPlayer().apply {
            setDataSource(path)
            setOnCompletionListener { stop() }
            prepare()
            start()
        }
        playingPath = path
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        playingPath = null
    }
}

@Composable
fun rememberAudioPlayer(): AudioPlayerState {
    val state = remember { AudioPlayerState() }
    DisposableEffect(Unit) {
        onDispose { state.stop() }
    }
    return state
}
