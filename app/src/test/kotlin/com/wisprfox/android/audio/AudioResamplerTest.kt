package com.wisprfox.android.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards the 16 kHz downsample math used on imported audio. */
class AudioResamplerTest {

    @Test fun identityWhenRatesEqual() {
        val r = AudioResampler(16_000, 16_000)
        assertTrue(r.isIdentity)
        val input = shortArrayOf(1, 2, 3, 4)
        assertEquals(listOf<Short>(1, 2, 3, 4), r.resample(input, 4).toList())
    }

    @Test fun neverUpsamples() {
        // An 8 kHz call recording stays 8 kHz; we don't invent samples.
        assertEquals(8_000, AudioResampler.targetRateFor(8_000))
        assertEquals(16_000, AudioResampler.targetRateFor(16_000))
    }

    @Test fun fortyEightKToSixteenKIsOneThirdTargetRate() {
        assertEquals(16_000, AudioResampler.targetRateFor(48_000))
        val r = AudioResampler(48_000, 16_000)
        assertTrue(!r.isIdentity)
        // 48000 input samples -> ~16000 output samples (3:1 decimation).
        val input = ShortArray(48_000) { (it % 7).toShort() }
        val out = r.resample(input, input.size)
        assertEquals(16_000, out.size)
    }

    @Test fun fortyFourKToSixteenKRatioApproxCorrect() {
        val r = AudioResampler(44_100, 16_000)
        val input = ShortArray(44_100) { 1 }
        val out = r.resample(input, input.size)
        // 44100 * 16000 / 44100 = 16000 outputs over a full second of input.
        assertEquals(16_000, out.size)
    }

    @Test fun stateCarriesAcrossBuffersWithNoSeamDrift() {
        // Streaming the same 48k second in small buffers must total the same
        // 16000 outputs as one call — the fractional accumulator must persist.
        val streamed = AudioResampler(48_000, 16_000)
        var total = 0
        val buf = ShortArray(1000) { 1 }
        repeat(48) { total += streamed.resample(buf, buf.size).size }
        assertEquals(16_000, total)
    }

    @Test fun emptyInputYieldsEmpty() {
        val r = AudioResampler(48_000, 16_000)
        assertEquals(0, r.resample(ShortArray(0), 0).size)
    }
}
