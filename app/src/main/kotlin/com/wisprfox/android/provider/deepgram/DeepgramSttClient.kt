package com.wisprfox.android.provider.deepgram

import com.wisprfox.android.provider.AudioFiles
import com.wisprfox.android.provider.ProgressFileBody
import com.wisprfox.android.provider.SttError
import com.wisprfox.android.provider.SttProgress
import com.wisprfox.android.provider.SttProgressEvent
import com.wisprfox.android.provider.SttProvider
import com.wisprfox.android.provider.Transcript
import com.wisprfox.android.history.SpeakerTurn
import com.wisprfox.android.history.SpeakerTurnsCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
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
    @Serializable private data class Utterance(
        val speaker: Int? = null,
        val transcript: String = "",
        val start: Double? = null,
        val end: Double? = null,
    )
    @Serializable private data class Channel(
        val alternatives: List<Alternative> = emptyList(),
        @SerialName("detected_language") val detectedLanguage: String? = null,
    )
    @Serializable private data class Alternative(val transcript: String = "")

    override suspend fun transcribe(wavPath: String, hintLang: String?, diarize: Boolean, progress: SttProgress?): Transcript =
        withContext(Dispatchers.IO) {
            val src = File(wavPath)
            if (!src.exists()) throw SttError.Io("file not found: $wavPath")

            val url = buildString {
                append(ENDPOINT)
                append("?model=")
                append(model)
                append("&smart_format=true")
                if (diarize) append("&diarize=true&utterances=true")
                if (hintLang != null) {
                    append("&language=")
                    append(hintLang)
                }
            }
            // Whole file in one request, never split: diarization needs the
            // full recording so "Speaker 1" means the same person throughout.
            val body = ProgressFileBody(src, AudioFiles.mediaType(src)) { sent, total ->
                progress?.onEvent(
                    if (sent >= total) SttProgressEvent.Processing(1, 1)
                    else SttProgressEvent.Uploading(1, 1, sent, total),
                )
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Token $apiKey")
                .post(body)
                .build()
            val call = client.newBuilder()
                .callTimeout(AudioFiles.callTimeoutSeconds(src.length()), TimeUnit.SECONDS)
                .build()

            try {
                call.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw SttError.Http(resp.code, bodyStr)
                    val parsed = try {
                        json.decodeFromString(DeepgramResponse.serializer(), bodyStr)
                    } catch (e: Exception) {
                        throw SttError.Decode(e.message ?: "json parse failed")
                    }
                    val channel = parsed.results.channels.firstOrNull()
                    val text = channel?.alternatives?.firstOrNull()?.transcript.orEmpty()
                    val utterances = if (diarize) {
                        runCatching {
                            json.parseToJsonElement(bodyStr).jsonObject["results"]
                                ?.jsonObject?.get("utterances")
                                ?.let { json.decodeFromJsonElement<List<Utterance>>(it) }
                        }.getOrNull().orEmpty()
                    } else emptyList()
                    val turns = utterances.filter { it.transcript.isNotBlank() }.map {
                        SpeakerTurn("Speaker ${(it.speaker ?: 0) + 1}", it.transcript, it.start, it.end)
                    }
                    val diarizedText = turns.joinToString("\n\n") { "${it.speaker}: ${it.text}" }
                    Transcript(
                        text = diarizedText.ifBlank { text },
                        language = channel?.detectedLanguage ?: hintLang,
                        durationSeconds = parsed.metadata?.duration,
                        speakerTurnsJson = turns.takeIf { it.isNotEmpty() }?.let(SpeakerTurnsCodec::encode),
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
