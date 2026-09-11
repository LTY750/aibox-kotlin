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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI Responses 协议（/responses 端点）。
 * 与 Chat Completions 的差异：
 * - system 提示走 instructions 字段
 * - content part 类型为 input_text / input_image / output_text
 * - 流事件为命名事件（response.output_text.delta 等）
 * - 工具定义扁平（type/name/description/parameters 在同一层）
 */
class OpenAiResponsesProvider(
    override val id: String,
    private val base: String,
    private val path: String,
    private val apiKey: String?,
    private val httpClient: AiHttpClient,
    private val models: List<ProviderModelInfo>,
) : AiProvider {

    override val apiStyle = ApiStyle.OPENAI_RESPONSES

    private val responsesUrl = base.removeSuffix("/") + path.removeSuffix("/") + "/responses"

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = flow {
        val body = buildRequestBody(request)
        val response = try {
            httpClient.execute {
                jsonPost(responsesUrl, body, authHeaders())
            }
        } catch (t: Throwable) {
            emit(StreamEvent.Error(mapNetworkError(t)))
            return@flow
        }

        val accumulator = ResponsesEventAccumulator()
        var finished = false
        response.bodyAsChannel().toSseFlow().collect { frame ->
            val json = runCatching { Json.parseToJsonElement(frame.data).jsonObject }
                .getOrElse { return@collect }
            val events = accumulator.onEvent(frame.event, json)
            for (event in events) {
                if (event is StreamEvent.Error) {
                    emit(event)
                    finished = true
                    return@collect
                }
                emit(event)
            }
        }
        if (!finished) {
            accumulator.onDone().forEach { emit(it) }
        }
    }.withAiRetry()

    private fun authHeaders(): Map<String, String> =
        if (apiKey != null) mapOf("Authorization" to "Bearer $apiKey") else emptyMap()

    internal fun buildRequestBody(request: ChatRequest): String {
        val instructions = request.messages
            .filter { it.role == RequestRole.SYSTEM }
            .flatMap { it.parts.filterIsInstance<RequestPart.Text>() }
            .joinToString("\n") { it.text }

        val json = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            put("store", false)
            if (instructions.isNotBlank()) put("instructions", instructions)
            request.temperature?.let { put("temperature", it) }
            request.topP?.let { put("top_p", it) }
            request.maxTokens?.let { put("max_output_tokens", it) }
            when (request.reasoningLevel) {
                ReasoningDialects.LEVEL_LOW, ReasoningDialects.LEVEL_MEDIUM, ReasoningDialects.LEVEL_HIGH ->
                    putJsonObject("reasoning") {
                        put("effort", request.reasoningLevel)
                        put("summary", "auto")
                    }
                ReasoningDialects.LEVEL_OFF ->
                    putJsonObject("reasoning") { put("effort", "low") }
            }
            put("input", buildJsonArray {
                for (message in request.messages) {
                    if (message.role == RequestRole.SYSTEM) continue
                    when (message.role) {
                        RequestRole.USER -> add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", buildContent(message, user = true))
                            },
                        )
                        RequestRole.ASSISTANT -> add(
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", buildContent(message, user = false))
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
                                                put("type", "input_text")
                                                put(
                                                    "text",
                                                    message.parts.filterIsInstance<RequestPart.Text>()
                                                        .joinToString("\n") { it.text },
                                                )
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
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("type", "function")
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", Json.parseToJsonElement(tool.parametersJsonSchema))
                            },
                        )
                    }
                })
            }
        }
        return json.toString()
    }

    private fun buildContent(message: RequestMessage, user: Boolean) = buildJsonArray {
        message.parts.forEach { part ->
            when (part) {
                is RequestPart.Text -> add(
                    buildJsonObject {
                        put("type", if (user) "input_text" else "output_text")
                        put("text", part.text)
                    },
                )
                is RequestPart.Image -> add(
                    buildJsonObject {
                        put("type", "input_image")
                        put("image_url", "data:${part.mediaType};base64,${part.base64}")
                    },
                )
            }
        }
    }

    override suspend fun listModels(): List<ProviderModelInfo> {
        return try {
            val url = base.removeSuffix("/") + path.removeSuffix("/") + "/models"
            val response = httpClient.client.get(url) {
                authHeaders().forEach { (k, v) -> header(k, v) }
            }
            if (!response.status.isSuccess()) return emptyList()
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.optArray("data")?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj.optString("id") ?: return@mapNotNull null
                ProviderModelInfo(
                    modelId = id,
                    providerId = this@OpenAiResponsesProvider.id,
                    type = "chat",
                    nickname = obj.optString("name"),
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * 纯函数累加器：Responses SSE 命名事件 → StreamEvent。
 * 解析容错约定与 OpenAiChunkAccumulator 一致。
 */
internal class ResponsesEventAccumulator {
    private var inputTokens: Long? = null
    private var outputTokens: Long? = null
    private var reasoningTokens: Long? = null
    private var finishSent = false
    private val openToolIndices = mutableSetOf<Int>()

    fun onEvent(event: String?, json: JsonObject): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        when (event) {
            "response.output_text.delta" -> {
                json.optString("delta")?.takeIf { it.isNotEmpty() }
                    ?.let { events.add(StreamEvent.TextDelta(it)) }
            }
            "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                json.optString("delta")?.takeIf { it.isNotBlank() }
                    ?.let { events.add(StreamEvent.ReasoningDelta(it)) }
            }
            "response.output_item.added" -> {
                val item = json.optObject("output_item") ?: return events
                if (item.optString("type") == "function_call") {
                    val index = json.optInt("output_index") ?: 0
                    openToolIndices.add(index)
                    events.add(
                        StreamEvent.ToolCallStart(
                            index = index,
                            toolCallId = item.optString("call_id") ?: item.optString("id") ?: "",
                            name = item.optString("name") ?: "",
                        ),
                    )
                }
            }
            "response.function_call_arguments.delta" -> {
                val index = json.optInt("output_index") ?: 0
                json.optString("delta")?.takeIf { it.isNotEmpty() }
                    ?.let { events.add(StreamEvent.ToolCallArgsDelta(index, it)) }
            }
            "response.output_item.done" -> {
                val item = json.optObject("output_item")
                if (item?.optString("type") == "function_call") {
                    val index = json.optInt("output_index") ?: 0
                    if (openToolIndices.remove(index)) {
                        events.add(StreamEvent.ToolCallEnd(index))
                        // 参数一次性到达（非流式工具调用）时兜底发参数
                        item.optString("arguments")?.takeIf { it.isNotEmpty() }?.let {
                            events.add(StreamEvent.ToolCallArgsDelta(index, it))
                        }
                    }
                }
            }
            "response.completed" -> {
                readUsage(json.optObject("response"))
                events.addAll(finish("stop"))
            }
            "response.incomplete" -> {
                readUsage(json.optObject("response"))
                events.addAll(finish("length"))
            }
            "response.failed" -> {
                val err = json.optObject("response")?.optObject("error")
                events.add(
                    StreamEvent.Error(
                        com.aibox.kotlin.core.network.ApiError(
                            statusCode = null,
                            message = err?.optString("message") ?: "response failed",
                        ),
                    ),
                )
            }
            "error" -> {
                val err = json.optObject("error")
                events.add(
                    StreamEvent.Error(
                        com.aibox.kotlin.core.network.ApiError(
                            statusCode = null,
                            message = err?.optString("message") ?: "stream error",
                        ),
                    ),
                )
            }
            else -> Unit
        }
        return events
    }

    private fun readUsage(response: JsonObject?) {
        val usage = response?.optObject("usage") ?: return
        inputTokens = usage.optLong("input_tokens") ?: inputTokens
        outputTokens = usage.optLong("output_tokens") ?: outputTokens
        reasoningTokens = usage.optObject("output_tokens_details")?.optLong("reasoning_tokens")
            ?: reasoningTokens
    }

    private fun finish(reason: String): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        if (inputTokens != null || outputTokens != null) {
            events.add(
                StreamEvent.Usage(
                    TokenUsage(
                        inputTokens = inputTokens,
                        outputTokens = outputTokens,
                        totalTokens = (inputTokens ?: 0) + (outputTokens ?: 0),
                        reasoningTokens = reasoningTokens,
                    ),
                ),
            )
        }
        if (!finishSent) {
            finishSent = true
            events.add(StreamEvent.Finish(reason))
        }
        return events
    }

    fun onDone(): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        openToolIndices.sorted().forEach { events.add(StreamEvent.ToolCallEnd(it)) }
        openToolIndices.clear()
        if (!finishSent) {
            events.addAll(finish("stop"))
        }
        return events
    }
}
