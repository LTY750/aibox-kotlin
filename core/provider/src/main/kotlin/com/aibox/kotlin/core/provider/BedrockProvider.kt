package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.model.ApiStyle
import com.aibox.kotlin.core.model.ProviderModelInfo
import com.aibox.kotlin.core.model.TokenUsage
import com.aibox.kotlin.core.network.AiHttpClient
import com.aibox.kotlin.core.network.NetworkError
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * AWS Bedrock Converse Stream 协议。
 * - 鉴权：SigV4 签名（见 SigV4Signer）；apiKey 存 AccessKeyId，
 *   secretAccessKey / sessionToken / region 存 ProviderSettings 专用字段
 * - 流格式：application/vnd.amazon.eventstream 二进制分帧（见 AwsEventStreamParser）
 * - 推理档位：Claude 系列映射为 additionalModelRequestFields.thinking（budget_tokens）
 */
class BedrockProvider(
    override val id: String,
    private val region: String,
    private val accessKeyId: String?,
    private val secretAccessKey: String?,
    private val sessionToken: String?,
    private val httpClient: AiHttpClient,
    private val models: List<ProviderModelInfo>,
) : AiProvider {

    override val apiStyle = ApiStyle.ANTHROPIC

    private val runtimeHost = "bedrock-runtime.$region.amazonaws.com"
    private val controlHost = "bedrock.$region.amazonaws.com"

    init {
        if (accessKeyId.isNullOrBlank() || secretAccessKey.isNullOrBlank()) {
            throw IllegalArgumentException("Bedrock requires AccessKeyId (apiKey) and SecretAccessKey")
        }
    }

    override fun streamChat(request: ChatRequest): Flow<StreamEvent> = flow {
        val body = buildRequestBody(request)
        val bodyBytes = body.toByteArray(Charsets.UTF_8)
        val encodedModel = URLEncoder.encode(request.model, "UTF-8")
        val path = "/model/$encodedModel/converse-stream"
        val amzDate = amzTimestamp()
        val signed = SigV4Signer.sign(
            method = "POST",
            host = runtimeHost,
            path = path,
            query = "",
            bodyBytes = bodyBytes,
            accessKeyId = accessKeyId!!,
            secretAccessKey = secretAccessKey!!,
            sessionToken = sessionToken,
            region = region,
            service = "bedrock",
            amzDate = amzDate,
            additionalHeaders = mapOf("content-type" to "application/json"),
        )

        val response = try {
            httpClient.execute {
                method = HttpMethod.Post
                url {
                    protocol = URLProtocol.HTTPS
                    host = runtimeHost
                    encodedPath = path
                }
                contentType(ContentType.Application.Json)
                header("Accept", "application/vnd.amazon.eventstream")
                header("x-amz-date", signed.amzDate)
                signed.securityTokenHeader.forEach { (k, v) -> header(k, v) }
                header("Authorization", signed.authorization)
                setBody(bodyBytes)
            }
        } catch (t: Throwable) {
            emit(StreamEvent.Error(AiHttpClient.mapThrowable(t)))
            return@flow
        }

        val accumulator = BedrockEventAccumulator()
        val parser = AwsEventStreamParser.IncrementalParser()
        val channel = response.bodyAsChannel()
        val chunk = ByteArray(16 * 1024)
        var finished = false
        try {
            while (true) {
                val n = channel.readAvailable(chunk)
                if (n < 0) break
                if (n == 0) continue
                for (awsEvent in parser.feed(chunk, n)) {
                    for (streamEvent in accumulator.onEvent(awsEvent)) {
                        if (streamEvent is StreamEvent.Error) {
                            emit(streamEvent)
                            finished = true
                            return@flow
                        }
                        emit(streamEvent)
                    }
                }
            }
        } catch (t: Throwable) {
            if (!finished) {
                emit(StreamEvent.Error(NetworkError(t.message ?: "bedrock stream error", t)))
                return@flow
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
            if (systemText.isNotBlank()) {
                put(
                    "system",
                    buildJsonArray { add(buildJsonObject { put("text", systemText) }) },
                )
            }
            put("messages", buildJsonArray {
                for (message in request.messages) {
                    if (message.role == RequestRole.SYSTEM) continue
                    when (message.role) {
                        RequestRole.USER -> add(
                            buildJsonObject {
                                put("role", "user")
                                put("content", buildContent(message))
                            },
                        )
                        RequestRole.ASSISTANT -> add(
                            buildJsonObject {
                                put("role", "assistant")
                                put("content", buildContent(message))
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
            val inferenceConfig = buildJsonObject {
                request.maxTokens?.let { put("maxTokens", it) }
                request.temperature?.let { put("temperature", it) }
                request.topP?.let { put("topP", it) }
            }
            if (inferenceConfig.isNotEmpty()) put("inferenceConfig", inferenceConfig)

            // 推理档位 → Claude thinking 方言
            when (request.reasoningLevel) {
                ReasoningDialects.LEVEL_LOW, ReasoningDialects.LEVEL_MEDIUM, ReasoningDialects.LEVEL_HIGH -> {
                    val budget = when (request.reasoningLevel) {
                        ReasoningDialects.LEVEL_LOW -> 1024L
                        ReasoningDialects.LEVEL_MEDIUM -> 4096L
                        else -> 8192L
                    }
                    // thinking budget 必须小于 maxTokens；未设或过小时按 budget*2 放宽
                    val effectiveMax = request.maxTokens?.takeIf { it > budget } ?: (budget * 2)
                    put("additionalModelRequestFields", buildJsonObject {
                        put("thinking", buildJsonObject {
                            put("type", "enabled")
                            put("budget_tokens", minOf(budget, effectiveMax / 2).coerceAtLeast(1024))
                        })
                    })
                }
            }

            if (request.tools.isNotEmpty()) {
                putJsonObject("toolConfig") {
                    put("tools", buildJsonArray {
                        request.tools.forEach { tool ->
                            add(
                                buildJsonObject {
                                    putJsonObject("toolSpec") {
                                        put("name", tool.name)
                                        put("description", tool.description)
                                        put("inputSchema", buildJsonObject {
                                            put("json", Json.parseToJsonElement(tool.parametersJsonSchema))
                                        })
                                    }
                                },
                            )
                        }
                    })
                }
            }
        }
        return json.toString()
    }

    private fun buildContent(message: RequestMessage) = buildJsonArray {
        message.parts.forEach { part ->
            when (part) {
                is RequestPart.Text -> add(buildJsonObject { put("text", part.text) })
                is RequestPart.Image -> {
                    val format = part.mediaType.substringAfter('/', "").lowercase()
                    if (format in setOf("png", "jpeg", "gif", "webp")) {
                        add(
                            buildJsonObject {
                                putJsonObject("image") {
                                    put("format", format)
                                    putJsonObject("source") { put("bytes", part.base64) }
                                }
                            },
                        )
                    }
                }
            }
        }
        message.toolCalls.forEach { call ->
            add(
                buildJsonObject {
                    putJsonObject("toolUse") {
                        put("toolUseId", call.toolCallId)
                        put("name", call.name)
                        runCatching { Json.parseToJsonElement(call.arguments) }.getOrNull()
                            ?.let { put("input", it) }
                    }
                },
            )
        }
    }

    /** ListFoundationModels（控制面，POST /models）。 */
    override suspend fun listModels(): List<ProviderModelInfo> {
        return try {
            val amzDate = amzTimestamp()
            val path = "/models"
            val bodyBytes = "{}".toByteArray(Charsets.UTF_8)
            val signed = SigV4Signer.sign(
                method = "POST",
                host = controlHost,
                path = path,
                query = "",
                bodyBytes = bodyBytes,
                accessKeyId = accessKeyId!!,
                secretAccessKey = secretAccessKey!!,
                sessionToken = sessionToken,
                region = region,
                service = "bedrock",
                amzDate = amzDate,
                additionalHeaders = mapOf("content-type" to "application/json"),
            )
            val response = httpClient.client.request {
                method = HttpMethod.Post
                url { takeFrom("https://$controlHost$path") }
                contentType(ContentType.Application.Json)
                header("x-amz-date", signed.amzDate)
                signed.securityTokenHeader.forEach { (k, v) -> header(k, v) }
                header("Authorization", signed.authorization)
                setBody(bodyBytes)
            }
            if (!response.status.isSuccess()) return emptyList()
            val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
            body.optArray("modelSummaries")?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val modelId = obj.optString("modelId") ?: return@mapNotNull null
                val outputs = obj.optArray("outputModalities")?.mapNotNull { (it as? JsonObject)?.toString() }
                // 只保留文本输出模型（排除 embedding / image 等）
                if (outputs != null && outputs.none { it.contains("TEXT") }) return@mapNotNull null
                ProviderModelInfo(
                    modelId = modelId,
                    providerId = this@BedrockProvider.id,
                    type = "chat",
                    nickname = obj.optString("modelName"),
                )
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun amzTimestamp(): String {
        val fmt = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }
}

/**
 * 纯函数累加器：Converse Stream 事件 → StreamEvent。
 * 事件头：:message-type = event|exception；:event-type = messageStart / contentBlockDelta / …
 */
internal class BedrockEventAccumulator {
    private var inputTokens: Long? = null
    private var outputTokens: Long? = null
    private var finishSent = false
    private val toolIndices = mutableMapOf<Int, Boolean>() // contentBlock index -> isToolUse

    fun onEvent(awsEvent: AwsEventStreamParser.AwsEvent): List<StreamEvent> {
        val messageType = awsEvent.headers[":message-type"]
        if (messageType == "exception") {
            val exceptionType = awsEvent.headers[":exception-type"] ?: "exception"
            val payload = runCatching {
                Json.parseToJsonElement(awsEvent.payloadText).jsonObject
            }.getOrNull()
            return listOf(
                StreamEvent.Error(
                    com.aibox.kotlin.core.network.ApiError(
                        statusCode = null,
                        message = payload?.optString("message") ?: exceptionType,
                    ),
                ),
            )
        }

        val json = runCatching {
            Json.parseToJsonElement(awsEvent.payloadText.ifBlank { "{}" }).jsonObject
        }.getOrNull() ?: return emptyList()
        val events = mutableListOf<StreamEvent>()

        when (awsEvent.headers[":event-type"]) {
            "messageStart" -> Unit
            "contentBlockStart" -> {
                val index = json.optInt("index") ?: return emptyList()
                val start = json.optObject("start") ?: return emptyList()
                if (start.optObject("toolUse") != null) {
                    toolIndices[index] = true
                    events.add(
                        StreamEvent.ToolCallStart(
                            index = index,
                            toolCallId = start.optObject("toolUse")?.optString("toolUseId") ?: "",
                            name = start.optObject("toolUse")?.optString("name") ?: "",
                        ),
                    )
                }
            }
            "contentBlockDelta" -> {
                val index = json.optInt("index") ?: return emptyList()
                val delta = json.optObject("delta") ?: return emptyList()
                delta.optString("text")?.takeIf { it.isNotEmpty() }
                    ?.let { events.add(StreamEvent.TextDelta(it)) }
                delta.optObject("reasoningContent")?.optString("text")?.takeIf { it.isNotBlank() }
                    ?.let { events.add(StreamEvent.ReasoningDelta(it)) }
                delta.optObject("toolUse")?.optString("input")?.takeIf { it.isNotEmpty() }
                    ?.let { events.add(StreamEvent.ToolCallArgsDelta(index, it)) }
            }
            "contentBlockStop" -> {
                val index = json.optInt("index") ?: return emptyList()
                if (toolIndices.remove(index) == true) {
                    events.add(StreamEvent.ToolCallEnd(index))
                }
            }
            "messageDelta" -> {
                json.optObject("usage")?.optLong("outputTokens")?.let { outputTokens = it }
                val stop = json.optObject("delta")?.optString("stopReason")
                if (stop != null && !finishSent) {
                    finishSent = true
                    events.add(StreamEvent.Finish(mapStopReason(stop)))
                }
            }
            "messageStop" -> {
                if (!finishSent) {
                    finishSent = true
                    events.add(StreamEvent.Finish("stop"))
                }
            }
            "metadata" -> {
                val usage = json.optObject("usage")
                inputTokens = usage?.optLong("inputTokens") ?: inputTokens
                outputTokens = usage?.optLong("outputTokens") ?: outputTokens
                val total = usage?.optLong("totalTokens")
                events.add(
                    StreamEvent.Usage(
                        TokenUsage(
                            inputTokens = inputTokens,
                            outputTokens = outputTokens,
                            totalTokens = total ?: ((inputTokens ?: 0) + (outputTokens ?: 0)),
                        ),
                    ),
                )
            }
        }
        return events
    }

    fun onDone(): List<StreamEvent> {
        val events = mutableListOf<StreamEvent>()
        toolIndices.keys.sorted().forEach { events.add(StreamEvent.ToolCallEnd(it)) }
        toolIndices.clear()
        if (!finishSent) {
            finishSent = true
            events.add(StreamEvent.Finish("stop"))
        }
        return events
    }

    private fun mapStopReason(reason: String): String = when (reason) {
        "end_turn", "stop_sequence" -> "stop"
        "max_tokens" -> "length"
        "tool_use" -> "tool-calls"
        "content_filter", "guardrail_intervened" -> "content-filter"
        else -> reason
    }
}

/** Bedrock 常用模型（未拉取远端列表时的注册表默认值）。 */
val BEDROCK_DEFAULT_MODELS = listOf(
    ProviderModelInfo(
        modelId = "anthropic.claude-sonnet-4-20250514-v1:0",
        type = "chat",
        capabilities = listOf(ModelCapabilityReasoning),
        contextWindow = 200_000,
    ),
    ProviderModelInfo(
        modelId = "anthropic.claude-3-7-sonnet-20250219-v1:0",
        type = "chat",
        capabilities = listOf(ModelCapabilityReasoning),
        contextWindow = 200_000,
    ),
    ProviderModelInfo(
        modelId = "anthropic.claude-3-5-haiku-20241022-v1:0",
        type = "chat",
        contextWindow = 200_000,
    ),
    ProviderModelInfo(
        modelId = "us.anthropic.claude-sonnet-4-20250514-v1:0",
        type = "chat",
        capabilities = listOf(ModelCapabilityReasoning),
        contextWindow = 200_000,
    ),
)

private const val ModelCapabilityReasoning = "reasoning"
