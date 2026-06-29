package com.wisprfox.android.provider.deepgram

import com.wisprfox.android.provider.SttError
import com.wisprfox.android.provider.SttProvider
import com.wisprfox.android.provider.Transcript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** Deepgram pre-recorded audio client. */
class DeepgramSttClient(
    baseClient: OkHttpClient,
    private val apiKey: String,
    private val model: String,
) : SttProvider {

    override val name: String = "deepgram"

    private val client = baseClient.newBuilder()
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class DeepgramResponse(
        val metadata: Metadata? = null,
        val results: Results = Results(),
    )
    @Serializable private data class Metadata(val duration: Double? = null)
    @Serializable private data class Results(val channels: List<Channel> = emptyList())
    @Serializable private data class Channel(
        val alternatives: List<Alternative> = emptyList(),
        @SerialName("detected_language") val detectedLanguage: String? = null,
    )
    @Serializable private data class Alternative(val transcript: String = "")

    override suspend fun transcribe(wavPath: String, hintLang: String?): Transcript =
        withContext(Dispatchers.IO) {
            val src = File(wavPath)
            if (!src.exists()) throw SttError.Io("file not found: $wavPath")

            val url = buildString {
                append(ENDPOINT)
                append("?model=")
                append(model)
                append("&smart_format=true")
                if (hintLang != null) {
                    append("&language=")
                    append(hintLang)
                }
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Token $apiKey")
                .post(src.asRequestBody("audio/wav".toMediaType()))
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw SttError.Http(resp.code, bodyStr)
                    val parsed = try {
                        json.decodeFromString(DeepgramResponse.serializer(), bodyStr)
                    } catch (e: Exception) {
                        throw SttError.Decode(e.message ?: "json parse failed")
                    }
                    val channel = parsed.results.channels.firstOrNull()
                    val text = channel?.alternatives?.firstOrNull()?.transcript.orEmpty()
                    Transcript(
                        text = text,
                        language = channel?.detectedLanguage ?: hintLang,
                        durationSeconds = parsed.metadata?.duration,
                    )
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
        private const val ENDPOINT = "https://api.deepgram.com/v1/listen"
    }
}
