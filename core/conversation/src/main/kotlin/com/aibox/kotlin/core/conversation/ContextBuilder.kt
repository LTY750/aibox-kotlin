package com.aibox.kotlin.core.conversation

import com.aibox.kotlin.core.model.Message
import com.aibox.kotlin.core.model.MessageContentPart
import com.aibox.kotlin.core.model.MessageRole
import com.aibox.kotlin.core.model.CompactionPoint
import com.aibox.kotlin.core.provider.RequestMessage
import com.aibox.kotlin.core.provider.RequestPart
import com.aibox.kotlin.core.provider.RequestRole
import com.aibox.kotlin.core.provider.ToolCallData
import com.aibox.kotlin.core.storage.BlobStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 上下文构建器：Session.messages → 协议无关请求消息。
 *
 * 规则（对齐旧版 model-message-converter 契约）：
 * - 系统提示词来自 settings.defaultPrompt，作为首条 SYSTEM 消息
 * - 应用最新的可用压缩点：boundary（含）之前的消息由 summary 消息替代
 * - maxContextMessageCount 截断（null = 不限制）
 * - 图片：模型支持 vision → base64 Image part；否则降级为 OCR 文本提示
 * - 助手消息的 tool-call 部分转为 toolCalls 历史（参数以 JSON 字符串传递）
 */
@Singleton
class ContextBuilder @Inject constructor(
    private val blobStore: BlobStore,
) {

    suspend fun build(
        messages: List<Message>,
        compactionPoints: List<CompactionPoint>,
        systemPrompt: String?,
        maxContextMessageCount: Long?,
        supportsVision: Boolean,
    ): List<RequestMessage> {
        var effective = applyCompaction(messages, compactionPoints)
        if (maxContextMessageCount != null && maxContextMessageCount > 0 &&
            effective.size > maxContextMessageCount
        ) {
            effective = effective.takeLast(maxContextMessageCount.toInt())
        }

        val result = mutableListOf<RequestMessage>()
        if (!systemPrompt.isNullOrBlank()) {
            result.add(RequestMessage(role = RequestRole.SYSTEM, parts = listOf(RequestPart.Text(systemPrompt))))
        }
        for (message in effective) {
            result.addAll(mapMessage(message, supportsVision))
        }
        return result
    }

    /** 最新可用压缩点：boundary 与 summary 消息都必须在当前路径上；boundary 前的 system 消息保留到最前。 */
    internal fun applyCompaction(messages: List<Message>, points: List<CompactionPoint>): List<Message> {
        val applicable = points.lastOrNull { point ->
            messages.any { it.id == point.boundaryMessageId } &&
                messages.any { it.id == point.summaryMessageId }
        } ?: return messages
        val summaryIndex = messages.indexOfFirst { it.id == applicable.summaryMessageId }
        if (summaryIndex < 0) return messages
        // 压缩丢弃范围内的 system 消息属于会话级提示词，提前保留
        val preservedSystem = messages.subList(0, summaryIndex).filter { it.role == MessageRole.SYSTEM }
        return preservedSystem + messages.subList(summaryIndex, messages.size)
    }

    private suspend fun mapMessage(message: Message, supportsVision: Boolean): List<RequestMessage> {
        return when (message.role) {
            MessageRole.SYSTEM -> {
                val text = message.contentParts.filterIsInstance<MessageContentPart.Text>().joinToString("\n") { it.text }
                if (text.isNotBlank()) listOf(RequestMessage(RequestRole.SYSTEM, listOf(RequestPart.Text(text)))) else emptyList()
            }
            MessageRole.USER -> listOf(mapUserMessage(message, supportsVision))
            MessageRole.ASSISTANT -> mapAssistantMessage(message)
            MessageRole.TOOL -> emptyList() // 工具结果内嵌于 assistant 的 tool-call 部分历史
        }
    }

    private suspend fun mapUserMessage(message: Message, supportsVision: Boolean): RequestMessage {
        val parts = mutableListOf<RequestPart>()
        for (content in message.contentParts) {
            when (content) {
                is MessageContentPart.Text -> parts.add(RequestPart.Text(content.text))
                is MessageContentPart.Image -> {
                    val imagePart = resolveImage(content, supportsVision)
                    if (imagePart != null) parts.add(imagePart)
                }
                else -> Unit
            }
        }
        // 附件：文本类内容以内联方式带入，其他以名称占位
        for (file in message.files) {
            if (file.fileType.startsWith("image/")) continue // 图片已通过 Image part 处理
            val inline = inlineTextFile(file)
            parts.add(
                RequestPart.Text(
                    if (inline != null) "[File: ${file.name}]\n$inline" else "[File: ${file.name}]",
                ),
            )
        }
        for (link in message.links) {
            parts.add(RequestPart.Text("[Link: ${link.title}](${link.url})"))
        }
        return RequestMessage(role = RequestRole.USER, parts = parts)
    }

    private suspend fun resolveImage(
        image: MessageContentPart.Image,
        supportsVision: Boolean,
    ): RequestPart? {
        if (supportsVision && image.storageKey.isNotBlank()) {
            val dataUrl = blobStore.readAsDataUrl(image.storageKey)
            if (dataUrl != null) {
                val marker = "base64,"
                val idx = dataUrl.indexOf(marker)
                if (idx > 0) {
                    val mediaType = dataUrl.substring(5, idx - 1).ifBlank { "image/png" }
                    return RequestPart.Image(
                        base64 = dataUrl.substring(idx + marker.length),
                        mediaType = mediaType,
                    )
                }
            }
        }
        // 无 vision 能力或读取失败：降级为 OCR/占位文本
        val ocr = image.ocrResult
        return RequestPart.Text(
            if (!ocr.isNullOrBlank()) "This is an image, OCR Result: \n$ocr" else "This is an image.",
        )
    }

    private suspend fun inlineTextFile(file: com.aibox.kotlin.core.model.MessageFile): String? {
        val key = file.storageKey ?: file.rawStorageKey ?: return null
        if ((file.byteLength ?: 0) > MAX_INLINE_FILE_BYTES) return null
        val isTextLike = file.fileType.startsWith("text/") ||
            file.fileType.contains("json") ||
            file.fileType.contains("markdown")
        if (!isTextLike) return null
        val bytes = blobStore.read(key) ?: return null
        return String(bytes, Charsets.UTF_8).take(MAX_INLINE_FILE_BYTES.toInt())
    }

    private fun mapAssistantMessage(message: Message): List<RequestMessage> {
        val text = message.contentParts.filterIsInstance<MessageContentPart.Text>()
            .joinToString("\n") { it.text }
        val toolCalls = message.contentParts.filterIsInstance<MessageContentPart.ToolCall>()
            .map { ToolCallData(it.toolCallId, it.toolName, it.args?.toString() ?: "{}") }
        if (text.isBlank() && toolCalls.isEmpty()) return emptyList()
        return listOf(
            RequestMessage(
                role = RequestRole.ASSISTANT,
                parts = if (text.isBlank()) emptyList() else listOf(RequestPart.Text(text)),
                toolCalls = toolCalls,
            ),
        )
    }

    companion object {
        /** 内联文本附件上限，超过则仅传文件名占位。 */
        const val MAX_INLINE_FILE_BYTES: Long = 64 * 1024
    }
}
