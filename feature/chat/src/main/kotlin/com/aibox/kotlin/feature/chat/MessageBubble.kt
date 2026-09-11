package com.aibox.kotlin.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aibox.kotlin.core.common.appString
import com.aibox.kotlin.core.conversation.ForkNavInfo
import com.aibox.kotlin.core.model.Message
import com.aibox.kotlin.core.model.MessageContentPart
import com.aibox.kotlin.core.model.MessageRole
import com.aibox.kotlin.core.model.ToolCallState
import com.aibox.kotlin.feature.chat.attachment.AttachmentBundle
import com.aibox.kotlin.feature.chat.attachment.MessageAttachmentGrid
import java.io.File

/**
 * 单条消息渲染（T04）。
 *
 * - 用户：右对齐主色气泡（正文/附件）+ 分支导航 + 编辑；
 * - 助手：左对齐全宽（Markdown 正文 / 推理折叠 / 工具块 / 附件网格 / 复制 / 重新生成 / 继续）；
 * - 元信息（模型名 / 字数 / Token / 延迟 / 时间）按 [MessageDisplayOptions] 各开关**独立**显示；
 * - 图片以缩略图网格呈现，点击回调 [onImageClick] 由上层打开全屏查看器。
 *
 * 依赖注入：`display` 由上层聚合自 Settings，`resolveBlob` 由上层从 BlobStore 注入，
 * 本组件**不直接依赖** `SettingsRepository` / `BlobStore` 具体实现。
 */
@Composable
fun MessageBubble(
    message: Message,
    modifier: Modifier = Modifier,
    isLastMessage: Boolean = false,
    generating: Boolean = false,
    forkNav: ForkNavInfo? = null,
    display: MessageDisplayOptions = MessageDisplayOptions.Default,
    resolveBlob: suspend (String) -> File? = { null },
    onImageClick: (List<String>, Int) -> Unit = { _, _ -> },
    onEditRequest: (Message) -> Unit = {},
    onRegenerateAt: (Message) -> Unit = {},
    onContinue: () -> Unit = {},
    onSwitchFork: (Int) -> Unit = {},
) {
    val isUser = message.role == MessageRole.USER
    val bundle = remember(message) {
        AttachmentBundle(
            images = message.contentParts.filterIsInstance<MessageContentPart.Image>(),
            files = message.files,
            links = message.links,
        )
    }
    val metaFields = remember(message, display) { MessageMetadata.fields(message, display) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        if (isUser) {
            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.widthIn(max = 300.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        message.contentParts.forEach { part ->
                            if (part is MessageContentPart.Text) {
                                Text(
                                    text = part.text,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }

                if (!bundle.isEmpty) {
                    MessageAttachmentGrid(
                        bundle = bundle,
                        resolveBlob = resolveBlob,
                        onImageClick = onImageClick,
                        modifier = Modifier.widthIn(max = 300.dp),
                    )
                }

                MetaLine(fields = metaFields, alignment = Alignment.End)

                if (!generating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        forkNav?.let { nav ->
                            ForkNavButtons(nav = nav, onSwitch = onSwitchFork)
                        }
                        IconButton(
                            onClick = { onEditRequest(message) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = appString("chat.edit"),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            AssistantMessageBody(
                message = message,
                bundle = bundle,
                display = display,
                metaFields = metaFields,
                isLastMessage = isLastMessage,
                generating = generating,
                resolveBlob = resolveBlob,
                onImageClick = onImageClick,
                onRegenerateAt = { onRegenerateAt(message) },
                onContinue = onContinue,
            )
        }
    }
}

@Composable
private fun AssistantMessageBody(
    message: Message,
    bundle: AttachmentBundle,
    display: MessageDisplayOptions,
    metaFields: List<String>,
    isLastMessage: Boolean,
    generating: Boolean,
    resolveBlob: suspend (String) -> File?,
    onImageClick: (List<String>, Int) -> Unit,
    onRegenerateAt: () -> Unit,
    onContinue: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        MetaLine(fields = metaFields, alignment = Alignment.Start)

        if (message.isSummary == true) {
            SummaryBlock(message)
            return@Column
        }

        message.contentParts.forEach { part ->
            when (part) {
                is MessageContentPart.Reasoning -> ReasoningBlock(part)
                is MessageContentPart.Text -> MarkdownText(
                    text = part.text,
                    markdownEnabled = display.markdownEnabled,
                )
                is MessageContentPart.ToolCall -> ToolCallBlock(part)
                // 图片统一由附件网格渲染（缩略图 + 全屏）
                else -> Unit
            }
        }

        if (!bundle.isEmpty) {
            MessageAttachmentGrid(
                bundle = bundle,
                resolveBlob = resolveBlob,
                onImageClick = onImageClick,
            )
        }

        message.error?.let { error ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        // 生成中的光标提示
        if (message.isStreamingMode == true) {
            Text(
                text = "▍",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (!generating && message.isStreamingMode != true) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp),
            ) {
                IconButton(
                    onClick = {
                        clipboard.setText(
                            AnnotatedString(
                                message.contentParts.filterIsInstance<MessageContentPart.Text>()
                                    .joinToString("\n") { it.text },
                            ),
                        )
                        copied = true
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = appString("chat.copy"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (copied) {
                    Text(
                        text = appString("chat.copied"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = onRegenerateAt,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = appString("chat.regenerate"),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isLastMessage && message.error == null) {
                    TextButton(
                        text = appString("chat.continue_generation"),
                        onClick = onContinue,
                    )
                }
            }
        }
    }
}

/** 元信息行（开关驱动，任一为空则不渲染）。 */
@Composable
private fun MetaLine(fields: List<String>, alignment: Alignment.Horizontal) {
    if (fields.isEmpty()) return
    Text(
        text = fields.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (alignment == Alignment.End) TextAlign.End else TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    )
}

/** 轻量文本按钮（复用 labelSmall 风格）。 */
@Composable
private fun TextButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = 32.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** 分支导航：‹ 2/3 ›。 */
@Composable
fun ForkNavButtons(
    nav: ForkNavInfo,
    onSwitch: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { if (nav.hasPrev) onSwitch(nav.position - 1) },
            enabled = nav.hasPrev,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Default.ChevronLeft,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (nav.hasPrev) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
        Text(
            text = "${nav.position + 1}/${nav.count}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(
            onClick = { if (nav.hasNext) onSwitch(nav.position + 1) },
            enabled = nav.hasNext,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (nav.hasNext) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
    }
}

/** 压缩摘要消息：折叠样式呈现，不参与操作行。 */
@Composable
private fun SummaryBlock(message: Message) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = appString("chat.summary"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = message.contentParts.filterIsInstance<MessageContentPart.Text>()
                    .joinToString("\n") { it.text },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 8,
            )
        }
    }
}

@Composable
private fun ReasoningBlock(part: MessageContentPart.Reasoning) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                text = appString("chat.thinking"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = part.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun ToolCallBlock(part: MessageContentPart.ToolCall) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (part.state) {
                    ToolCallState.RESULT -> "✓ "
                    ToolCallState.ERROR -> "✗ "
                    else -> "⚙ "
                } + part.toolName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
