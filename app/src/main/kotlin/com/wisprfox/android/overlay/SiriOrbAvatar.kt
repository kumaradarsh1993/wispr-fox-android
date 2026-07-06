package com.wisprfox.android.overlay

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import com.wisprfox.android.core.PipelineState

/**
 * The "Apple-like button" avatar (P-3). A circular orb with layered rotating
 * sweep gradients in Siri's blue / purple / teal (with a pink accent, sampled
 * from the desktop `--sc1/--sc2/--sc3` CSS vars). Spin speed varies by state
 * (idle slow, RECORDING fast, TRANSCRIBING/CLEANING medium); a green ring pulse
 * on DONE and a red flash on ERROR echo the desktop `.siri-orb` success/error
 * states. Fully self-contained — no assets, pure Compose drawing.
 *
 * Compose has no conic-gradient, so the desktop's stacked conic-gradients are
 * approximated with several [Brush.sweepGradient] layers rotated independently
 * and blended, which reads as the same swirling-plasma orb.
 */

// Siri palette (desktop clippy/+page.svelte .siri-orb).
private val SIRI_PINK = Color(0xFFFF5FA2)
private val SIRI_BLUE = Color(0xFF5AC8FA)
private val SIRI_PURPLE = Color(0xFFA06BFF)
private val SIRI_TEAL = Color(0xFF34D9C9) // teal accent (audit: blue/purple/teal)
private val SIRI_HEART = Color(0xFFF4F3FB) // near-white core
private val SIRI_GREEN = Color(0xFF34C759)
private val SIRI_RED = Color(0xFFFF453A)

@Composable
fun SiriOrbAvatar(state: PipelineState, modifier: Modifier) {
    // Spin period per state (ms). Fast while listening; medium while
    // transcribing/cleaning; slow at rest — mirrors the desktop --spin var.
    val spinMs = when (state) {
        PipelineState.RECORDING -> 4500
        PipelineState.TRANSCRIBING, PipelineState.CLEANING, PipelineState.INJECTING -> 2200
        else -> 11000
    }

    val transition = rememberInfiniteTransition(label = "siri")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(spinMs, easing = LinearEasing), RepeatMode.Restart),
        label = "siriAngle",
    )
    // A slow breathing scale so the orb feels alive even at rest.
    val breathe by transition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "siriBreathe",
    )

    // Success/error ring accents. Green pulse on DONE, red flash on ERROR.
    val ringColor by animateColorAsState(
        targetValue = when (state) {
            PipelineState.DONE -> SIRI_GREEN
            PipelineState.ERROR -> SIRI_RED
            else -> Color.Transparent
        },
        animationSpec = tween(220),
        label = "siriRing",
    )
    val ringPulse by animateFloatAsState(
        targetValue = if (state == PipelineState.DONE || state == PipelineState.ERROR) 1f else 0f,
        animationSpec = tween(360),
        label = "siriRingPulse",
    )

    Canvas(modifier) {
        val d = size.minDimension
        val r = d / 2f * breathe
        val c = Offset(size.width / 2f, size.height / 2f)

        // Base near-white heart so the swirl reads against dark backgrounds.
        drawCircle(color = SIRI_HEART, radius = r, center = c)

        // Layer 1: primary sweep (purple → blue → teal → purple), spinning.
        rotate(degrees = angle, pivot = c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to SIRI_PURPLE,
                    0.30f to SIRI_BLUE,
                    0.55f to SIRI_TEAL,
                    0.80f to SIRI_PURPLE,
                    1f to SIRI_PURPLE,
                    center = c,
                ),
                radius = r,
                center = c,
                alpha = 0.85f,
            )
        }
        // Layer 2: counter-rotating pink/blue accent, blended for the plasma feel.
        rotate(degrees = -angle * 1.6f, pivot = c) {
            drawCircle(
                brush = Brush.sweepGradient(
                    0f to SIRI_PINK.copy(alpha = 0f),
                    0.25f to SIRI_PINK,
                    0.5f to SIRI_BLUE.copy(alpha = 0f),
                    0.75f to SIRI_TEAL,
                    1f to SIRI_PINK.copy(alpha = 0f),
                    center = c,
                ),
                radius = r,
                center = c,
                alpha = 0.55f,
                blendMode = BlendMode.Screen,
            )
        }

        // Soft light core so the centre glows (radial white → transparent).
        drawCircle(
            brush = Brush.radialGradient(
                0f to SIRI_HEART.copy(alpha = 0.9f),
                0.45f to SIRI_HEART.copy(alpha = 0.25f),
                1f to Color.Transparent,
                center = c,
                radius = r * 0.9f,
            ),
            radius = r,
            center = c,
        )

        // Success/error ring flash.
        if (ringPulse > 0.01f && ringColor != Color.Transparent) {
            drawCircle(
                color = ringColor.copy(alpha = 0.85f * ringPulse),
                radius = r * (0.92f + 0.06f * ringPulse),
                center = c,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = d * 0.06f),
            )
        }
    }
}
