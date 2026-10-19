package com.wisprfox.android.provider.elevenlabs

import com.wisprfox.android.history.SpeakerTurn
import com.wisprfox.android.history.SpeakerTurnsCodec
import com.wisprfox.android.provider.AudioFiles
import com.wisprfox.android.provider.ProgressFileBody
import com.wisprfox.android.provider.SttError
import com.wisprfox.android.provider.SttProgress
import com.wisprfox.android.provider.SttProgressEvent
import com.wisprfox.android.provider.SttProvider
import com.wisprfox.android.provider.Transcript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** ElevenLabs Scribe speech-to-text client. With `diarize`, word-level speaker ids become speaker turns. */
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
        val words: List<ElevenLabsWord> = emptyList(),
    )

    override suspend fun transcribe(wavPath: String, hintLang: String?, diarize: Boolean, progress: SttProgress?): Transcript =
        withContext(Dispatchers.IO) {
            val src = File(wavPath)
            if (!src.exists()) throw SttError.Io("file not found: $wavPath")

            // One request for the whole file: diarization must see it all.
            val fileBody = ProgressFileBody(src, AudioFiles.mediaType(src)) { sent, total ->
                progress?.onEvent(
                    if (sent >= total) SttProgressEvent.Processing(1, 1)
                    else SttProgressEvent.Uploading(1, 1, sent, total),
                )
            }
            val form = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model_id", model)
                .addFormDataPart("file", src.name, fileBody)
                .apply {
                    if (hintLang != null) addFormDataPart("language_code", hintLang)
                    if (diarize) {
                        addFormDataPart("diarize", "true")
                        addFormDataPart("timestamps_granularity", "word")
                    }
                }
                .build()

            val request = Request.Builder()
                .url(ENDPOINT)
                .header("xi-api-key", apiKey)
                .post(form)
                .build()
            val call = client.newBuilder()
                .callTimeout(AudioFiles.callTimeoutSeconds(src.length()), TimeUnit.SECONDS)
                .build()

            try {
                call.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw SttError.Http(resp.code, bodyStr)
                    val parsed = try {
                        json.decodeFromString(ElevenLabsResponse.serializer(), bodyStr)
                    } catch (e: Exception) {
                        throw SttError.Decode(e.message ?: "json parse failed")
                    }
                    val turns = if (diarize) ElevenLabsTurns.fromWords(parsed.words) else emptyList()
                    Transcript(
                        text = SpeakerTurnsCodec.plainTranscript(turns).ifBlank { parsed.text },
                        language = parsed.languageCode ?: hintLang,
                        durationSeconds = parsed.words.lastOrNull { it.end != null }?.end,
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
        private const val ENDPOINT = "https://api.elevenlabs.io/v1/speech-to-text"
    }
}

/** One entry of Scribe's `words` array (`type` is "word", "spacing" or "audio_event"). */
@Serializable
data class ElevenLabsWord(
    val text: String = "",
    val type: String = "word",
    val start: Double? = null,
    val end: Double? = null,
    @SerialName("speaker_id") val speakerId: String? = null,
)

/** Groups Scribe's word stream into speaker turns. Pure, unit-tested. */
object ElevenLabsTurns {
    fun fromWords(words: List<ElevenLabsWord>): List<SpeakerTurn> {
        // "speaker_0", "speaker_1"… are numbered in order of first appearance,
        // so speaker chips read Speaker 1, Speaker 2 like Deepgram's.
        val labels = LinkedHashMap<String, String>()
        val turns = mutableListOf<SpeakerTurn>()
        var speaker: String? = null
        val text = StringBuilder()
        var start: Double? = null
        var end: Double? = null

        fun flush() {
            val s = speaker
            val t = text.toString().trim()
            if (s != null && t.isNotEmpty()) turns += SpeakerTurn(s, t, start, end)
            text.setLength(0)
            start = null
            end = null
        }

        for (word in words) {
            when (word.type) {
                "word" -> {
                    val id = word.speakerId ?: "speaker_0"
                    val label = labels.getOrPut(id) { "Speaker ${labels.size + 1}" }
                    if (label != speaker) {
                        flush()
                        speaker = label
                    }
                    if (start == null) start = word.start
                    word.end?.let { end = it }
                    text.append(word.text)
                }
                "spacing" -> if (text.isNotEmpty()) text.append(word.text.ifEmpty { " " })
                else -> Unit // audio events ("(laughter)") are not speech
            }
        }
        flush()
        return turns
    }
}
