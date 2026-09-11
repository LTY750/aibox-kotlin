package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.model.ApiStyle
import com.aibox.kotlin.core.model.ProviderModelInfo
import kotlinx.coroutines.flow.Flow

/**
 * AI Provider 抽象。实现按协议族划分（OpenAI Compatible / Anthropic / Gemini），
 * 每个实现负责：请求体构建、鉴权头、SSE 解析、usage 提取与错误分类。
 */
interface AiProvider {
    val id: String

    /** 协议族。 */
    val apiStyle: ApiStyle

    /**
     * 发起流式聊天。调用方通过取消收集 Flow 取消请求。
     * 实现必须遵守重试契约（见 RetryPolicy）：内容未流出前 429/5xx 自动重试，
     * 已流出后抛 MidStreamApiError 且绝不重试。
     */
    fun streamChat(request: ChatRequest): Flow<StreamEvent>

    /** 拉取远端模型列表（GET /models 等）。 */
    suspend fun listModels(): List<ProviderModelInfo>
}

/** 内置 Provider 的静态注册信息。 */
data class BuiltinProviderDefinition(
    val id: String,
    val name: String,
    /** 协议族。 */
    val type: String,
    val defaultApiHost: String,
    val defaultModels: List<ProviderModelInfo> = emptyList(),
)

/**
 * 重试契约（与旧版 ai-retry 语义一致）：
 * - 最多 5 次尝试，初始延迟 1000ms，指数退避 ×2
 * - 仅 429 / 5xx 且流内容未流出前重试
 * - MidStreamApiError 与取消异常绝不重试
 */
object RetryPolicy {
    const val MAX_ATTEMPTS = 5
    const val INITIAL_DELAY_MS = 1000L
    const val BACKOFF_FACTOR = 2.0
}
