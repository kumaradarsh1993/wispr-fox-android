package com.wisprfox.android.provider.openai

import com.wisprfox.android.provider.LlmError
import com.wisprfox.android.provider.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/** OpenAI Responses API client for cleanup and drafting. */
class OpenAiLlmClient(
    baseClient: OkHttpClient,
    private val apiKey: String,
    private val model: String,
) : LlmProvider {

    override val name: String = "openai"

    private val client = baseClient.newBuilder()
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class ResponsesRequest(
        val model: String,
        val instructions: String,
        val input: String,
        val temperature: Float,
        @SerialName("max_output_tokens") val maxOutputTokens: Int,
        val reasoning: Reasoning,
    )

    @Serializable
    private data class Reasoning(val effort: String)

    override suspend fun complete(system: String, user: String, temperature: Float): String =
        withContext(Dispatchers.IO) {
            val payload = ResponsesRequest(
                model = model,
                instructions = system,
                input = user,
                temperature = temperature,
                maxOutputTokens = 2048,
                reasoning = Reasoning("low"),
            )
            val body = json.encodeToString(ResponsesRequest.serializer(), payload)
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
                    val root = try {
                        json.parseToJsonElement(bodyStr).jsonObject
                    } catch (e: Exception) {
                        throw LlmError.Decode(e.message ?: "json parse failed")
                    }
                    val text = extractOutputText(root)
                    if (text.isBlank()) throw LlmError.Decode("empty response from OpenAI")
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

    private fun extractOutputText(root: JsonObject): String {
        root["output_text"]?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        val out = StringBuilder()
        val items = root["output"] as? JsonArray ?: return ""
        for (item in items) {
            val content = item.jsonObject["content"]?.jsonArray ?: continue
            for (part in content) {
                val obj = part.jsonObject
                val type = obj["type"]?.jsonPrimitive?.contentOrNull
                if (type == "output_text") {
                    val text = (obj["text"] as? JsonPrimitive)?.contentOrNull
                    if (text != null) out.append(text)
                }
            }
        }
        return out.toString()
    }

    companion object {
        private const val ENDPOINT = "https://api.openai.com/v1/responses"
    }
}
