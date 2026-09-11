package com.aibox.kotlin.core.conversation

import com.aibox.kotlin.core.model.Message
import com.aibox.kotlin.core.model.Session

/** 聊天生成过程中的对外事件，UI 层据此增量渲染。 */
sealed class ChatGenerationEvent {
    /** 用户消息已写入、会话已持久化（新会话在此事件获得真实 id）。 */
    data class SessionSaved(val session: Session) : ChatGenerationEvent()

    /** 助手占位消息已创建。 */
    data class AssistantStarted(val message: Message) : ChatGenerationEvent()

    /** 助手消息增量更新（流式内容）。 */
    data class AssistantUpdated(val message: Message) : ChatGenerationEvent()

    /** 服务端错误后自动重试中（内容未流出前的 429/5xx）。 */
    data class Retrying(val attempt: Int, val maxAttempts: Int) : ChatGenerationEvent()

    /** 生成完成，最终消息已持久化。 */
    data class Completed(val session: Session, val message: Message) : ChatGenerationEvent()

    /** 生成失败，失败信息已写入消息并持久化。 */
    data class Failed(val session: Session, val message: Message, val error: Throwable) : ChatGenerationEvent()
}

class NoModelConfiguredException : Exception("No model configured")
