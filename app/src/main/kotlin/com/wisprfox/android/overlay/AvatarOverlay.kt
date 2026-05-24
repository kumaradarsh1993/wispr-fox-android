package com.wisprfox.android.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wisprfox.android.core.AppState
import com.wisprfox.android.core.PipelineState
import com.wisprfox.android.provider.DictationMode
import kotlinx.coroutines.delay

private val FOX_SIZE = 70.dp     // ~15% bigger than the previous 58/54.
private val FOX_IMG = 66.dp
private val MENU_WIDTH = 92.dp   // pills sized so the column width ≈ Foxy → no horizontal drift.

/**
 * The floating avatar. Single tap = start/stop (toggle). Long-press = haptic +
 * a vertical pop-up menu of Raw / Clean / Draft that appears ABOVE Foxy without
 * moving it. Drag to reposition. While listening, a live voice indicator + a
 * cheeky line replace the old (frozen) numeric timer.
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

    var showBubble by remember { mutableStateOf(false) }
    LaunchedEffect(snapshot.pipeline, snapshot.message) {
        showBubble = snapshot.pipeline != PipelineState.IDLE || snapshot.message != null
        if (snapshot.pipeline == PipelineState.DONE || snapshot.pipeline == PipelineState.ERROR) {
            delay(2500); showBubble = false
        }
    }

    // Local 1-second ticker for the listening commentary (the heartbeat-based
    // elapsedMs only updates every 30s, so it can't drive a live label).
    var listenSec by remember { mutableIntStateOf(0) }
    LaunchedEffect(snapshot.pipeline) {
        if (snapshot.pipeline == PipelineState.RECORDING) {
            listenSec = 0
            while (true) { delay(1000); listenSec += 1 }
        }
    }

    val transition = rememberInfiniteTransition(label = "avatar")
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (snapshot.pipeline == PipelineState.RECORDING) 1.1f else 1.03f,
        animationSpec = infiniteRepeatable(
            tween(if (snapshot.pipeline == PipelineState.RECORDING) 650 else 2600),
            RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    // Column is centred on Foxy; the menu stacks ABOVE with a width ≈ Foxy, so
    // opening it grows the window upward (not sideways) and Foxy doesn't move.
    Column(horizontalAlignment = Alignment.CenterHorizontally) {

        AnimatedVisibility(
            visible = menuOpen,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                MenuPill("Raw") { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onPickMode(DictationMode.RAW); menuOpen = false }
                MenuPill("Clean") { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onPickMode(DictationMode.CLEANED); menuOpen = false }
                MenuPill("Draft") { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onPickMode(DictationMode.REFORMATTED); menuOpen = false }
            }
        }

        // Status / cheeky bubble (hidden while the menu is open to avoid stacking).
        AnimatedVisibility(visible = showBubble && !menuOpen) {
            val isRec = snapshot.pipeline == PipelineState.RECORDING
            val text = snapshot.message
                ?: if (isRec) listeningLabel(listenSec) else bubbleLabel(snapshot.pipeline)
            if (text != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (snapshot.messageIsError) Color(0xFFB3261E) else MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                        Text(
                            text,
                            fontSize = 11.sp,
                            color = if (snapshot.messageIsError) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                        if (isRec) {
                            VoiceBars()
                        }
                    }
                }
            }
        }

        // The avatar.
        Box(
            modifier = Modifier
                .size(FOX_SIZE)
                .scale(breathe)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (menuOpen) menuOpen = false else onTap() },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuOpen = true
                        },
                        onDoubleTap = { onOpenApp() },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragEnd = { onDragEnd() }) { change, dragAmount ->
                        change.consume(); onDrag(dragAmount.x, dragAmount.y)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(avatarFor(snapshot.pipeline)),
                contentDescription = "wispr-fox",
                modifier = Modifier.size(FOX_IMG),
            )
        }
    }
}

@Composable
private fun MenuPill(label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 3.dp,
        modifier = Modifier
            .width(MENU_WIDTH)
            .pointerInput(label) { detectTapGestures(onTap = { onClick() }) },
    ) {
        Text(
            label,
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
            textAlign = TextAlign.Center,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Tiny animated equalizer that reads as "I'm hearing you". */
@Composable
private fun VoiceBars() {
    val t = rememberInfiniteTransition(label = "bars")
    val color = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.padding(start = 6.dp).height(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(4) { i ->
            val h by t.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(420 + i * 90), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight(h)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color),
            )
        }
    }
}
