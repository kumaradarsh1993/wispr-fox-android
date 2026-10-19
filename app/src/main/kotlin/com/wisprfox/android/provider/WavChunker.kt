package com.wisprfox.android.provider

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Splits a PCM WAV into chunks under a byte ceiling so each fits Groq's 25 MB
 * Whisper upload limit. Ported from `wispr-fox/src-tauri/src/stt/chunk.rs`.
 *
 * Our recordings are 16 kHz mono 16-bit (≈32 KB/s), so a 20 MB chunk holds
 * ~10 minutes — most clips never split. Each cut is moved back to the quietest
 * 20 ms within the 4 s before the nominal boundary, so a word is rarely split
 * across two requests. Chunk texts are joined with a single space.
 *
 * Only canonical PCM WAV (the format [com.wisprfox.android.audio.WavWriter]
 * produces) is supported: a 44-byte RIFF/WAVE/fmt /data header followed by
 * interleaved little-endian samples.
 */
object WavChunker {

    /** Target ceiling per chunk — well below Groq's 25 MB hard limit. */
    const val TARGET_CHUNK_BYTES = 20L * 1024 * 1024

    private const val HEADER_SIZE = 44

    private data class WavSpec(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val dataOffset: Long,
        val dataSize: Long,
    )

    /**
     * Returns the chunk files. If [src] is already under [targetMaxBytes], the
     * single-element list is `[src]` (no copy). Otherwise N temp files named
     * `<stem>.chunkK.wav` are written alongside [src]; caller must [cleanup].
     */
    fun splitIfNeeded(src: File, targetMaxBytes: Long): List<File> {
        if (src.length() <= targetMaxBytes) return listOf(src)

        val spec = readSpec(src) ?: return listOf(src) // not parseable → don't split
        val bytesPerFrame = (spec.bitsPerSample / 8) * spec.channels
        if (bytesPerFrame <= 0) return listOf(src)

        val totalFrames = spec.dataSize / bytesPerFrame
        // Frames whose PCM bytes fit under the target (minus header headroom).
        val framesPerChunkCap = (targetMaxBytes - HEADER_SIZE) / bytesPerFrame
        if (framesPerChunkCap <= 0) return listOf(src)

        var nChunks = ((totalFrames + framesPerChunkCap - 1) / framesPerChunkCap).toInt()
        if (nChunks < 2) nChunks = 2
        val framesPerChunk = ((totalFrames + nChunks - 1) / nChunks)

        val stem = src.nameWithoutExtension
        val parent = src.parentFile ?: File(".")
        val out = ArrayList<File>(nChunks)

        RandomAccessFile(src, "r").use { raf ->
            var frameStart = 0L
            var index = 0
            while (frameStart < totalFrames) {
                val remaining = totalFrames - frameStart
                var framesThis = if (remaining <= framesPerChunkCap) remaining else framesPerChunk
                // Cut at the quietest moment shortly before the nominal boundary,
                // not mid-word. Only ever moves the cut earlier, so every chunk
                // stays under the byte ceiling.
                if (framesThis < remaining) framesThis = quietCut(raf, spec, frameStart, framesThis, bytesPerFrame)
                val byteStart = spec.dataOffset + frameStart * bytesPerFrame
                val byteLen = (framesThis * bytesPerFrame).toInt()
                val pcm = ByteArray(byteLen)
                raf.seek(byteStart)
                raf.readFully(pcm)

                val chunkFile = File(parent, "$stem.chunk$index.wav")
                writeWav(chunkFile, spec, pcm)
                out.add(chunkFile)

                frameStart += framesThis
                index++
            }
        }
        return out
    }

    /** How far back from a nominal cut we look for a pause. */
    private const val SEARCH_SECONDS = 4

