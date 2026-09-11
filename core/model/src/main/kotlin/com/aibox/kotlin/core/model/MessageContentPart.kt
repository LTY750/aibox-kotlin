package com.aibox.kotlin.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * 消息内容块。与旧版 contentParts 的 discriminated union 对齐，
 * 判别字段为 "type"（kotlinx.serialization 密封类默认判别器）。
 *
 * 标注 `@Immutable`：静态不可变模型，供 Compose 编译器判定「可跳过」，
 * 支撑 T04/D2「流式仅重组末尾项」。
 */
@Immutable
@Serializable
sealed class MessageContentPart {

    @Immutable
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String = "",
    ) : MessageContentPart()

    @Immutable
    @Serializable
    @SerialName("image")
    data class Image(
        val storageKey: String = "",
        val ocrResult: String? = null,
    ) : MessageContentPart()

    @Immutable
    @Serializable
    @SerialName("info")
    data class Info(
        val text: String = "",
    ) : MessageContentPart()

    @Immutable
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(
        val text: String = "",
        val startTime: Long? = null,
        val duration: Long? = null,
    ) : MessageContentPart()

    @Immutable
    @Serializable
    @SerialName("tool-call")
    data class ToolCall(
        val state: ToolCallState = ToolCallState.CALL,
        val toolCallId: String = "",
        val toolName: String = "",
        /** 工具调用参数，原样 JSON。 */
        val args: JsonElement? = null,
        /** 工具执行结果，原样 JSON。 */
        val result: JsonElement? = null,
        val startTime: Long? = null,
        val duration: Long? = null,
        val resultStorageKey: String? = null,
    ) : MessageContentPart()
}
