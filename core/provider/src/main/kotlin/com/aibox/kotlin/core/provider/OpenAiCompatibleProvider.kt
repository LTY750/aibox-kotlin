package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.model.ApiStyle
import com.aibox.kotlin.core.model.ProviderModelInfo
import com.aibox.kotlin.core.model.TokenUsage
import com.aibox.kotlin.core.network.AiHttpClient
import com.aibox.kotlin.core.network.ApiError
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI Chat Completions 兼容协议（openai/deepseek/qwen/groq/openrouter/ollama 等）。
 * 线格式：SSE data 帧 + 终止 [DONE]；reasoning_content 承载思考增量；
 * tool_calls 按 index 累积参数分片。
 */
class OpenAiCompatibleProvider(
    override val id: String,
    private val base: String,
    private val path: String,
    private val apiKey: String?,
    private val httpClient: AiHttpClient,
    private val models: List<ProviderModelInfo>,
) : AiProvider {

    override val apiStyle = ApiStyle.OPENAI

    private val chatUrl = base.removeSuffix("/") + path

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = flow {
        val body = buildRequestBody(request)
        val response = try {
            httpClient.execute {
                jsonPost(chatUrl, body, authHeaders())
            }
        } catch (t: Throwable) {
            emit(StreamEvent.Error(mapNetworkError(t)))
            return@flow
        }

        val accumulator = OpenAiChunkAccumulator()
        var finished = false
        response.bodyAsChannel().toSseFlow().collect { frame ->
            if (frame.data == "[DONE]") {
                accumulator.onDone().forEach { emit(it) }
                finished = true
                return@collect
            }
            val json = runCatching { Json.parseToJsonElement(frame.data).jsonObject }
                .getOrElse { return@collect }
            for (event in accumulator.onFrame(json)) {
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

    private fun authHeaders(): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        if (apiKey != null) headers["Authorization"] = "Bearer $apiKey"
        if (id == "openrouter") headers.putAll(openRouterHeaders())
        return headers
    }

    internal fun buildRequestBody(request: ChatRequest): String {
        val json = buildJsonObject {
            put("model", request.model)
            put("stream", true)
            putJsonObject("stream_options") { put("include_usage", true) }
            request.temperature?.let { put("temperature", it) }
            request.topP?.let { put("top_p", it) }
            request.maxTokens?.let { put("max_tokens", it) }
            ReasoningDialects.apply(this, id, request.model, request.reasoningLevel)
            put("messages", buildJsonArray {
                for (message in request.messages) {
                    add(buildJsonObject {
                        when (message.role) {
                            RequestRole.SYSTEM -> {
                                put("role", "system")
                                put("content", message.parts.joinToString("\n") { (it as RequestPart.Text).text })
                            }
                            RequestRole.USER -> {
                                put("role", "user")
                                val texts = message.parts.filterIsInstance<RequestPart.Text>()
                                val images = message.parts.filterIsInstance<RequestPart.Image>()
                                if (images.isEmpty() && texts.size == 1) {
                                    put("content", texts[0].text)
                                } else {
                                    put("content", buildJsonArray {
                                        texts.forEach { add(buildJsonObject { put("type", "text"); put("text", it.text) }) }
                                        images.forEach {
                                            add(
                                                buildJsonObject {
                                                    put("type", "image_url")
                                                    putJsonObject("image_url") {
                                                        put("url", "data:${it.mediaType};base64,${it.base64}")
                                                    }
                                                },
                                            )
                                        }
                                    })
                                }
                            }
                            RequestRole.ASSISTANT -> {
                                put("role", "assistant")
                                val text = message.parts.filterIsInstance<RequestPart.Text>()
                                    .joinToString("\n") { it.text }
                                if (text.isNotBlank()) put("content", text) else put("content", null as String?)
                                if (message.toolCalls.isNotEmpty()) {
                                    put("tool_calls", buildJsonArray {
                                        message.toolCalls.forEach { call ->
                                            add(
                                                buildJsonObject {
                                                    put("id", call.toolCallId)
                                                    put("type", "function")
                                                    putJsonObject("function") {
                                                        put("name", call.name)
                                                        put("arguments", call.arguments)
                                                    }
                                                },
                                            )
                                        }
                                    })
                                }
                            }
                            RequestRole.TOOL -> {
                                put("role", "tool")
                                put("tool_call_id", message.toolCallId ?: "")
                                put(
                                    "content",
                                    message.parts.filterIsInstance<RequestPart.Text>()
                                        .joinToString("\n") { it.text },
                                )
                            }
                        }
                    })
                }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", buildJsonArray {
                    request.tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", Json.parseToJsonElement(tool.parametersJsonSchema))
                                }
                            },
                        )
                    }
                })
            }
        }
        return json.toString()
    }

    override suspend fun listModels(): List<ProviderModelInfo> {
        return try {
            val url = HostUtils.modelsListBase(base, path)
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
                    providerId = this@OpenAiCompatibleProvider.id,
                    type = "chat",
                    nickname = obj.optString("name"),
                    contextWindow = obj.optLong("context_length"),
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * 纯函数累加器：OpenAI chunk → StreamEvent。
 * 与 HTTP/Flow 解耦，便于单元测试。
 *
 * 解析完全容错：真实服务商的帧形状不可信（DeepSeek 的 usage 帧带
 * "choices": null，部分网关 delta 为 null），任何 null/缺失/类型异常
 * 都按"字段缺失"处理，绝不抛异常——一次解析失败会毁掉整条已计费的流。
 */
internal class OpenAiChunkAccumulator {
    private val openToolIndices = mutableSetOf<Int>()
    private var finishReason: String? = null

    fun onFrame(json: JsonObject): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()

        // 错误帧（error 可能为 null / 非对象）
        json.optObject("error")?.let { err ->
            val message = err.optString("message") ?: "stream error"
            events.add(StreamEvent.Error(ApiError(statusCode = err.optInt("code"), message = message)))
            return events
        }

        // choices 在 usage-only 帧中可能为 null / 缺失 / 空数组
        val choice = json.optArray("choices")?.firstOrNull() as? JsonObject
        val delta = choice?.optObject("delta")
        if (delta != null) {
            delta.optString("content")?.takeIf { it.isNotEmpty() }
                ?.let { events.add(StreamEvent.TextDelta(it)) }
            val reasoning = delta.optString("reasoning_content")
                ?: delta.optString("reasoning")
            reasoning?.takeIf { it.isNotBlank() }
                ?.let { events.add(StreamEvent.ReasoningDelta(it)) }
            delta.optArray("tool_calls")?.forEach { callEl ->
                val call = callEl as? JsonObject ?: return@forEach
                val index = call.optInt("index") ?: 0
                val function = call.optObject("function")
                call.optString("id")?.let { id ->
                    openToolIndices.add(index)
                    events.add(
                        StreamEvent.ToolCallStart(
                            index = index,
                            toolCallId = id,
                            name = function?.optString("name") ?: "",
                        ),
                    )
                }
                function?.optString("arguments")?.takeIf { it.isNotEmpty() }
                    ?.let { events.add(StreamEvent.ToolCallArgsDelta(index, it)) }
            }
        }
        choice?.optString("finish_reason")?.let { finishReason = it }
        json.optObject("usage")?.let { usage ->
            events.add(
                StreamEvent.Usage(
                    TokenUsage(
                        inputTokens = usage.optLong("prompt_tokens"),
                        outputTokens = usage.optLong("completion_tokens"),
                        totalTokens = usage.optLong("total_tokens"),
                    ),
                ),
            )
        }
        return events
    }

    fun onDone(): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        openToolIndices.sorted().forEach { events.add(StreamEvent.ToolCallEnd(it)) }
        openToolIndices.clear()
        events.add(StreamEvent.Finish(mapFinishReason(finishReason)))
        return events
    }

    private fun mapFinishReason(reason: String?): String = when (reason) {
        "tool_calls", "function_call" -> "tool-calls"
        "length" -> "length"
        "stop" -> "stop"
        "content_filter" -> "content-filter"
        null -> "stop"
        else -> reason
    }
}

internal fun mapNetworkError(t: Throwable): Throwable = AiHttpClient.mapThrowable(t)
