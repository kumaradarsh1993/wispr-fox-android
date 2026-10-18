package com.wisprfox.android.audio

/**
 * Streaming linear-decimation resampler for mono 16-bit PCM.
 *
 * Imported audio (shared from Samsung Voice Recorder, call recorders, iPhone
 * Voice Memos, …) commonly arrives at 44.1 or 48 kHz. Whisper and every STT
 * provider we call downsample to 16 kHz internally anyway, so sending the
 * original rate wastes ~2-3x the bytes for zero accuracy gain — and that
 * directly multiplies the number of 20 MB chunks a long recording is split
 * into, hence the number of billed Groq requests. A 30-minute 48 kHz mono clip
 * is ~172 MB (≈9 chunks); the same clip at 16 kHz is ~57 MB (≈3 chunks).
 *
 * This converts an arbitrary source rate to a target rate by the same
 * fractional-accumulator decimation the merge path
 * ([com.wisprfox.android.queue.ImportWorker.appendPcm]) already uses in
 * production, extracted here so it can be unit-tested and shared. It is
 * **stateful across buffers**: a streaming decoder hands it one output buffer
 * at a time and the fractional remainder carries over, so there is no seam or
 * drift at buffer boundaries over a multi-hour file.
 *
 * It is deliberately a nearest/sample-and-hold decimator, not a windowed-sinc
 * filter: speech STT does not benefit from the extra fidelity, and the cheap
 * integer math keeps a long decode off the GC and fast on a phone.
 */
class AudioResampler(private val sourceRate: Int, private val targetRate: Int) {

    private var accumulator = 0L

    /** True when no work is needed (equal rates, or a nonsensical rate). */
    val isIdentity: Boolean =
        sourceRate <= 0 || targetRate <= 0 || sourceRate == targetRate

    /**
     * Resample the first [length] samples of [input], returning a freshly sized
     * array of output samples (may be empty for a short input buffer when
     * downsampling). When [isIdentity], returns a copy of the first [length]
     * samples unchanged.
     */
    fun resample(input: ShortArray, length: Int): ShortArray {
        if (length <= 0) return ShortArray(0)
        if (isIdentity) return input.copyOf(length)

        // Upper bound on outputs this call: each input sample emits at most
        // ceil(targetRate/sourceRate) outputs; +1 covers a carried remainder.
        val maxOut = (length.toLong() * targetRate / sourceRate + 2L).toInt()
        val out = ShortArray(maxOut)
        var outSize = 0
        for (i in 0 until length) {
            val sample = input[i]
            accumulator += targetRate.toLong()
            while (accumulator >= sourceRate) {
                out[outSize++] = sample
                accumulator -= sourceRate.toLong()
            }
        }
        return if (outSize == out.size) out else out.copyOf(outSize)
    }

    companion object {
        /** Whisper's native rate; the STT upload target for imported audio. */
        const val TARGET_RATE_HZ = 16_000

        /**
         * The rate [AudioImporter] should emit for a source at [sourceRate]:
         * [TARGET_RATE_HZ] when the source is higher, otherwise the source
         * rate unchanged (never upsample — it only inflates bytes).
         */
        fun targetRateFor(sourceRate: Int): Int =
            if (sourceRate > TARGET_RATE_HZ) TARGET_RATE_HZ else sourceRate
    }
}
