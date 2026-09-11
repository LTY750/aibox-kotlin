package com.aibox.kotlin.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MessageRole {
    @SerialName("system") SYSTEM,
    @SerialName("user") USER,
    @SerialName("assistant") ASSISTANT,
    @SerialName("tool") TOOL,
}

/** tool-call 内容块的状态，持久化值为小写字符串。 */
@Serializable
enum class ToolCallState {
    @SerialName("call") CALL,
    @SerialName("result") RESULT,
    @SerialName("error") ERROR,
    @SerialName("paused") PAUSED,
}

@Serializable
data class TokenUsage(
    @SerialName("inputTokens") val inputTokens: Long? = null,
    @SerialName("outputTokens") val outputTokens: Long? = null,
    @SerialName("totalTokens") val totalTokens: Long? = null,
    @SerialName("reasoningTokens") val reasoningTokens: Long? = null,
    @SerialName("cachedInputTokens") val cachedInputTokens: Long? = null,
)
