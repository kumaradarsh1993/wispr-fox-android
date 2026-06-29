package com.wisprfox.android.provider.elevenlabs

import com.wisprfox.android.provider.SttError
import com.wisprfox.android.provider.SttProvider
import com.wisprfox.android.provider.Transcript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
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

/** ElevenLabs Scribe speech-to-text client. */
class ElevenLabsSttClient(
    baseClient: OkHttpClient,
    private val apiKey: String,
    private val model: String,
) : SttProvider {

    override val name: String = "elevenlabs"

    private val client = baseClient.newBuilder()
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ElevenLabsResponse(
        val text: String = "",
        @SerialName("language_code") val languageCode: String? = null,
    )

    override suspend fun transcribe(wavPath: String, hintLang: String?): Transcript =
        withContext(Dispatchers.IO) {
            val src = File(wavPath)
            if (!src.exists()) throw SttError.Io("file not found: $wavPath")

            val fileBody = src.asRequestBody("audio/wav".toMediaType())
            val form = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model_id", model)
                .addFormDataPart("file", src.name, fileBody)
                .apply {
                    if (hintLang != null) addFormDataPart("language_code", hintLang)
                }
                .build()

            val request = Request.Builder()
                .url(ENDPOINT)
                .header("xi-api-key", apiKey)
                .post(form)
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw SttError.Http(resp.code, bodyStr)
                    val parsed = try {
                        json.decodeFromString(ElevenLabsResponse.serializer(), bodyStr)
                    } catch (e: Exception) {
                        throw SttError.Decode(e.message ?: "json parse failed")
                    }
                    Transcript(parsed.text, parsed.languageCode ?: hintLang, null)
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
        private const val ENDPOINT = "https://api.elevenlabs.io/v1/speech-to-text"
    }
}
