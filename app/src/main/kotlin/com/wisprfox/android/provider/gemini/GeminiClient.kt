package com.wisprfox.android.provider.gemini

import com.wisprfox.android.provider.LlmError
import com.wisprfox.android.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * Google Gemini client. Ported from `wispr-fox/src-tauri/src/llm/gemini.rs`.
 *
 * Different shape from Groq/OpenAI: `generateContent` endpoint, a `contents`
 * array of `parts`, a separate `systemInstruction`, and auth via the
 * `x-goog-api-key` header (NOT a Bearer token). Default model
 * `gemini-2.5-flash` (most generous free tier as of May 2026). Checks
 * `promptFeedback.blockReason` before extracting the candidate text.
 */
class GeminiClient(
    baseClient: OkHttpClient,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : LlmProvider {

    override val name: String = "gemini"

    private val client: OkHttpClient = baseClient.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }

    @Serializable private data class Part(val text: String)
    @Serializable private data class Content(val role: String? = null, val parts: List<Part>)
    @Serializable private data class GenerationConfig(
        val temperature: Float,
        val maxOutputTokens: Int,
    )
    @Serializable private data class GenerateContentRequest(
        val contents: List<Content>,
        val systemInstruction: Content? = null,
        val generationConfig: GenerationConfig,
    )

    @Serializable private data class RespPart(val text: String? = null)
    @Serializable private data class RespContent(val parts: List<RespPart>? = null)
    @Serializable private data class Candidate(
        val content: RespContent? = null,
        val finishReason: String? = null,
    )
    @Serializable private data class PromptFeedback(val blockReason: String? = null)
    @Serializable private data class GenerateContentResponse(
        val candidates: List<Candidate>? = null,
        val promptFeedback: PromptFeedback? = null,
    )

    override suspend fun complete(system: String, user: String, temperature: Float): String =
        withContext(Dispatchers.IO) {
            val payload = GenerateContentRequest(
                contents = listOf(Content(role = "user", parts = listOf(Part(user)))),
                systemInstruction = if (system.isEmpty()) null
                    else Content(role = null, parts = listOf(Part(system))),
                generationConfig = GenerationConfig(temperature, 2048),
            )
            val body = json.encodeToString(GenerateContentRequest.serializer(), payload)
                .toRequestBody("application/json".toMediaType())
            val url = "$ENDPOINT_BASE/$model:generateContent"
            val request = Request.Builder()
                .url(url)
                .header("x-goog-api-key", apiKey)
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw LlmError.Http(resp.code, bodyStr)
                    val parsed = try {
                        json.decodeFromString(GenerateContentResponse.serializer(), bodyStr)
                    } catch (e: Exception) {
                        throw LlmError.Decode(e.message ?: "json parse failed")
                    }
                    parsed.promptFeedback?.blockReason?.let {
                        throw LlmError.Decode("gemini blocked: $it")
                    }
                    val text = parsed.candidates
                        ?.firstOrNull()
                        ?.content
                        ?.parts
                        ?.firstOrNull()
                        ?.text
                        .orEmpty()
                    if (text.isEmpty()) throw LlmError.Decode("empty response from gemini")
                    text
                }
            } catch (e: LlmError) {
                throw e
            } catch (e: InterruptedIOException) {
                throw LlmError.Timeout
            } catch (e: Exception) {
                throw LlmError.Network(e.message ?: e.toString())
            }
        }

    companion object {
        const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
