package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.model.ApiStyle
import com.aibox.kotlin.core.model.ProviderModelInfo
import com.aibox.kotlin.core.model.TokenUsage
import com.aibox.kotlin.core.network.AiHttpClient
import com.aibox.kotlin.core.network.jsonPost
import com.aibox.kotlin.core.network.toSseFlow
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Anthropic Messages 协议。
 * 线格式：SSE 命名事件（message_start / content_block_* / message_delta / message_stop / error）。
 * 约束：max_tokens 必填；temperature 与 top_p 互斥（优先 temperature）。
 */
class AnthropicProvider(
    override val id: String,
    private val apiHost: String,
    private val apiKey: String?,
    private val httpClient: AiHttpClient,
    private val models: List<ProviderModelInfo>,
) : AiProvider {

    override val apiStyle = ApiStyle.ANTHROPIC

    private val messagesUrl = apiHost.removeSuffix("/") + "/messages"

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = flow {
        val body = buildRequestBody(request)
        val response = try {
            httpClient.execute {
                jsonPost(
                    messagesUrl,
                    body,
                    mapOf(
                        "x-api-key" to (apiKey ?: ""),
                        "anthropic-version" to "2023-06-01",
                        "anthropic-dangerous-direct-browser-access" to "true",
                    ),
                )
            }
        } catch (t: Throwable) {
            emit(StreamEvent.Error(mapNetworkError(t)))
            return@flow
        }

        val accumulator = AnthropicEventAccumulator()
        var finished = false
        response.bodyAsChannel().toSseFlow().collect { frame ->
            val json = runCatching { Json.parseToJsonElement(frame.data).jsonObject }
                .getOrElse { return@collect }
            val event = accumulator.onEvent(frame.event, json)
            if (event is StreamEvent.Error) {
                emit(event)
                finished = true
                return@collect
            }
            if (event != null) emit(event)
        }
        if (!finished) {
            accumulator.onDone().forEach { emit(it) }
        }
    }.withAiRetry()

    internal fun buildRequestBody(request: ChatRequest): String {
        val systemText = request.messages
            .filter { it.role == RequestRole.SYSTEM }
            .flatMap { it.parts.filterIsInstance<RequestPart.Text>() }
            .joinToString("\n") { it.text }

        val json = buildJsonObject {
            put("model", request.model)
            // thinking budget 必须小于 max_tokens；开启推理时确保 max_tokens 足够
            val thinkingBudget = when (request.reasoningLevel) {
                ReasoningDialects.LEVEL_LOW -> 1024L
                ReasoningDialects.LEVEL_MEDIUM -> 4096L
                ReasoningDialects.LEVEL_HIGH -> 8192L
                else -> null
            }
            val maxTokens = request.maxTokens ?: DEFAULT_MAX_TOKENS
            val effectiveMaxTokens = if (thinkingBudget != null && maxTokens <= thinkingBudget + 1024) {
                thinkingBudget + 4096
            } else {
                maxTokens
            }
            put("max_tokens", effectiveMaxTokens)
            put("stream", true)
            if (systemText.isNotBlank()) put("system", systemText)
            if (thinkingBudget != null) {
                put("thinking", buildJsonObject {
                    put("type", "enabled")
                    put("budget_tokens", thinkingBudget)
                })
            }
            // temperature 与 top_p 互斥，优先 temperature
            request.temperature?.let { put("temperature", it) }
                ?: request.topP?.let { put("top_p", it) }
            put("messages", buildJsonArray {
                for (message in request.messages) {
                    if (message.role == RequestRole.SYSTEM) continue
                    when (message.role) {
                        RequestRole.USER -> add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", buildUserContent(message))
                            },
                        )
                        RequestRole.ASSISTANT -> add(
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", buildAssistantContent(message))
                            },
                        )
                        RequestRole.TOOL -> add(
                            buildJsonObject {
                                put("role", "user")
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("tool_use_id", message.toolCallId ?: "")
                                                put(
                                                    "content",
                                                    message.parts.filterIsInstance<RequestPart.Text>()
                                                        .joinToString("\n") { it.text },
                                                )
                                                put("type", "tool_result")
                                            },
                                        )
                                    },
                                )
                            },
                        )
                        RequestRole.SYSTEM -> Unit
                    }
                }
            })
        }
        return json.toString()
    }

    private fun buildUserContent(message: RequestMessage) = buildJsonArray {
        message.parts.forEach { part ->
            when (part) {
                is RequestPart.Text -> add(buildJsonObject { put("type", "text"); put("text", part.text) })
                is RequestPart.Image -> add(
                    buildJsonObject {
                        put("type", "image")
                        putJsonObject("source") {
                            put("type", "base64")
                            put("media_type", part.mediaType)
                            put("data", part.base64)
                        }
                    },
                )
            }
        }
    }

    private fun buildAssistantContent(message: RequestMessage) = buildJsonArray {
        message.parts.filterIsInstance<RequestPart.Text>()
            .forEach { add(buildJsonObject { put("type", "text"); put("text", it.text) }) }
        message.toolCalls.forEach { call ->
            add(
                buildJsonObject {
                    put("type", "tool_use")
                    put("id", call.toolCallId)
                    put("name", call.name)
                    put("input", runCatching { Json.parseToJsonElement(call.arguments) }.getOrDefault(JsonNull))
                },
            )
        }
    }

    override suspend fun listModels(): List<ProviderModelInfo> {
        return try {
            val response = httpClient.client.get("$apiHost/models?limit=990") {
                header("x-api-key", apiKey ?: "")
                header("anthropic-version", "2023-06-01")
            }
            if (!response.status.isSuccess()) return emptyList()
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body["data"]?.jsonArray?.mapNotNull { element ->
                val obj = element.jsonObject
                if (obj["type"]?.jsonPrimitive?.content != "model") return@mapNotNull null
                val modelId = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                ProviderModelInfo(modelId = modelId, providerId = this@AnthropicProvider.id, type = "chat")
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        const val DEFAULT_MAX_TOKENS = 8192L
    }
}

/** 纯函数累加器：Anthropic SSE 命名事件 → StreamEvent。 */
internal class AnthropicEventAccumulator {
    private var inputTokens: Long? = null
    private var outputTokens: Long? = null
    private var finishSent = false
    private val toolIndices = mutableMapOf<Int, Boolean>() // index -> isToolUse

    fun onEvent(event: String?, json: JsonObject): StreamEvent? {
        // 全部走 opt* 安全访问：字段为 null / 缺失 / 类型异常时按缺失处理，绝不抛异常
        return when (event) {
            "message_start" -> {
                inputTokens = json.optObject("message")?.optObject("usage")?.optLong("input_tokens")
                inputTokens?.let { StreamEvent.Usage(TokenUsage(inputTokens = it)) }
            }
            "content_block_start" -> {
                val index = json.optInt("index") ?: return null
                val block = json.optObject("content_block") ?: return null
                if (block.optString("type") == "tool_use") {
                    toolIndices[index] = true
                    StreamEvent.ToolCallStart(
                        index = index,
                        toolCallId = block.optString("id") ?: "",
                        name = block.optString("name") ?: "",
                    )
                } else {
                    null
                }
            }
            "content_block_delta" -> {
                val index = json.optInt("index") ?: return null
                val delta = json.optObject("delta")
                when (delta?.optString("type")) {
                    "text_delta" -> StreamEvent.TextDelta(delta.optString("text") ?: "")
                    "thinking_delta" -> StreamEvent.ReasoningDelta(delta.optString("thinking") ?: "")
                    "input_json_delta" -> StreamEvent.ToolCallArgsDelta(index, delta.optString("partial_json") ?: "")
                    else -> null
                }
            }
            "content_block_stop" -> {
                val index = json.optInt("index") ?: return null
                if (toolIndices[index] == true) StreamEvent.ToolCallEnd(index) else null
            }
            "message_delta" -> {
                json.optObject("usage")?.optLong("output_tokens")?.let { outputTokens = it }
                val stop = json.optObject("delta")?.optString("stop_reason")
                if (stop != null && !finishSent) {
                    finishSent = true
                    StreamEvent.Finish(mapStopReason(stop))
                } else {
                    outputTokens?.let { StreamEvent.Usage(TokenUsage(outputTokens = it, inputTokens = inputTokens)) }
                }
            }
            "message_stop" -> {
                if (!finishSent) {
                    finishSent = true
                    StreamEvent.Finish("stop")
                } else {
                    null
                }
            }
            "error" -> {
                val obj = json.optObject("error")
                StreamEvent.Error(
                    com.aibox.kotlin.core.network.ApiError(
                        statusCode = null,
                        message = obj?.optString("message") ?: "stream error",
                    ),
                )
            }
            else -> null // ping 及未知事件
        }
    }

    fun onDone(): List<StreamEvent> =
        if (!finishSent) {
            finishSent = true
            listOf(
                StreamEvent.Usage(
                    TokenUsage(inputTokens = inputTokens, outputTokens = outputTokens),
                ),
                StreamEvent.Finish("stop"),
            )
        } else {
            emptyList()
        }

    private fun mapStopReason(reason: String): String = when (reason) {
        "max_tokens" -> "length"
        "tool_use" -> "tool-calls"
        "end_turn" -> "stop"
        else -> reason
    }
}
