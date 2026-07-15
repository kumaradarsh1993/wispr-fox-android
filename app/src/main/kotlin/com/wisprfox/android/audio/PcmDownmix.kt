package com.wisprfox.android.audio

/**
 * Pure PCM helpers used by [AudioImporter] to normalise decoded audio into the
 * mono 16-bit shape the transcription pipeline expects. Kept side-effect-free so
 * they're unit-testable without a device (see PcmDownmixTest).
 *
 * We downmix to mono because every STT provider we call transcribes mono and a
 * stereo file would otherwise upload at twice the size on the flaky mobile
 * networks this app targets. We keep the source sample rate: [WavChunker] reads
 * the rate from the header and chunks generically, and Whisper/Nova/Scribe all
 * resample internally, so there's no need to risk a hand-rolled resampler.
 */
object PcmDownmix {

    /**
     * Average [channels] interleaved 16-bit samples down to a single mono track.
     * Returns [interleaved] unchanged when it's already mono. Any trailing
     * partial frame (shouldn't happen with well-formed PCM) is ignored.
     */
    fun downmixToMono(interleaved: ShortArray, channels: Int): ShortArray {
        if (channels <= 1) return interleaved
        val frames = interleaved.size / channels
        val out = ShortArray(frames)
        for (f in 0 until frames) {
            val base = f * channels
            var sum = 0
            for (c in 0 until channels) sum += interleaved[base + c].toInt()
            out[f] = (sum / channels).toShort()
        }
        return out
    }

    /**
     * Convert interleaved 32-bit float PCM (range roughly [-1, 1], the output of
     * some MediaCodec decoders) to interleaved 16-bit PCM with clamping. Channel
     * layout is preserved — call [downmixToMono] afterwards.
     */
    fun floatsToShorts(floats: FloatArray, count: Int): ShortArray {
        val out = ShortArray(count)
        for (i in 0 until count) out[i] = floatToShort(floats[i])
        return out
    }

    /** Scale a normalised float sample to a clamped signed 16-bit value. */
    fun floatToShort(v: Float): Short {
        val scaled = Math.round(v * 32767f)
        return when {
            scaled > Short.MAX_VALUE -> Short.MAX_VALUE
            scaled < Short.MIN_VALUE -> Short.MIN_VALUE
            else -> scaled.toShort()
        }
    }
}
