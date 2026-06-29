package com.wisprfox.android.overlay

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.settings.Avatar

/** Renders the chosen avatar for the current pipeline state. */
@Composable
fun AvatarView(avatar: Avatar, state: PipelineState, modifier: Modifier) {
    val description = when (state) {
        PipelineState.IDLE -> "wispr-fox ready"
        PipelineState.RECORDING -> "wispr-fox recording"
        PipelineState.TRANSCRIBING -> "wispr-fox transcribing"
        PipelineState.CLEANING -> "wispr-fox polishing"
        PipelineState.INJECTING -> "wispr-fox pasting"
        PipelineState.DONE -> "wispr-fox done"
        PipelineState.ERROR -> "wispr-fox error"
    }
    when (avatar) {
        Avatar.FOX -> Image(
            painter = painterResource(avatarFor(state)),
            contentDescription = description,
            modifier = modifier,
        )
        Avatar.CLIPPY -> ClippyAvatar(state, modifier)
    }
}
