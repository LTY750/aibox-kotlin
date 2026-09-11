package com.aibox.kotlin.feature.chat

import com.aibox.kotlin.core.common.I18n
import com.aibox.kotlin.core.model.Message
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 消息元信息构造（纯逻辑，可 JVM 单测）。
 *
 * 依据 [MessageDisplayOptions] 的开关，独立产出「模型名 / 字数 / Token 数 /
 * 已用 Token / 首 Token 延迟 / 时间戳」等标签，UI 只负责拼接展示。
 */
object MessageMetadata {

    /** 时间格式：HH:mm（跨天消息由会话时间轴体现）。 */
    private const val TIME_PATTERN = "HH:mm"

    /**
     * 依据开关产出元信息标签列表（顺序：模型 → 字数 → Token → 已用 → 延迟 → 时间）。
     * 无任何可用字段时返回空列表。
     */
    fun fields(message: Message, display: MessageDisplayOptions): List<String> {
        if (!display.showAnyMeta) return emptyList()

        val result = mutableListOf<String>()

        if (display.showModelName) {
            message.model?.takeIf { it.isNotBlank() }?.let { result += it }
        }
        if (display.showWordCount) {
            message.wordCount?.let { result += I18n.t("meta.word_count", it.toString()) }
        }
        if (display.showTokenCount) {
            (message.tokenCount ?: message.usage?.outputTokens)
                ?.let { result += I18n.t("meta.token_count", it.toString()) }
        }
        if (display.showUsedToken) {
            message.usage?.totalTokens?.let { result += I18n.t("meta.used_token", it.toString()) }
        }
        if (display.showFirstTokenLatency) {
            message.firstTokenLatency?.let { result += I18n.t("meta.first_token_latency", it.toString()) }
        }
        if (display.showMessageTimestamp) {
            message.timestamp?.let { result += formatTimestamp(it) }
        }

        return result
    }

    /** epoch 毫秒 → `HH:mm`。 */
    fun formatTimestamp(epochMillis: Long): String =
        SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date(epochMillis))
}
