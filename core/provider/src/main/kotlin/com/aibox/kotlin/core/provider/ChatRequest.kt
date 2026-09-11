package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.model.ProviderModelInfo

/**
 * 统一的协议无关聊天请求。
 * 由 core:conversation 的上下文构建器从 Session/Message 组装，
 * 再由各协议族 Provider 翻译为具体线格式。
 */
data class ChatRequest(
    val model: String,
    val messages: List<RequestMessage>,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Long? = null,
    val tools: List<ToolDefinition> = emptyList(),
    /** 推理强度："off" | "low" | "medium" | "high"；null = 模型默认（不发送方言参数）。 */
    val reasoningLevel: String? = null,
)

/** 请求消息角色（协议无关）。 */
enum class RequestRole { SYSTEM, USER, ASSISTANT, TOOL }

/** 请求消息内容块。 */
sealed class RequestPart {
    data class Text(val text: String) : RequestPart()

    /** base64（无 data-url 前缀）+ MIME 类型。 */
    data class Image(val base64: String, val mediaType: String) : RequestPart()
}

data class RequestMessage(
    val role: RequestRole,
    val parts: List<RequestPart> = emptyList(),
    /** ASSISTANT 角色历史中的工具调用（多轮工具使用的中间轮次）。 */
    val toolCalls: List<ToolCallData> = emptyList(),
    /** TOOL 角色结果消息对应的调用 id。 */
    val toolCallId: String? = null,
)

data class ToolCallData(
    val toolCallId: String,
    val name: String,
    /** 已完成的参数 JSON 字符串。 */
    val arguments: String,
)

data class ToolDefinition(
    val name: String,
    val description: String,
    /** JSON Schema 字符串。 */
    val parametersJsonSchema: String,
)
