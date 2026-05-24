package com.wisprfox.android.provider.groq

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
 * Groq chat-completions client. Ported from
 * `wispr-fox/src-tauri/src/llm/groq.rs`. Default model
 * `llama-3.3-70b-versatile`; 8-second call timeout (cleanup must not stall the
 * pipeline). Extracts `choices[0].message.content`.
 */
class GroqChatClient(
    baseClient: OkHttpClient,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : LlmProvider {

    override val name: String = "groq"

    private val client: OkHttpClient = baseClient.newBuilder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable private data class ChatMessage(val role: String, val content: String)
    @Serializable private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Float,
    )
    @Serializable private data class ChatChoiceMessage(val content: String = "")
    @Serializable private data class ChatChoice(val message: ChatChoiceMessage)
    @Serializable private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

    override suspend fun complete(system: String, user: String, temperature: Float): String =
        withContext(Dispatchers.IO) {
            val payload = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage("system", system),
                    ChatMessage("user", user),
                ),
                temperature = temperature,
            )
            val body = json.encodeToString(ChatRequest.serializer(), payload)
                .toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $apiKey")
                .post(body)
                .build()

            try {
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw LlmError.Http(resp.code, bodyStr)
                    val parsed = try {
                        json.decodeFromString(ChatResponse.serializer(), bodyStr)
                    } catch (e: Exception) {
                        throw LlmError.Decode(e.message ?: "json parse failed")
                    }
                    parsed.choices.firstOrNull()?.message?.content.orEmpty()
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
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    }
}
