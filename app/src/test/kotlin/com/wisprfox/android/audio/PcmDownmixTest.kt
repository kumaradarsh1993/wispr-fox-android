package com.wisprfox.android.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Covers the pure PCM helpers behind audio-file import: stereo→mono downmix
 *  and float→16-bit conversion with clamping. */
class PcmDownmixTest {

    @Test fun monoInputReturnedUnchanged() {
        val mono = shortArrayOf(1, -2, 3, -4)
        // No copy for the common already-mono case.
        assertSame(mono, PcmDownmix.downmixToMono(mono, channels = 1))
    }

    @Test fun stereoAveragesChannelPairs() {
        // L/R interleaved: (10,20)->15, (-10,-20)->-15, (100,-100)->0
        val stereo = shortArrayOf(10, 20, -10, -20, 100, -100)
        assertArrayEquals(shortArrayOf(15, -15, 0), PcmDownmix.downmixToMono(stereo, channels = 2))
    }

    @Test fun downmixUsesIntSumToAvoidShortOverflow() {
        // 30000 + 30000 would overflow a Short mid-sum; Int accumulation keeps 30000.
        val stereo = shortArrayOf(30000, 30000)
        assertArrayEquals(shortArrayOf(30000), PcmDownmix.downmixToMono(stereo, channels = 2))
    }

    @Test fun trailingPartialFrameIgnored() {
        // 3 samples with 2 channels → 1 full frame, last odd sample dropped.
        val stereo = shortArrayOf(4, 6, 99)
        assertArrayEquals(shortArrayOf(5), PcmDownmix.downmixToMono(stereo, channels = 2))
    }

    @Test fun floatToShortScalesAndClamps() {
        assertEquals(0.toShort(), PcmDownmix.floatToShort(0f))
        // Symmetric scale by 32767: ±1.0 maps to ±32767 (the most-negative code
        // is intentionally unused, a standard, speech-safe choice).
        assertEquals(32767.toShort(), PcmDownmix.floatToShort(1f))
        assertEquals((-32767).toShort(), PcmDownmix.floatToShort(-1f))
        // Beyond full-scale clamps rather than wrapping.
        assertEquals(32767.toShort(), PcmDownmix.floatToShort(2f))
        assertEquals((-32768).toShort(), PcmDownmix.floatToShort(-2f))
        // Half-scale rounds to ~16384.
        assertEquals(16384.toShort(), PcmDownmix.floatToShort(0.5f))
    }

    @Test fun floatsToShortsConvertsBuffer() {
        val floats = floatArrayOf(0f, 1f, -1f, 0.5f)
        assertArrayEquals(
            shortArrayOf(0, 32767, -32767, 16384),
            PcmDownmix.floatsToShorts(floats, floats.size),
        )
    }
}
