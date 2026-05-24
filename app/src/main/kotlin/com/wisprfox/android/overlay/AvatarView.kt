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
    when (avatar) {
        Avatar.FOX -> Image(
            painter = painterResource(avatarFor(state)),
            contentDescription = "wispr-fox",
            modifier = modifier,
        )
        Avatar.CLIPPY -> ClippyAvatar(state, modifier)
    }
}
