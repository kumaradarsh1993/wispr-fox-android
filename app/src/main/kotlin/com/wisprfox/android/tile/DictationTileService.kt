package com.wisprfox.android.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.wisprfox.android.WisprFoxApp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.PipelineState

/**
 * Secondary, zero-overlay activation surface. A Quick Settings tile tap counts
 * as user-initiated, so it's allowed to start the foreground microphone
 * service even from the background (Android 14+ rule). Tile reflects recording
 * state. Single tap toggles start/stop in the user's default mode.
 */
class DictationTileService : TileService() {

    override fun onClick() {
        super.onClick()
        WisprFoxApp.container(this).controller.toggle()
        refresh()
    }

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    private fun refresh() {
        val recording = AppState.state.value.pipeline == PipelineState.RECORDING
        qsTile?.apply {
            state = if (recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (recording) "Recording…" else "wispr-fox"
            updateTile()
        }
    }
}
