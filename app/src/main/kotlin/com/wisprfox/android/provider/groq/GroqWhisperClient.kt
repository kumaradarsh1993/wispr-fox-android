package com.wisprfox.android.provider.groq

import com.wisprfox.android.provider.SttError
import com.wisprfox.android.provider.SttProvider
import com.wisprfox.android.provider.Transcript
import com.wisprfox.android.provider.WavChunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * Groq Whisper REST client — multipart POST to /openai/v1/audio/transcriptions.
 * Ported from `wispr-fox/src-tauri/src/stt/groq.rs`.
 *
 * Defaults: model `whisper-large-v3-turbo`, `response_format=verbose_json`,
 * and crucially **no `language` param** so Whisper auto-detects (the user
 * code-switches English ↔ Hindi). Files over ~20 MB are split via
 * [WavChunker], transcribed serially, and the texts concatenated.
 */
class GroqWhisperClient(
    baseClient: OkHttpClient,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : SttProvider {

    override val name: String = "groq"

    // Single-request Whisper is fast (~15 s for 5-min audio); the wider
    // ceiling accommodates the multi-chunk serial path. Matches the 120 s cap
    // the desktop flow applies around the STT call.
    private val client: OkHttpClient = baseClient.newBuilder()
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class GroqResponse(
        val text: String = "",
        val language: String? = null,
        val duration: Double? = null,
    )

    override suspend fun transcribe(wavPath: String, hintLang: String?): Transcript =
        withContext(Dispatchers.IO) {
            val src = File(wavPath)
            if (!src.exists()) throw SttError.Io("file not found: $wavPath")

            // Fast path: fits in one request (the common case).
            if (src.length() <= WavChunker.TARGET_CHUNK_BYTES) {
                return@withContext transcribeOne(src, hintLang)
            }

            // Slow path: split, transcribe each chunk serially, concatenate.
            val chunks = WavChunker.splitIfNeeded(src, WavChunker.TARGET_CHUNK_BYTES)
            try {
                for (c in chunks) {
                    if (c.length() > MAX_BYTES) {
                        throw SttError.FileTooLarge(c.length(), MAX_BYTES)
                    }
                }
                val parts = ArrayList<String>(chunks.size)
                var detectedLang: String? = null
                var totalDuration = 0.0
                for (chunk in chunks) {
                    val t = transcribeOne(chunk, hintLang)
                    if (detectedLang == null) detectedLang = t.language
                    t.durationSeconds?.let { totalDuration += it }
                    parts.add(t.text.trim())
                }
                Transcript(
                    text = parts.joinToString(" "),
                    language = detectedLang,
                    durationSeconds = if (totalDuration > 0.0) totalDuration else null,
                )
            } finally {
                WavChunker.cleanup(chunks, src)
            }
        }

    private fun transcribeOne(wav: File, hintLang: String?): Transcript {
        val fileBody = wav.asRequestBody("audio/wav".toMediaType())
        val multipartBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("file", wav.name, fileBody)
        if (hintLang != null) multipartBuilder.addFormDataPart("language", hintLang)

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .post(multipartBuilder.build())
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw SttError.Http(resp.code, bodyStr)
                }
                val parsed = try {
                    json.decodeFromString(GroqResponse.serializer(), bodyStr)
                } catch (e: Exception) {
                    throw SttError.Decode(e.message ?: "json parse failed")
                }
                return Transcript(parsed.text, parsed.language, parsed.duration)
            }
        } catch (e: SttError) {
            throw e
        } catch (e: InterruptedIOException) {
            throw SttError.Timeout
        } catch (e: Exception) {
            throw SttError.Network(e.message ?: e.toString())
        }
    }

    companion object {
        const val ENDPOINT = "https://api.groq.com/openai/v1/audio/transcriptions"
        const val DEFAULT_MODEL = "whisper-large-v3-turbo"
        /** Groq's documented per-request file ceiling. */
        const val MAX_BYTES = 25L * 1024 * 1024
    }
}
