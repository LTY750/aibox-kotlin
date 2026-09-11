package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.model.ApiStyle
import com.aibox.kotlin.core.model.ProviderModelInfo
import com.aibox.kotlin.core.model.TokenUsage
import com.aibox.kotlin.core.network.AiHttpClient
import com.aibox.kotlin.core.network.ApiError
import com.aibox.kotlin.core.network.MidStreamApiError
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
 * Google Gemini 协议（generativelanguage v1beta）。
 * 线格式：?alt=sse 的 data 帧；tool call 一次性完整到达（无参数分片）；
 * thought=true 的 part 为思考内容；usageMetadata 携带 token 统计。
 *
 * 关键契约：mid-stream error 帧——HTTP 200 之后 Google 仍可能下发
 * {"error":{...}}；内容未流出前映射为 ApiError（可重试），
 * 已流出后映射为 MidStreamApiError（绝不重试）。
 */
class GeminiProvider(
    override val id: String,
    private val apiHost: String,
    private val apiKey: String?,
    private val httpClient: AiHttpClient,
    private val models: List<ProviderModelInfo>,
) : AiProvider {

    override val apiStyle = ApiStyle.GOOGLE

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = flow {
        val body = buildRequestBody(request)
        val url = apiHost.removeSuffix("/") +
            "/models/" + request.model + ":streamGenerateContent?alt=sse"
        val response = try {
            httpClient.execute {
                jsonPost(url, body, mapOf("x-goog-api-key" to (apiKey ?: "")))
            }
        } catch (t: Throwable) {
            emit(StreamEvent.Error(mapNetworkError(t)))
            return@flow
        }

        val accumulator = GeminiChunkAccumulator()
        var finished = false
        response.bodyAsChannel().toSseFlow().collect { frame ->
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

    internal fun buildRequestBody(request: ChatRequest): String {
        val systemText = request.messages
            .filter { it.role == RequestRole.SYSTEM }
            .flatMap { it.parts.filterIsInstance<RequestPart.Text>() }
            .joinToString("\n") { it.text }

        val json = buildJsonObject {
            put("contents", buildJsonArray {
                for (message in request.messages) {
                    when (message.role) {
                        RequestRole.SYSTEM -> continue
                        RequestRole.USER -> add(buildContent("user", message))
                        RequestRole.ASSISTANT -> add(buildContent("model", message))
                        // Gemini 无 tool 角色：tool 结果以 user 角色 functionResponse 承载
                        RequestRole.TOOL -> add(buildContent("user", message))
                    }
                }
            })
            if (systemText.isNotBlank()) {
                putJsonObject("systemInstruction") {
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", systemText) })
                    })
                }
            }
            putJsonObject("generationConfig") {
                request.temperature?.let { put("temperature", it) }
                request.topP?.let { put("topP", it) }
                request.maxTokens?.let { put("maxOutputTokens", it) }
                // 推理档位 → thinkingConfig（budget 映射与旧版 google-thinking 一致）
                when (request.reasoningLevel) {
                    ReasoningDialects.LEVEL_LOW, ReasoningDialects.LEVEL_MEDIUM, ReasoningDialects.LEVEL_HIGH -> {
                        put("thinkingConfig", buildJsonObject {
                            put("includeThoughts", true)
                            put(
                                "thinkingBudget",
                                when (request.reasoningLevel) {
                                    ReasoningDialects.LEVEL_LOW -> 1024
                                    ReasoningDialects.LEVEL_MEDIUM -> 4096
                                    else -> 8192
                                },
                            )
                        })
                    }
                }
            }
            put(
                "safetySettings",
                buildJsonArray {
                    listOf(
                        "HARM_CATEGORY_HARASSMENT",
                        "HARM_CATEGORY_HATE_SPEECH",
                        "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                        "HARM_CATEGORY_DANGEROUS_CONTENT",
                    ).forEach { category ->
                        add(
                            buildJsonObject {
                                put("category", category)
                                put("threshold", "BLOCK_NONE")
                            },
                        )
                    }
                },
            )
        }
        return json.toString()
    }

    private fun buildContent(role: String, message: RequestMessage) = buildJsonObject {
        put("role", role)
        put("parts", buildJsonArray {
            when (message.role) {
                RequestRole.TOOL -> {
                    // functionResponse：name 取自对应工具调用（TOOL 消息只有文本结果，name 由上游保证非空）
                    add(
                        buildJsonObject {
                            putJsonObject("functionResponse") {
                                put("name", message.toolCallId ?: "")
                                putJsonObject("response") {
                                    put(
                                        "result",
                                        message.parts.filterIsInstance<RequestPart.Text>()
                                            .joinToString("\n") { it.text },
                                    )
                                }
                            }
                        },
                    )
                }
                else -> {
                    message.parts.forEach { part ->
                        when (part) {
                            is RequestPart.Text -> add(buildJsonObject { put("text", part.text) })
                            is RequestPart.Image -> add(
                                buildJsonObject {
                                    putJsonObject("inlineData") {
                                        put("mimeType", part.mediaType)
                                        put("data", part.base64)
                                    }
                                },
                            )
                        }
                    }
                    // 助手历史的工具调用 → functionCall（参数完整对象）
                    if (message.role == RequestRole.ASSISTANT) {
                        message.toolCalls.forEach { call ->
                            add(
                                buildJsonObject {
                                    putJsonObject("functionCall") {
                                        put("name", call.name)
                                        put(
                                            "args",
                                            runCatching { Json.parseToJsonElement(call.arguments) }
                                                .getOrDefault(buildJsonObject { }),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        })
    }

    override suspend fun listModels(): List<ProviderModelInfo> {
        return try {
            val response = httpClient.client.get("$apiHost/models") {
                header("x-goog-api-key", apiKey ?: "")
            }
            if (!response.status.isSuccess()) return emptyList()
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.optArray("models")?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val rawName = obj.optString("name") ?: return@mapNotNull null
                val modelId = rawName.removePrefix("models/")
                val methods = obj.optArray("supportedGenerationMethods")
                    ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
                if (!methods.any { it.contains("generate") }) return@mapNotNull null
                ProviderModelInfo(
                    modelId = modelId,
                    providerId = this@GeminiProvider.id,
                    type = "chat",
                    nickname = obj.optString("displayName"),
                    contextWindow = obj.optLong("inputTokenLimit"),
                    maxOutput = obj.optLong("outputTokenLimit"),
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}

/**
 * 纯函数累加器：Gemini SSE 帧 → StreamEvent。
 * 关键：error 帧按“内容是否已流出”分类为 ApiError / MidStreamApiError。
 */
internal class GeminiChunkAccumulator {
    private var contentForwarded = false
    private var finishSent = false
    private var nextToolIndex = 0

    fun onFrame(json: JsonObject): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()

        // mid-stream error 帧（HTTP 200 后仍可能出现；error 可能为 null / 非对象）
        json.optObject("error")?.let { err ->
            val code = err.optLong("code")?.toInt()
            val message = err.optString("message") ?: "stream error"
            events.add(
                StreamEvent.Error(
                    if (contentForwarded) {
                        MidStreamApiError(statusCode = code, message = message)
                    } else {
                        ApiError(statusCode = code, message = message)
                    },
                ),
            )
            return events
        }

        // 安全拦截
        json.optObject("promptFeedback")?.optString("blockReason")?.let {
            events.add(StreamEvent.Error(ApiError(message = "Blocked: $it")))
            return events
        }

        val candidate = json.optArray("candidates")?.firstOrNull() as? JsonObject
        candidate?.optObject("content")?.optArray("parts")?.forEach { partEl ->
            val part = partEl as? JsonObject ?: return@forEach
            val thought = part.optString("thought") == "true"
            val text = part.optString("text")
            if (text != null) {
                contentForwarded = true
                if (thought) {
                    events.add(StreamEvent.ReasoningDelta(text))
                } else {
                    events.add(StreamEvent.TextDelta(text))
                }
            }
            // inlineData：模型生成的图片
            part.optObject("inlineData")?.let { data ->
                contentForwarded = true
                events.add(
                    StreamEvent.Image(
                        mediaType = data.optString("mimeType") ?: "image/png",
                        base64 = data.optString("data") ?: "",
                    ),
                )
            }
            // functionCall：参数一次性完整到达
            part.optObject("functionCall")?.let { call ->
                contentForwarded = true
                val index = nextToolIndex++
                val name = call.optString("name") ?: ""
                events.add(StreamEvent.ToolCallStart(index, "call_$index", name))
                events.add(
                    StreamEvent.ToolCallArgsDelta(index, (call["args"] ?: buildJsonObject { }).toString()),
                )
                events.add(StreamEvent.ToolCallEnd(index))
            }
        }
        candidate?.optString("finishReason")?.let {
            if (!finishSent) {
                finishSent = true
                events.add(StreamEvent.Finish(mapFinishReason(it)))
            }
        }
        json.optObject("usageMetadata")?.let { usage ->
            val prompt = usage.optLong("promptTokenCount") ?: 0
            val candidatesTokens = usage.optLong("candidatesTokenCount") ?: 0
            val thoughts = usage.optLong("thoughtsTokenCount") ?: 0
            val total = usage.optLong("totalTokenCount")
            events.add(
                StreamEvent.Usage(
                    TokenUsage(
                        inputTokens = prompt,
                        outputTokens = candidatesTokens + thoughts,
                        totalTokens = total,
                        reasoningTokens = thoughts.takeIf { it > 0 },
                    ),
                ),
            )
        }
        return events
    }

    fun onDone(): List<StreamEvent> =
        if (!finishSent) {
            finishSent = true
            listOf(StreamEvent.Finish("stop"))
        } else {
            emptyList()
        }

    private fun mapFinishReason(reason: String): String = when (reason) {
        "STOP" -> "stop"
        "MAX_TOKENS" -> "length"
        "SAFETY" -> "content-filter"
        else -> reason.lowercase()
    }
}
