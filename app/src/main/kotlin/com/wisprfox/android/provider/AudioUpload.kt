package com.wisprfox.android.provider

import com.wisprfox.android.audio.AudioImporter
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import java.io.File

/** What an STT client reports while it works, so the UI can say more than "Transcribing…". */
sealed class SttProgressEvent {
    /** Bytes of [part] (1-based, of [parts]) sent so far. */
    data class Uploading(val part: Int, val parts: Int, val sent: Long, val total: Long) : SttProgressEvent()

    /** [part] is fully uploaded; the provider is working on it. */
    data class Processing(val part: Int, val parts: Int) : SttProgressEvent()
}

fun interface SttProgress {
    fun onEvent(event: SttProgressEvent)
}

/** Content types and size helpers shared by the STT clients. */
object AudioFiles {
    fun isWav(file: File): Boolean = file.name.endsWith(".wav", ignoreCase = true)

    /** Imports are AAC in an MPEG-4 container (.m4a); live mic recordings are WAV. */
    fun mediaType(file: File): MediaType = when (file.extension.lowercase()) {
        "wav" -> "audio/wav"
        "m4a", "mp4", "aac" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "ogg", "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }.toMediaType()

    /**
     * Whole-file upload + provider processing ceiling. A flat 120 s is too tight
     * for an hour-long meeting on a slow uplink, so it grows with the file
     * (~40 KB/s worst-case uplink), capped at 15 minutes.
     */
    fun callTimeoutSeconds(bytes: Long): Long = (120L + bytes / 40_000L).coerceAtMost(900L)
}

/** A file request body that reports upload progress. */
class ProgressFileBody(
    private val file: File,
    private val type: MediaType,
    private val onBytes: (sent: Long, total: Long) -> Unit,
) : RequestBody() {
    override fun contentType(): MediaType = type
    override fun contentLength(): Long = file.length()
    override fun writeTo(sink: BufferedSink) {
        val total = contentLength()
        var sent = 0L
        file.source().use { source ->
            while (true) {
                val read = source.read(sink.buffer, SEGMENT)
                if (read == -1L) break
                sent += read
                sink.flush()
                onBytes(sent, total)
            }
        }
    }

    private companion object {
        const val SEGMENT = 64L * 1024
    }
}

/**
 * Splits audio for providers with a per-request cap (Groq / OpenAI Whisper,
 * 25 MB). Files under [targetBytes] go up whole, whatever their format. A larger
 * WAV is split by [WavChunker] at quiet points; a larger compressed file (an
 * .m4a over ~20 MB is roughly 80+ minutes at 32 kbps) is first decoded to a
 * temporary WAV next to it so the same quiet-point splitter can cut it.
 */
class WhisperParts private constructor(
    val files: List<File>,
    private val splitSource: File,
    private val tempWav: File?,
) {
    fun cleanup() {
        WavChunker.cleanup(files, splitSource)
        tempWav?.let { runCatching { it.delete() } }
    }

    companion object {
        fun prepare(src: File, targetBytes: Long = WavChunker.TARGET_CHUNK_BYTES): WhisperParts {
            if (src.length() <= targetBytes) return WhisperParts(listOf(src), src, null)
            val wav = if (AudioFiles.isWav(src)) src else {
                val temp = File(src.parentFile, "${src.nameWithoutExtension}.split-tmp.wav")
                try {
                    AudioImporter.decodeFileToWav(src, temp)
                } catch (e: Exception) {
                    runCatching { temp.delete() }
                    throw SttError.Io("couldn't prepare a long recording for splitting: ${e.message}")
                }
                temp
            }
            val chunks = WavChunker.splitIfNeeded(wav, targetBytes)
            return WhisperParts(chunks, wav, wav.takeIf { it != src })
        }
    }
}
