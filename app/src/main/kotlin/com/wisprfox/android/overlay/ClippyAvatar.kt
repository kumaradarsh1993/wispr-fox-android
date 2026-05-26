package com.wisprfox.android.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import com.wisprfox.android.core.PipelineState
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Our "black Clippy" — the stylized paperclip from the desktop floater, ported
 * with its key animation beats:
 *   - idle: eyes blink periodically + pupils dart left/centre/right
 *   - recording: leans forward, an ear unfurls out of the left side (bouncy)
 *     and gently flaps; eyes double-blink to read as attentive
 * Eyes stay ink-black (no robotic colour change). A white halo behind the body
 * gives the desktop's faint-glow edge so it pops on dark backgrounds.
 */
private const val BODY_PATH =
    "M 50 30 C 50 18, 70 18, 70 30 L 70 110 C 70 132, 38 132, 38 110 L 38 50 C 38 38, 58 38, 58 50 L 58 100"
private const val EAR_PATH =
    "M 36 52 C 22 50, 8 58, 6 72 C 6 84, 18 90, 28 84 C 34 78, 36 66, 36 52 Z"

private val INK = Color(0xFF1D1D1F)
private val EAR_FILL = Color(0xFF7A7A7E)

@Composable
fun ClippyAvatar(state: PipelineState, modifier: Modifier) {
    val recording = state == PipelineState.RECORDING

    // Ear unfurls with a bouncy spring; body leans forward while listening.
    val ear by animateFloatAsState(
        targetValue = if (recording) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow),
        label = "ear",
    )
    val lean by animateFloatAsState(
        targetValue = if (recording) -7f else 0f,
        animationSpec = tween(300),
        label = "lean",
    )

    // Blink loop — single blink at rest, double-blink while listening.
    var blink by remember { mutableStateOf(false) }
    LaunchedEffect(recording) {
        while (true) {
            delay(Random.nextLong(2200, 4200))
            blink = true; delay(110); blink = false
            if (recording) { delay(130); blink = true; delay(110); blink = false }
        }
    }
    val eyeOpen by animateFloatAsState(if (blink) 0f else 1f, tween(70), label = "eye")

    // Pupils dart around while idle (they settle centre while recording).
    var look by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(recording) {
        while (true) {
            delay(Random.nextLong(2600, 5200))
            look = if (recording) 0f else listOf(-1f, 0f, 1f, 0f).random()
        }
    }
    val pupilDx by animateFloatAsState(look * 2.2f, tween(420), label = "pupil")

    // Gentle ear flap while listening.
    val flapT = rememberInfiniteTransition(label = "flap")
    val flap by flapT.animateFloat(
        initialValue = -2f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "flapAngle",
    )

    Canvas(modifier) {
        val sc = size.minDimension / 150f
        val offX = size.width / 2f - 54f * sc
        val offY = size.height / 2f - 75f * sc
        fun p(x: Float, y: Float) = Offset(x * sc + offX, y * sc + offY)
        val basePivot = p(54f, 128f)

        rotate(degrees = lean + if (recording) flap * 0.4f else 0f, pivot = basePivot) {
            // Ear — unfurls horizontally from the attach point while recording.
            if (ear > 0.01f) {
                val earPath = PathParser().parsePathString(EAR_PATH).toPath()
                earPath.asAndroidPath().transform(androidMatrix(sc, offX, offY))
                scale(scaleX = ear, scaleY = (0.6f + 0.4f * ear), pivot = p(34f, 54f)) {
                    rotate(degrees = flap, pivot = p(20f, 70f)) {
                        drawPath(earPath, color = Color.White, style = Stroke(width = 5f * sc))
                        drawPath(earPath, color = EAR_FILL, style = Stroke(width = 2.5f * sc))
                    }
                }
            }

            val body = PathParser().parsePathString(BODY_PATH).toPath()
            body.asAndroidPath().transform(androidMatrix(sc, offX, offY))
            // White halo + soft glow, then ink body.
            drawPath(body, Color.White.copy(alpha = 0.45f), style = Stroke(15f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(body, Color.White, style = Stroke(11f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(body, INK, style = Stroke(6f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Brows (white halo then ink).
            drawBrow(p(36f, 36f), p(42f, 32f), p(48f, 36f), sc, Color.White, 7.5f)
            drawBrow(p(60f, 36f), p(66f, 32f), p(72f, 36f), sc, Color.White, 7.5f)
            drawBrow(p(36f, 36f), p(42f, 32f), p(48f, 36f), sc, INK, 3.5f)
            drawBrow(p(60f, 36f), p(66f, 32f), p(72f, 36f), sc, INK, 3.5f)

            // Eyes — black pupils that blink + dart. No colour change.
            for (cx in listOf(44f, 66f)) {
                val center = p(cx, 51f)
                val rx = 8.5f * sc
                val ry = (9.5f * eyeOpen).coerceAtLeast(0.9f) * sc
                drawOval(Color.White, topLeft = Offset(center.x - rx, center.y - ry), size = Size(rx * 2, ry * 2))
                drawOval(INK, topLeft = Offset(center.x - rx, center.y - ry), size = Size(rx * 2, ry * 2), style = Stroke(2f * sc))
                if (eyeOpen > 0.35f) {
                    drawCircle(INK, 4f * sc, Offset(center.x + pupilDx * sc, center.y))
                    drawCircle(Color.White, 1.1f * sc, Offset(center.x + pupilDx * sc - 1.2f * sc, center.y - 1.2f * sc))
                }
            }
        }
    }
}

private fun androidMatrix(sc: Float, offX: Float, offY: Float): android.graphics.Matrix =
    android.graphics.Matrix().apply { setScale(sc, sc); postTranslate(offX, offY) }

private fun DrawScope.drawBrow(a: Offset, c: Offset, b: Offset, sc: Float, color: Color, width: Float) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(a.x, a.y)
        quadraticBezierTo(c.x, c.y, b.x, b.y)
    }
    drawPath(path, color, style = Stroke(width = width * sc, cap = StrokeCap.Round))
}
