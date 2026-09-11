package com.aibox.kotlin.feature.chat

import androidx.compose.runtime.Immutable
import com.aibox.kotlin.core.model.Settings

/**
 * 消息渲染所需的最小显示选项（T04）。
 *
 * 由 `Settings` 聚合而来，作为**唯一**渲染入参下传给 `MessageBubble`，
 * 使 UI 层不直接依赖 `SettingsRepository`（可组合、可预览、可单测）。
 * 仅供 Compose 读取，故标注 [Immutable] 以便跳过重组。
 */
@Immutable
data class MessageDisplayOptions(
    /** 渲染 Markdown（`Settings.enableMarkdownRendering`，默认开）。 */
    val markdownEnabled: Boolean = true,
    val showWordCount: Boolean = false,
    val showTokenCount: Boolean = false,
    val showUsedToken: Boolean = false,
    val showModelName: Boolean = false,
    val showMessageTimestamp: Boolean = false,
    val showFirstTokenLatency: Boolean = false,
    /** 头像占位开关（保留语义，当前以角色前缀体现）。 */
    val showAvatar: Boolean = true,
) {
    /** 是否至少开启一项元信息。 */
    val showAnyMeta: Boolean
        get() = showWordCount || showTokenCount || showUsedToken ||
            showModelName || showMessageTimestamp || showFirstTokenLatency

    companion object {
        /**
         * 由 [Settings] 聚合。旧版字段为可空布尔，语义：`null` = 未设置。
         * Markdown 与头像默认开启；其余默认关闭（与旧版克制的信息密度一致）。
         */
        fun from(settings: Settings): MessageDisplayOptions = MessageDisplayOptions(
            markdownEnabled = settings.enableMarkdownRendering != false,
            showWordCount = settings.showWordCount == true,
            showTokenCount = settings.showTokenCount == true,
            showUsedToken = settings.showUsedToken == true,
            showModelName = settings.showModelName == true,
            showMessageTimestamp = settings.showMessageTimestamp == true,
            showFirstTokenLatency = settings.showFirstTokenLatency == true,
            showAvatar = settings.showAvatar != false,
        )

        /** 默认（无 Settings）选项，供预览与独立使用。 */
        val Default: MessageDisplayOptions = MessageDisplayOptions()
    }
}
