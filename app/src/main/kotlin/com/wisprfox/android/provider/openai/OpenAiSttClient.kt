package com.wisprfox.android.provider.openai

import com.wisprfox.android.provider.SttError
import com.wisprfox.android.provider.SttProvider
import com.wisprfox.android.provider.Transcript
import com.wisprfox.android.provider.WavChunker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** OpenAI audio transcriptions client. */
class OpenAiSttClient(
    baseClient: OkHttpClient,
    private val apiKey: String,
    private val model: String,
) : SttProvider {

    override val name: String = "openai"

    private val client = baseClient.newBuilder()
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class OpenAiResponse(val text: String = "")

    override suspend fun transcribe(wavPath: String, hintLang: String?): Transcript =
        withContext(Dispatchers.IO) {
            val src = File(wavPath)
            if (!src.exists()) throw SttError.Io("file not found: $wavPath")

            if (src.length() <= WavChunker.TARGET_CHUNK_BYTES) {
                return@withContext transcribeOne(src, hintLang)
            }

            val chunks = WavChunker.splitIfNeeded(src, WavChunker.TARGET_CHUNK_BYTES)
            try {
                for (c in chunks) {
                    if (c.length() > MAX_BYTES) throw SttError.FileTooLarge(c.length(), MAX_BYTES)
                }
                val parts = chunks.map { transcribeOne(it, hintLang).text.trim() }
                Transcript(parts.joinToString(" "), hintLang, null)
            } finally {
                WavChunker.cleanup(chunks, src)
            }
        }

    private fun transcribeOne(wav: File, hintLang: String?): Transcript {
        val fileBody = wav.asRequestBody("audio/wav".toMediaType())
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("response_format", "json")
            .addFormDataPart("file", wav.name, fileBody)
            .apply {
                if (hintLang != null) addFormDataPart("language", hintLang)
            }
            .build()

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .post(multipart)
            .build()

        try {
            client.newCall(request).execute().use { resp ->
                val bodyStr = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw SttError.Http(resp.code, bodyStr)
                val parsed = try {
                    json.decodeFromString(OpenAiResponse.serializer(), bodyStr)
                } catch (e: Exception) {
                    throw SttError.Decode(e.message ?: "json parse failed")
                }
                return Transcript(parsed.text, hintLang, null)
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
        private const val ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
        private const val MAX_BYTES = 25L * 1024 * 1024
    }
}
