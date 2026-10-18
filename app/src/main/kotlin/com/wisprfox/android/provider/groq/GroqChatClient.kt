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
 * `wispr-fox/src-tauri/src/llm/groq.rs`, including the reasoning-model handling
 * that port added: every Groq model we offer today (GPT-OSS 20B/120B, Qwen 3.x)
 * is a *reasoning* model, and Groq's default `max_completion_tokens` is 1024
 * with the thinking counted against that budget — so a long dictation could
 * spend the whole cap reasoning and return `content: null`, which a
 * non-nullable parser rejected as a decode error ("Cleanup failed"). We lift
 * the budget to 16k, ask each family to suppress/hide its chain-of-thought (so
 * it neither leaks into `content` nor is billed to download), parse a nullable
 * `content`, and strip any `<think>…</think>` a model emits anyway.
 */
class GroqChatClient(
    baseClient: OkHttpClient,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : LlmProvider {

    override val name: String = "groq"

    private val client: OkHttpClient = baseClient.newBuilder()
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    // explicitNulls=false drops the reasoning_* fields when they don't apply to
    // the selected model (Groq returns 400 for a param a model doesn't support).
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Serializable private data class ChatMessage(val role: String, val content: String)
    @Serializable private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Float,
        val max_completion_tokens: Int,
        /** Qwen only. `hidden` = final answer only (else Qwen dumps its
         *  chain-of-thought into `content` as `<think>…</think>`). */
        val reasoning_format: String? = null,
        /** GPT-OSS: `low` (cleanup is a rewrite, not a proof). Qwen: `none`. */
        val reasoning_effort: String? = null,
        /** GPT-OSS only (mutually exclusive with reasoning_format): drop the
         *  reasoning field from the reply so we don't pay to download it. */
        val include_reasoning: Boolean? = null,
    )
    /** `content` is nullable: a reasoning model can exhaust its budget before
     *  answering, or return only tool calls. */
    @Serializable private data class ChatChoiceMessage(val content: String? = null)
    @Serializable private data class ChatChoice(
        val message: ChatChoiceMessage,
        val finish_reason: String? = null,
    )
    @Serializable private data class ChatResponse(val choices: List<ChatChoice> = emptyList())

    override suspend fun complete(system: String, user: String, temperature: Float): String =
        withContext(Dispatchers.IO) {
            // Per-family reasoning params (Groq rejects unsupported ones with 400).
            val reasoningFormat: String?
            val reasoningEffort: String?
            val includeReasoning: Boolean?
            when {
                isGptOss(model) -> { reasoningFormat = null; reasoningEffort = "low"; includeReasoning = false }
                isQwen(model) -> { reasoningFormat = "hidden"; reasoningEffort = "none"; includeReasoning = null }
                else -> { reasoningFormat = null; reasoningEffort = null; includeReasoning = null }
            }
            val payload = ChatRequest(
                model = model,
                messages = listOf(
                    ChatMessage("system", system),
                    ChatMessage("user", user),
                ),
                temperature = temperature,
                max_completion_tokens = MAX_COMPLETION_TOKENS,
                reasoning_format = reasoningFormat,
                reasoning_effort = reasoningEffort,
                include_reasoning = includeReasoning,
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
                    val choice = parsed.choices.firstOrNull()
                    val text = choice?.message?.content?.let { stripThinkBlock(it) }.orEmpty()
                    // Blank answer (budget exhausted / all-thinking) → a decode
                    // error, so CleanupOrchestrator falls back to the raw
                    // transcript instead of saving an empty cleanup.
                    if (text.isBlank()) {
                        throw LlmError.Decode(
                            "Groq returned no answer text (finish_reason=${choice?.finish_reason}, model=$model)"
                        )
                    }
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
        const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_MODEL = "openai/gpt-oss-20b"

        /** Groq's default completion cap is 1024 and reasoning counts against
         *  it; 16k comfortably covers a Draft rewrite of a 20-minute recording. */
        const val MAX_COMPLETION_TOKENS = 16_384

        internal fun isGptOss(model: String): Boolean = model.startsWith("openai/gpt-oss")
        internal fun isQwen(model: String): Boolean = model.startsWith("qwen/")

        /**
         * Strip a leading `<think>…</think>` block if a model emitted one despite
         * reasoning_format=hidden (older Qwen builds). An unterminated `<think>`
         * means the whole reply was thinking — return empty so the caller treats
         * it as a failure rather than pasting a monologue.
         */
        internal fun stripThinkBlock(s: String): String {
            val t = s.trimStart()
            if (!t.startsWith("<think>")) return s
            val end = t.indexOf("</think>")
            return if (end >= 0) t.substring(end + "</think>".length) else ""
        }
    }
}