    /**
     * Returns the chunk length (frames from [frameStart]) that ends at the
     * quietest 20 ms window within the last [SEARCH_SECONDS] of [nominalFrames].
     * Falls back to [nominalFrames] for anything but 16-bit PCM.
     */
    private fun quietCut(raf: RandomAccessFile, spec: WavSpec, frameStart: Long, nominalFrames: Long, bytesPerFrame: Int): Long {
        if (spec.bitsPerSample != 16) return nominalFrames
        val searchFrames = minOf(spec.sampleRate.toLong() * SEARCH_SECONDS, nominalFrames / 4)
        val window = (spec.sampleRate / 50).coerceAtLeast(1)
        if (searchFrames < window * 2) return nominalFrames
        val searchStart = frameStart + nominalFrames - searchFrames
        val bytes = ByteArray((searchFrames * bytesPerFrame).toInt())
        raf.seek(spec.dataOffset + searchStart * bytesPerFrame)
        raf.readFully(bytes)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        // First channel is enough to find a pause.
        val mono = ShortArray(searchFrames.toInt()) { i -> bb.getShort(i * bytesPerFrame) }
        val quietStart = quietestWindowStart(mono, window)
        val cut = searchStart + quietStart + window / 2
        return (cut - frameStart).coerceIn(1L, nominalFrames)
    }

    /**
     * Start index of the [window]-sample stretch with the least energy, scanning
     * in half-window steps. Ties go to the later window so chunks stay balanced.
     */
    internal fun quietestWindowStart(samples: ShortArray, window: Int): Int {
        if (samples.size <= window) return 0
        val step = (window / 2).coerceAtLeast(1)
        var best = 0
        var bestEnergy = Long.MAX_VALUE
        var start = 0
        while (start + window <= samples.size) {
            var energy = 0L
            for (i in start until start + window) {
                val s = samples[i].toLong()
                energy += s * s
            }
            if (energy <= bestEnergy) {
                bestEnergy = energy
                best = start
            }
            start += step
        }
        return best
    }

    /** Best-effort delete of chunk temp files (never deletes the original). */
    fun cleanup(chunks: List<File>, src: File) {
        for (c in chunks) {
            if (c.absolutePath == src.absolutePath) continue
            runCatching { c.delete() }
        }
    }

    private fun readSpec(file: File): WavSpec? = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val riff = ByteArray(12)
            raf.readFully(riff)
            if (String(riff, 0, 4, Charsets.US_ASCII) != "RIFF") return null
            if (String(riff, 8, 4, Charsets.US_ASCII) != "WAVE") return null

            var sampleRate = 0
            var channels = 0
            var bits = 0
            var dataOffset = -1L
            var dataSize = 0L

            val chunkHeader = ByteArray(8)
            while (raf.filePointer + 8 <= raf.length()) {
                raf.readFully(chunkHeader)
                val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val size = ByteBuffer.wrap(chunkHeader, 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(size.toInt())
                        raf.readFully(fmt)
                        val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        bb.short // audioFormat
                        channels = bb.short.toInt()
                        sampleRate = bb.int
                        bb.int // byteRate
                        bb.short // blockAlign
                        bits = bb.short.toInt()
                    }
                    "data" -> {
                        dataOffset = raf.filePointer
                        dataSize = size
                        raf.seek(raf.filePointer + size) // skip data
                    }
                    else -> raf.seek(raf.filePointer + size + (size and 1L)) // word-align
                }
                if (dataOffset >= 0 && sampleRate > 0) break
            }
            if (dataOffset < 0 || sampleRate == 0 || bits == 0 || channels == 0) return null
            WavSpec(sampleRate, channels, bits, dataOffset, dataSize)
        }
    }.getOrNull()

    private fun writeWav(file: File, spec: WavSpec, pcm: ByteArray) {
        val byteRate = spec.sampleRate * spec.channels * spec.bitsPerSample / 8
        val blockAlign = spec.channels * spec.bitsPerSample / 8
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1) // PCM
        header.putShort(spec.channels.toShort())
        header.putInt(spec.sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(spec.bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        file.outputStream().use { os ->
            os.write(header.array())
            os.write(pcm)
        }
    }
}
