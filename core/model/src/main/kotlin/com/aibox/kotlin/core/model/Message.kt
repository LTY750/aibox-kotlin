package com.aibox.kotlin.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 持久化消息模型。仅包含会写入 SQLite / 备份文件的字段；
 * 生成中状态、取消句柄等运行时信息由 UI 层管理，不落盘。
 */
@Immutable
@Serializable
data class Message(
    val id: String,
    val role: MessageRole = MessageRole.USER,
    val name: String? = null,
    @SerialName("aiProvider") val aiProvider: String? = null,
    val model: String? = null,
    val files: List<MessageFile> = emptyList(),
    val links: List<MessageLink> = emptyList(),
    val contentParts: List<MessageContentPart> = emptyList(),
    @SerialName("isStreamingMode") val isStreamingMode: Boolean? = null,
    val errorCode: Int? = null,
    val error: String? = null,
    val usage: TokenUsage? = null,
    /** 输出 token 数。 */
    val tokenCount: Long? = null,
    /** 已弃用：保留以兼容旧数据读取。 */
    val tokensUsed: Long? = null,
    val timestamp: Long? = null,
    val updatedAt: Long? = null,
    val firstTokenLatency: Long? = null,
    val generationDuration: Long? = null,
    val finishReason: String? = null,
    @SerialName("isSummary") val isSummary: Boolean? = null,
    @SerialName("isForkMarker") val isForkMarker: Boolean? = null,
    val forkedFromSessionId: String? = null,
    val wordCount: Long? = null,
)
