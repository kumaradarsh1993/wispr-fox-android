package com.wisprfox.android.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/** Where decoded mono 16-bit PCM goes. The output rate is known only once the decoder reports it. */
interface PcmSink {
    fun start(sampleRate: Int)
    fun write(samples: ShortArray, offset: Int, length: Int)
    /** Finish the file. Safe to call when [start] never ran. */
    fun close()
}

/** Canonical mono 16-bit WAV, for the merge path and anything that needs raw PCM. */
class WavSink(private val outFile: File) : PcmSink {
    private var writer: WavWriter? = null
    override fun start(sampleRate: Int) {
        outFile.parentFile?.mkdirs()
        if (outFile.exists()) outFile.delete()
        writer = WavWriter(outFile, sampleRate, channels = 1, bitsPerSample = 16)
    }
    override fun write(samples: ShortArray, offset: Int, length: Int) {
        requireNotNull(writer).write(samples, offset, length)
    }
    override fun close() {
        writer?.close()
        writer = null
    }
}

/**
 * Mono AAC-LC in an MPEG-4 container (.m4a) via MediaCodec + MediaMuxer.
 * At 16 kHz / 32 kbps, 30 minutes is about 7 MB instead of a 57 MB WAV, and
 * every STT provider the app talks to accepts it.
 */
class AacM4aSink(
    private val outFile: File,
    private val bitRate: Int = BIT_RATE,
) : PcmSink {
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var track = -1
    private var muxerStarted = false
    private var sampleRate = 16_000
    private var framesQueued = 0L
    private val info = MediaCodec.BufferInfo()

    override fun start(sampleRate: Int) {
        this.sampleRate = sampleRate
        outFile.parentFile?.mkdirs()
        if (outFile.exists()) outFile.delete()
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_BYTES)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }

    override fun write(samples: ShortArray, offset: Int, length: Int) {
        val enc = requireNotNull(codec)
        var pos = offset
        val end = offset + length
        while (pos < end) {
            val inIndex = enc.dequeueInputBuffer(TIMEOUT_US)
            if (inIndex >= 0) {
                val buf = requireNotNull(enc.getInputBuffer(inIndex))
                buf.clear()
                buf.order(ByteOrder.nativeOrder())
                val count = minOf(end - pos, buf.remaining() / 2)
                val shorts = buf.asShortBuffer()
                shorts.put(samples, pos, count)
                enc.queueInputBuffer(inIndex, 0, count * 2, presentationUs(), 0)
                framesQueued += count
                pos += count
            }
            drain(endOfStream = false)
        }
    }

    override fun close() {
        val enc = codec ?: return
        try {
            var queued = false
            while (!queued) {
                val inIndex = enc.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    enc.queueInputBuffer(inIndex, 0, 0, presentationUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    queued = true
                } else {
                    drain(endOfStream = false)
                }
            }
            drain(endOfStream = true)
        } finally {
            runCatching { enc.stop() }
            runCatching { enc.release() }
            codec = null
            muxer?.let { m ->
                if (muxerStarted) runCatching { m.stop() }
                runCatching { m.release() }
            }
            muxer = null
        }
    }

    private fun presentationUs(): Long = framesQueued * 1_000_000L / sampleRate

    private fun drain(endOfStream: Boolean) {
        val enc = requireNotNull(codec)
        val mux = requireNotNull(muxer)
        var idle = 0
        while (true) {
            val outIndex = enc.dequeueOutputBuffer(info, if (endOfStream) TIMEOUT_US else 0L)
            when {
                outIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // At end of stream, give the encoder ~5 s to flush, then stop waiting.
                    if (!endOfStream || ++idle > MAX_IDLE_POLLS) return
                }
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = mux.addTrack(enc.outputFormat)
                    mux.start()
                    muxerStarted = true
                }
                outIndex >= 0 -> {
                    val out = enc.getOutputBuffer(outIndex)
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (out != null && info.size > 0 && !isConfig && muxerStarted) {
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        mux.writeSampleData(track, out, info)
                    }
                    enc.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    companion object {
        const val BIT_RATE = 32_000
        private const val INPUT_BYTES = 16 * 1024
        private const val TIMEOUT_US = 10_000L
        private const val MAX_IDLE_POLLS = 500
    }
}
