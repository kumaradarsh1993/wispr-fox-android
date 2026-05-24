package com.wisprfox.android.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.provider.DictationMode
import kotlinx.coroutines.delay

/**
 * The floating fox. Single tap = start/stop (toggle). Long-press = haptic +
 * a small mode menu (Raw / Cleaned / Reformat). Drag to reposition. The art
 * swaps with the pipeline state and the fox gently breathes / perks while
 * recording, echoing the desktop floater's feel.
 */
@Composable
fun AvatarOverlay(
    snapshot: AppState.Snapshot,
    onTap: () -> Unit,
    onPickMode: (DictationMode) -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }

    // Auto-dismiss a transient DONE/ERROR bubble after a moment.
    var showBubble by remember { mutableStateOf(false) }
    LaunchedEffect(snapshot.pipeline, snapshot.message) {
        showBubble = snapshot.pipeline != PipelineState.IDLE || snapshot.message != null
        if (snapshot.pipeline == PipelineState.DONE || snapshot.pipeline == PipelineState.ERROR) {
            delay(2500)
            showBubble = false
        }
    }

    // Gentle breathing while idle; quicker perk while recording.
    val transition = rememberInfiniteTransition(label = "avatar")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (snapshot.pipeline == PipelineState.RECORDING) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            tween(if (snapshot.pipeline == PipelineState.RECORDING) 700 else 2600),
            RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        // Speech bubble + (when open) the mode menu sit above the fox.
        AnimatedVisibility(visible = showBubble || menuOpen) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (menuOpen) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 6.dp,
                    ) {
                        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            MenuPill("Raw") { onPickMode(DictationMode.RAW); menuOpen = false }
                            MenuPill("Cleaned") { onPickMode(DictationMode.CLEANED); menuOpen = false }
                            MenuPill("Reformat") { onPickMode(DictationMode.REFORMATTED); menuOpen = false }
                        }
                    }
                } else {
                    bubbleLabel(snapshot.pipeline, snapshot.elapsedMs)?.let { label ->
                        val text = snapshot.message ?: label
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (snapshot.messageIsError) Color(0xFFB3261E) else MaterialTheme.colorScheme.surface,
                            shadowElevation = 4.dp,
                        ) {
                            Text(
                                text,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (snapshot.messageIsError) Color.White else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }

        // The fox.
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(breathe)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (menuOpen) menuOpen = false else onTap()
                        },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        },
                        onDoubleTap = { onOpenApp() },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragEnd = { onDragEnd() },
                    ) { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(avatarFor(snapshot.pipeline)),
                contentDescription = "wispr-fox",
                modifier = Modifier.size(88.dp),
            )
        }
    }
}

@Composable
private fun MenuPill(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClick() }) },
    ) {
        Text(
            label,
            modifier = Modifier
                .background(Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
