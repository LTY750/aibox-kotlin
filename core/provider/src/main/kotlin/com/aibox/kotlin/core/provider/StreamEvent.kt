package com.aibox.kotlin.core.provider

import com.aibox.kotlin.core.model.TokenUsage

/**
 * 统一流式事件。各协议族 SSE 帧统一折叠为该模型，
 * 会话层据此增量更新消息内容块。
 */
sealed class StreamEvent {
    /** 正文文本增量。 */
    data class TextDelta(val text: String) : StreamEvent()

    /** 思考/推理内容增量。 */
    data class ReasoningDelta(val text: String) : StreamEvent()

    /** 工具调用开始（收到 id 与名称）。 */
    data class ToolCallStart(val index: Int, val toolCallId: String, val name: String) : StreamEvent()

    /** 工具调用参数增量（OpenAI/Anthropic 为分片字符串）。 */
    data class ToolCallArgsDelta(val index: Int, val argsDelta: String) : StreamEvent()

    /** 工具调用参数结束（Gemini 一次性到达时直接 Start + ArgsDelta + End）。 */
    data class ToolCallEnd(val index: Int) : StreamEvent()

    /** 图片生成结果（base64，无前缀）。 */
    data class Image(val mediaType: String, val base64: String) : StreamEvent()

    /** usage 到达（可多次，取最后一次/按协议累加）。 */
    data class Usage(val usage: TokenUsage) : StreamEvent()

    /** 流正常结束。 */
    data class Finish(val reason: String) : StreamEvent()

    /** 自动重试前通知（内容未流出前的 429/5xx）。 */
    data class Retrying(val attempt: Int, val maxAttempts: Int, val cause: Throwable) : StreamEvent()

    /** 不可恢复错误，终止流。 */
    data class Error(val error: Throwable) : StreamEvent()
}
