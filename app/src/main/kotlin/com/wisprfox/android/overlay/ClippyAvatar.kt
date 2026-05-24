package com.wisprfox.android.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import com.wisprfox.android.core.PipelineState

/**
 * Our own "black Clippy" — the stylized paperclip from the desktop floater
 * (src/lib ClippyFloater stylized skin), ported to a Compose Canvas. The body
 * path is the exact SVG from desktop; eyes/brows are drawn on top. State is
 * conveyed by the eye/accent colour (the speech bubble carries the words), and
 * a small "listening" ear pops out while recording.
 */
private const val BODY_PATH =
    "M 50 30 C 50 18, 70 18, 70 30 L 70 110 C 70 132, 38 132, 38 110 L 38 50 C 38 38, 58 38, 58 50 L 58 100"

private val INK = Color(0xFF1D1D1F)

@Composable
fun ClippyAvatar(state: PipelineState, modifier: Modifier) {
    Canvas(modifier) {
        // Desktop content lives roughly in x[36..72], y[18..132]; centre ~(54,75).
        val sc = size.minDimension / 150f
        val offX = size.width / 2f - 54f * sc
        val offY = size.height / 2f - 75f * sc
        fun p(x: Float, y: Float) = Offset(x * sc + offX, y * sc + offY)

        val accent = when (state) {
            PipelineState.RECORDING -> Color(0xFF0A84FF)
            PipelineState.INJECTING, PipelineState.DONE -> Color(0xFF2Fb170)
            PipelineState.ERROR -> Color(0xFFB3261E)
            else -> INK
        }

        // Listening ear — a small filled leaf off the left side while recording.
        if (state == PipelineState.RECORDING) {
            val ear = PathParser().parsePathString(
                "M 36 52 C 22 50, 8 58, 6 72 C 6 84, 18 90, 28 84 C 34 78, 36 66, 36 52 Z"
            ).toPath()
            ear.asAndroidPath().transform(androidMatrix(sc, offX, offY))
            drawPath(ear, color = Color(0xFF8A8A8E), style = Stroke(width = 2f * sc))
        }

        val body = PathParser().parsePathString(BODY_PATH).toPath()
        body.asAndroidPath().transform(androidMatrix(sc, offX, offY))
        // White halo FIRST (the desktop's stroke-width-11 white outline) so the
        // black paperclip reads cleanly on dark backgrounds — this was the
        // "faint white glow around the edges" nuance. A soft wide low-alpha
        // pass approximates the desktop's white drop-shadow glow.
        drawPath(body, color = Color.White.copy(alpha = 0.45f), style = Stroke(width = 15f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(body, color = Color.White, style = Stroke(width = 11f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Black body on top.
        drawPath(body, color = INK, style = Stroke(width = 6f * sc, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Brows — white halo then ink, same as the body.
        drawBrow(p(36f, 36f), p(42f, 32f), p(48f, 36f), sc, Color.White, 7.5f)
        drawBrow(p(60f, 36f), p(66f, 32f), p(72f, 36f), sc, Color.White, 7.5f)
        drawBrow(p(36f, 36f), p(42f, 32f), p(48f, 36f), sc, INK, 3.5f)
        drawBrow(p(60f, 36f), p(66f, 32f), p(72f, 36f), sc, INK, 3.5f)

        // Eyes (white sclera + ink ring + accent pupil).
        for (cx in listOf(44f, 66f)) {
            drawCircle(Color.White, 8.5f * sc, p(cx, 51f))
            drawCircle(INK, 8.5f * sc, p(cx, 51f), style = Stroke(width = 2f * sc))
            drawCircle(accent, 4f * sc, p(cx, 51f))
        }
    }
}

private fun androidMatrix(sc: Float, offX: Float, offY: Float): android.graphics.Matrix =
    android.graphics.Matrix().apply { setScale(sc, sc); postTranslate(offX, offY) }

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBrow(
    a: Offset, c: Offset, b: Offset, sc: Float, color: Color, width: Float,
) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(a.x, a.y)
        quadraticBezierTo(c.x, c.y, b.x, b.y)
    }
    drawPath(path, color, style = Stroke(width = width * sc, cap = StrokeCap.Round))
}
