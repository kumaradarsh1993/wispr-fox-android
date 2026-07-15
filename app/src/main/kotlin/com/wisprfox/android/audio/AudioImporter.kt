package com.wisprfox.android.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.File
import java.nio.ByteOrder
import java.nio.ShortBuffer

/**
 * Decodes an imported audio file (any container/codec the platform can decode —
 * AAC/M4A, MP3, AMR-NB/WB, 3GP, FLAC, Ogg/Opus, WAV/PCM — covering Samsung Voice
 * Recorder, call recorders, and iPhone Voice Memos) into the canonical mono
 * 16-bit PCM WAV the rest of the pipeline already understands ([WavChunker],
 * every STT client). We keep the source sample rate and only downmix to mono.
 *
 * Streams frame-by-frame straight to [WavWriter] so a multi-hour call never has
 * to fit in memory. Runs off the main thread (called from [com.wisprfox.android
 * .queue.ImportWorker]).
 */
object AudioImporter {

    /** Thrown when the source has no decodable audio track or decoding fails. */
    class ImportException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class Result(val durationMs: Long, val sampleRate: Int)

    private const val TAG = "wisprfox/AudioImporter"
    private const val TIMEOUT_US = 10_000L
    // Mirrors AudioFormat.ENCODING_* without depending on the framework constants
    // so the decode logic stays unit-reasoned; values are the platform's.
    private const val ENCODING_PCM_16BIT = 2
    private const val ENCODING_PCM_FLOAT = 4

    /**
     * Decode [uri] into [outFile] as mono 16-bit WAV. Overwrites [outFile].
     * @throws ImportException on any unrecoverable decode error.
     */
    fun decodeToWav(context: Context, uri: Uri, outFile: File): Result {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            throw ImportException("Couldn't open that file — it may be missing or an unsupported type.", e)
        }

        val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
            extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: run {
            extractor.release()
            throw ImportException("No audio track found in that file.")
        }

        val inputFormat = extractor.getTrackFormat(trackIndex)
        extractor.selectTrack(trackIndex)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME)
            ?: throw ImportException("Unknown audio format.").also { extractor.release() }
        val declaredDurationUs =
            if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION) else 0L

        val codec = try {
            MediaCodec.createDecoderByType(mime).apply { configure(inputFormat, null, null, 0) }
        } catch (e: Exception) {
            extractor.release()
            throw ImportException("This audio format isn't supported on this device.", e)
        }

        outFile.parentFile?.mkdirs()
        var writer: WavWriter? = null
        var outSampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE))
            inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 16_000
        var outChannels = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
            inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
        var pcmEncoding = ENCODING_PCM_16BIT
        var framesWritten = 0L

        try {
            codec.start()
            val info = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val inBuf = codec.getInputBuffer(inIndex)
                        val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = codec.outputFormat
                        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) outSampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) outChannels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (f.containsKey(KEY_PCM_ENCODING)) pcmEncoding = f.getInteger(KEY_PCM_ENCODING)
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER, MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> {
                        // No output ready / deprecated signal — keep pumping.
                    }
                    else -> {
                        if (outIndex >= 0) {
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawOutputEos = true
                            if (info.size > 0) {
                                if (writer == null) {
                                    writer = WavWriter(outFile, outSampleRate, channels = 1, bitsPerSample = 16)
                                }
                                val mono = readMono(codec, outIndex, info, outChannels, pcmEncoding)
                                writer.write(mono, 0, mono.size)
                                framesWritten += mono.size
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                        }
                    }
                }
            }
        } catch (e: ImportException) {
            throw e
        } catch (e: Exception) {
            throw ImportException("Couldn't decode that audio file.", e)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            runCatching { extractor.release() }
            runCatching { writer?.close() }
        }

        if (writer == null || framesWritten == 0L) {
            runCatching { outFile.delete() }
            throw ImportException("That file didn't contain any decodable audio.")
        }

        val durationMs = if (declaredDurationUs > 0) declaredDurationUs / 1000
        else framesWritten * 1000 / outSampleRate.coerceAtLeast(1)
        Log.i(TAG, "decoded import → ${outFile.name} rate=$outSampleRate frames=$framesWritten (~${durationMs}ms)")
        return Result(durationMs = durationMs, sampleRate = outSampleRate)
    }

    /** Read one decoded output buffer as a mono 16-bit ShortArray. */
    private fun readMono(
        codec: MediaCodec,
        outIndex: Int,
        info: MediaCodec.BufferInfo,
        channels: Int,
        pcmEncoding: Int,
    ): ShortArray {
        val buf = codec.getOutputBuffer(outIndex)
            ?: throw ImportException("Decoder returned an empty buffer.")
        buf.position(info.offset)
        buf.limit(info.offset + info.size)

        val interleaved: ShortArray = if (pcmEncoding == ENCODING_PCM_FLOAT) {
            val fb = buf.order(ByteOrder.nativeOrder()).asFloatBuffer()
            val floats = FloatArray(fb.remaining())
            fb.get(floats)
            PcmDownmix.floatsToShorts(floats, floats.size)
        } else {
            val sb: ShortBuffer = buf.order(ByteOrder.nativeOrder()).asShortBuffer()
            val shorts = ShortArray(sb.remaining())
            sb.get(shorts)
            shorts
        }
        return PcmDownmix.downmixToMono(interleaved, channels)
    }

    // MediaFormat.KEY_PCM_ENCODING is API 24+; the app minSdk is 31 so it's
    // always present, but we read it defensively via the string key.
    private const val KEY_PCM_ENCODING = "pcm-encoding"
}
