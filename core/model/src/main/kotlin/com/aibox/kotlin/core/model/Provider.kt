package com.aibox.kotlin.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 会话级设置：全局参数 + 本会话覆盖项。 */
@Serializable
data class SessionSettings(
    val provider: String? = null,
    val modelId: String? = null,
    val maxContextMessageCount: Long? = null,
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Long? = null,
    val stream: Boolean? = null,
    val autoCompaction: Boolean? = null,
    /** 推理强度："off" | "low" | "medium" | "high"；null = 跟随模型默认。 */
    val reasoningLevel: String? = null,
)

/** 默认模型引用。 */
@Serializable
data class ModelRef(
    val provider: String,
    val model: String,
)

/** 模型能力标签（持久化为小写字符串数组）。 */
object ModelCapability {
    const val VISION = "vision"
    const val REASONING = "reasoning"
    const val TOOL_USE = "tool_use"
    const val WEB_SEARCH = "web_search"
}

/** 协议族，对应旧版 apiStyle。 */
@Serializable
enum class ApiStyle {
    @SerialName("google") GOOGLE,
    @SerialName("openai") OPENAI,
    @SerialName("openai-responses") OPENAI_RESPONSES,
    @SerialName("anthropic") ANTHROPIC,
}

@Serializable
data class ProviderModelInfo(
    val modelId: String,
    /** 运行时由解析器打上，不持久化依赖。 */
    val providerId: String? = null,
    /** "chat" | "embedding" | "rerank" | "image"。 */
    val type: String? = null,
    val apiStyle: ApiStyle? = null,
    val nickname: String? = null,
    val labels: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val contextWindow: Long? = null,
    val maxOutput: Long? = null,
) {
    fun supports(capability: String): Boolean = capabilities.contains(capability)
}

/** settings.providers[providerId] 的条目。 */
@Serializable
data class ProviderSettings(
    val apiKey: String? = null,
    val apiHost: String? = null,
    val apiPath: String? = null,
    val models: List<ProviderModelInfo> = emptyList(),
    val excludedModels: List<String> = emptyList(),
    // Bedrock 专用（apiKey 存 AccessKeyId）
    val region: String? = null,
    val secretAccessKey: String? = null,
    val sessionToken: String? = null,
)

/** settings.customProviders[] 的条目。 */
@Serializable
data class CustomProvider(
    val id: String,
    val name: String,
    /** 协议族："openai" | "gemini" | "claude" | "openai-responses"。 */
    val type: String,
    val isCustom: Boolean = true,
    val iconUrl: String? = null,
    val description: String? = null,
    val defaultSettings: ProviderSettings? = null,
)
