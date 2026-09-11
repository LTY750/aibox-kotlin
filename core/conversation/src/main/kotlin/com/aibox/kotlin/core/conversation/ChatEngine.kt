package com.aibox.kotlin.core.conversation

import com.aibox.kotlin.core.common.TokenEstimator
import com.aibox.kotlin.core.common.newId
import com.aibox.kotlin.core.model.ApiStyle
import com.aibox.kotlin.core.model.CompactionPoint
import com.aibox.kotlin.core.model.Message
import com.aibox.kotlin.core.model.MessageContentPart
import com.aibox.kotlin.core.model.MessageRole
import com.aibox.kotlin.core.model.ModelRef
import com.aibox.kotlin.core.model.ProviderModelInfo
import com.aibox.kotlin.core.model.ProviderSettings
import com.aibox.kotlin.core.model.Session
import com.aibox.kotlin.core.model.ToolCallState
import com.aibox.kotlin.core.provider.ChatRequest
import com.aibox.kotlin.core.provider.ProviderFactory
import com.aibox.kotlin.core.provider.RequestMessage
import com.aibox.kotlin.core.provider.RequestPart
import com.aibox.kotlin.core.provider.RequestRole
import com.aibox.kotlin.core.provider.StreamEvent
import com.aibox.kotlin.core.storage.BlobStore
import com.aibox.kotlin.core.storage.SessionRepository
import com.aibox.kotlin.core.storage.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 聊天引擎：编排一次生成的完整生命周期——
 * 用户消息落库 → （可选）上下文压缩 → 构建上下文 → 流式生成（增量折叠进消息）→
 * 最终持久化 → （可选）LLM 自动命名。
 *
 * 取消语义：收集方取消 Flow 即取消请求（协程取消传播到 Ktor），
 * 已生成的部分内容会通过 NonCancellable 持久化。
 *
 * 分叉语义（对齐旧版 message-forks）：
 * - 重新生成 / 编辑重发：在分叉点创建新分支，旧回复保留在原分支，可来回切换
 * - 继续生成：不建分支，向最后一条助手消息追加内容
 */
@Singleton
class ChatEngine @Inject constructor(
    private val providerFactory: ProviderFactory,
    private val settingsRepository: SettingsRepository,
    private val sessionRepository: SessionRepository,
    private val contextBuilder: ContextBuilder,
    private val blobStore: BlobStore,
) {

    /** 发送一条用户消息并生成回复。 */
    fun send(
        session: Session,
        userText: String,
        imageStorageKeys: List<String> = emptyList(),
        files: List<com.aibox.kotlin.core.model.MessageFile> = emptyList(),
    ): Flow<ChatGenerationEvent> = flow {
        val settings = settingsRepository.snapshot()
        val modelRef = resolveModelRef(session, settings.defaultChatModel)
        val userMessage = buildUserMessage(userText, imageStorageKeys, files)

        // 草稿会话（id == "new"）在首条消息时才落库
        val baseSession = if (session.id == DRAFT_SESSION_ID) session.copy(id = newId()) else session
        val sessionWithUser = baseSession.copy(
            messages = baseSession.messages + userMessage,
            name = baseSession.name.ifBlank { deriveTitle(userText) },
        )
        sessionRepository.upsertSession(sessionWithUser)
        emit(ChatGenerationEvent.SessionSaved(sessionWithUser))

        val effective = maybeCompact(sessionWithUser, modelRef, settings)
        if (effective !== sessionWithUser) {
            emit(ChatGenerationEvent.SessionSaved(effective))
        }

        var resultSession = effective
        generate(
            session = effective,
            modelRef = modelRef,
            defaultPrompt = settings.defaultPrompt,
            maxContextMessageCount = session.settings?.maxContextMessageCount,
        ) { event ->
            if (event is ChatGenerationEvent.Completed || event is ChatGenerationEvent.Failed) {
                resultSession = (event as? ChatGenerationEvent.Completed)?.session
                    ?: (event as? ChatGenerationEvent.Failed)?.session
                    ?: effective
            }
            emit(event)
        }

        // 生成完成后尝试 LLM 自动命名（失败不影响聊天）
        maybeGenerateName(resultSession, modelRef, settings) { renamed ->
            emit(ChatGenerationEvent.SessionSaved(renamed))
        }
    }

    /**
     * 重新生成：定位目标消息（默认最后一条助手消息）→ 在其前的最近用户消息处
     * 创建分支 → 在新分支生成。旧回复保留在原分支。
     */
    fun regenerate(session: Session, targetMessageId: String? = null): Flow<ChatGenerationEvent> = flow {
        val settings = settingsRepository.snapshot()
        val modelRef = resolveModelRef(session, settings.defaultChatModel)
        val targetId = targetMessageId
            ?: session.messages.lastOrNull { it.role == MessageRole.ASSISTANT }?.id
            ?: return@flow
        val forkPoint = MessageForks.findForkPointBefore(session.messages, targetId)
        val base = if (forkPoint != null) {
            MessageForks.createFork(session, forkPoint)
        } else {
            trimTrailingAssistant(session)
        }
        sessionRepository.upsertSession(base)
        emit(ChatGenerationEvent.SessionSaved(base))

        val effective = maybeCompact(base, modelRef, settings)
        if (effective !== base) {
            emit(ChatGenerationEvent.SessionSaved(effective))
        }

        generate(
            session = effective,
            modelRef = modelRef,
            defaultPrompt = settings.defaultPrompt,
            maxContextMessageCount = session.settings?.maxContextMessageCount,
        ) { event -> emit(event) }
    }

    /**
     * 编辑用户消息并在该消息处建分支重新生成（Save & Resend 语义）。
     * 保留原消息中的图片部分，仅替换文本。
     */
    fun editAndResend(session: Session, messageId: String, newText: String): Flow<ChatGenerationEvent> = flow {
        val target = session.messages.firstOrNull { it.id == messageId } ?: return@flow
        if (target.role != MessageRole.USER) return@flow
        val settings = settingsRepository.snapshot()
        val modelRef = resolveModelRef(session, settings.defaultChatModel)

        val images = target.contentParts.filterIsInstance<MessageContentPart.Image>()
        val newParts = buildList {
            if (newText.isNotBlank()) add(MessageContentPart.Text(newText))
            addAll(images)
        }
        val editedMessages = session.messages.map {
            if (it.id == messageId) it.copy(contentParts = newParts) else it
        }
        val forked = MessageForks.createFork(session.copy(messages = editedMessages), messageId)
        sessionRepository.upsertSession(forked)
        emit(ChatGenerationEvent.SessionSaved(forked))

        val effective = maybeCompact(forked, modelRef, settings)
        if (effective !== forked) {
            emit(ChatGenerationEvent.SessionSaved(effective))
        }

        generate(
            session = effective,
            modelRef = modelRef,
            defaultPrompt = settings.defaultPrompt,
            maxContextMessageCount = session.settings?.maxContextMessageCount,
        ) { event -> emit(event) }
    }

    /** 继续生成：把上下文（含最后一条助手消息的已生成部分）重发，流式追加到该消息。 */
    fun continueGeneration(session: Session): Flow<ChatGenerationEvent> = flow {
        val last = session.messages.lastOrNull { it.isSummary != true } ?: return@flow
        if (last.role != MessageRole.ASSISTANT || last.error != null) return@flow
        val settings = settingsRepository.snapshot()
        val modelRef = resolveModelRef(session, settings.defaultChatModel)

        generate(
            session = session,
            modelRef = modelRef,
            defaultPrompt = settings.defaultPrompt,
            maxContextMessageCount = session.settings?.maxContextMessageCount,
            existing = last,
        ) { event -> emit(event) }
    }

    /**
     * 进程被杀后的恢复：把该会话中处于流式占位状态（`isStreamingMode == true`）的
     * 助手消息标记为已中断（`isStreamingMode = false`、`finishReason = "interrupted"`），
     * 并持久化。
     *
     * 说明：按会话触发，不做全库扫描（避免改动 `SessionRepository` 接口）。
     *
     * @return 是否发生过恢复，供 UI 提示"上次生成已中断"并提供重试。
     */
    suspend fun recoverInterrupted(sessionId: String): Boolean {
        val session = sessionRepository.getSession(sessionId) ?: return false
        var recovered = false
        val messages = session.messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && message.isStreamingMode == true) {
                recovered = true
                message.copy(isStreamingMode = false, finishReason = "interrupted")
            } else {
                message
            }
        }
        if (recovered) {
            sessionRepository.upsertSession(session.copy(messages = messages))
        }
        return recovered
    }

    private suspend fun generate(
        session: Session,
        modelRef: ModelRef,
        defaultPrompt: String?,
        maxContextMessageCount: Long?,
        existing: Message? = null,
        emit: suspend (ChatGenerationEvent) -> Unit,
    ) {
        val settings = settingsRepository.snapshot()
        val providerSettings = settings.providers[modelRef.provider] ?: ProviderSettings()
        val custom = settings.customProviders.firstOrNull { it.id == modelRef.provider }
        val provider = providerFactory.create(modelRef.provider, providerSettings, custom)

        val modelInfo: ProviderModelInfo? =
            (custom?.defaultSettings?.models ?: providerSettings.models).firstOrNull { it.modelId == modelRef.model }
        val supportsVision = modelInfo?.supports("vision") ?: false

        val startedAt = System.currentTimeMillis()
        var assistant = existing?.copy(
            isStreamingMode = true,
            error = null,
            errorCode = null,
        ) ?: Message(
            id = newId(),
            role = MessageRole.ASSISTANT,
            aiProvider = modelRef.provider,
            model = modelRef.model,
            timestamp = startedAt,
            isStreamingMode = true,
        )
        emit(ChatGenerationEvent.AssistantStarted(assistant))

        val folding = MessageFolding(assistant) { updated -> assistant = updated }
        var firstTokenAt: Long? = null

        val request = ChatRequest(
            model = modelRef.model,
            messages = contextBuilder.build(
                messages = session.messages,
                compactionPoints = session.compactionPoints,
                systemPrompt = defaultPrompt?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PROMPT,
                maxContextMessageCount = maxContextMessageCount,
                supportsVision = supportsVision,
            ),
            temperature = session.settings?.temperature,
            topP = session.settings?.topP,
            maxTokens = session.settings?.maxTokens,
            reasoningLevel = session.settings?.reasoningLevel,
        )

        var failure: Throwable? = null
        try {
            provider.streamChat(request).collect { event ->
                when (event) {
                    is StreamEvent.TextDelta, is StreamEvent.ReasoningDelta -> {
                        if (firstTokenAt == null) firstTokenAt = System.currentTimeMillis()
                    }
                    else -> Unit
                }
                when (event) {
                    is StreamEvent.TextDelta -> folding.appendText(event.text)
                    is StreamEvent.ReasoningDelta -> folding.appendReasoning(event.text)
                    is StreamEvent.ToolCallStart -> folding.toolCallStart(event.index, event.toolCallId, event.name)
                    is StreamEvent.ToolCallArgsDelta -> folding.toolCallArgs(event.index, event.argsDelta)
                    is StreamEvent.ToolCallEnd -> folding.toolCallEnd(event.index)
                    is StreamEvent.Image -> {
                        val key = blobStore.save(
                            android.util.Base64.decode(event.base64, android.util.Base64.DEFAULT),
                            ext = event.mediaType.substringAfter('/'),
                        )
                        folding.appendImage(key)
                    }
                    is StreamEvent.Usage -> folding.usage(event.usage)
                    is StreamEvent.Finish -> folding.finish(event.reason)
                    is StreamEvent.Retrying -> emit(ChatGenerationEvent.Retrying(event.attempt, event.maxAttempts))
                    is StreamEvent.Error -> failure = event.error
                }
                folding.updateMeta(
                    firstTokenLatency = firstTokenAt?.let { it - startedAt },
                )
                emit(ChatGenerationEvent.AssistantUpdated(assistant))
            }
        } catch (e: CancellationException) {
            // 用户主动停止：持久化部分生成内容
            val stopped = folding.snapshot().copy(
                isStreamingMode = null,
                finishReason = "cancelled",
                generationDuration = System.currentTimeMillis() - startedAt,
            )
            withContext(NonCancellable) {
                persistCompleted(session, stopped)
            }
            throw e
        }

        val duration = System.currentTimeMillis() - startedAt
        val finalMessage = folding.snapshot().copy(
            isStreamingMode = null,
            generationDuration = duration,
            firstTokenLatency = firstTokenAt?.let { it - startedAt },
        )

        val error = failure
        if (error != null) {
            val failedMessage = finalMessage.copy(error = error.message ?: error::class.simpleName)
            val saved = persistCompleted(session, failedMessage)
            emit(ChatGenerationEvent.Failed(saved, failedMessage, error))
        } else {
            val saved = persistCompleted(session, finalMessage)
            emit(ChatGenerationEvent.Completed(saved, finalMessage))
        }
    }

    // ---------------------------------------------------------------------
    // 上下文压缩（对齐旧版 compaction 契约）
    // ---------------------------------------------------------------------

    /** 输出预留 token（与旧版 OUTPUT_RESERVE_TOKENS 一致）。 */
    private val outputReserveTokens = 32_000L

    private fun compactionEnabled(session: Session, autoCompaction: Boolean?): Boolean =
        autoCompaction != false && session.settings?.autoCompaction != false

    internal fun isOverflow(
        messages: List<Message>,
        contextWindow: Long?,
        threshold: Double,
    ): Boolean {
        val window = contextWindow ?: return false
        if (window <= 0) return false
        val tokens = estimateSessionTokens(messages)
        val limit = maxOf(window - outputReserveTokens, (window * 0.5).toLong()) * threshold
        return tokens > limit
    }

    internal fun estimateSessionTokens(messages: List<Message>): Int =
        messages.sumOf { message ->
            TokenEstimator.estimateMessage(
                message.contentParts.mapNotNull { part ->
                    (part as? MessageContentPart.Text)?.text
                },
            )
        }

    /**
     * 溢出时执行压缩：让当前模型生成摘要消息（isSummary），插入到边界消息之后，
     * 记录压缩点并持久化。返回压缩后的会话；未触发时返回原实例。
     */
    private suspend fun maybeCompact(
        session: Session,
        modelRef: ModelRef,
        settings: com.aibox.kotlin.core.model.Settings,
    ): Session {
        if (!compactionEnabled(session, settings.autoCompaction)) return session
        val threshold = settings.compactionThreshold?.takeIf { it in 0.4..0.9 } ?: 0.6
        val providerSettings = settings.providers[modelRef.provider] ?: ProviderSettings()
        val custom = settings.customProviders.firstOrNull { it.id == modelRef.provider }
        val modelInfo =
            (custom?.defaultSettings?.models ?: providerSettings.models).firstOrNull { it.modelId == modelRef.model }
        if (!isOverflow(session.messages, modelInfo?.contextWindow, threshold)) return session

        val boundary = session.messages.lastOrNull { it.isSummary != true } ?: return session
        val language = languageName(settings.language)
        val prompt = buildString {
            append(
                "Summarize the conversation so far. Cover: what was discussed, key decisions made, " +
                    "work currently in progress, and next steps. Keep it concise. Write in $language.",
            )
            append("\n\nConversation:\n")
            session.messages
                .filter { it.isSummary != true && it.role != MessageRole.SYSTEM }
                .takeLast(MAX_SUMMARY_INPUT_MESSAGES)
                .forEach { message ->
                    val role = if (message.role == MessageRole.USER) "User" else "Assistant"
                    append(role).append(": ")
                    append(messageText(message).replace('\n', ' ').take(2000))
                    append('\n')
                }
        }

        val summaryText = runCatching {
            completeText(modelRef, providerSettings, custom, prompt, maxTokens = 2048)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: return session

        val summaryMessage = Message(
            id = newId(),
            role = MessageRole.ASSISTANT,
            isSummary = true,
            contentParts = listOf(MessageContentPart.Text(summaryText.trim())),
            timestamp = System.currentTimeMillis(),
        )
        val point = CompactionPoint(
            summaryMessageId = summaryMessage.id,
            boundaryMessageId = boundary.id,
            createdAt = System.currentTimeMillis(),
        )
        val compacted = session.copy(
            messages = session.messages + summaryMessage,
            compactionPoints = session.compactionPoints + point,
        )
        sessionRepository.upsertSession(compacted)
        return compacted
    }

    // ---------------------------------------------------------------------
    // LLM 自动命名（对齐旧版 prompts.nameConversation）
    // ---------------------------------------------------------------------

    private suspend fun maybeGenerateName(
        session: Session,
        modelRef: ModelRef,
        settings: com.aibox.kotlin.core.model.Settings,
        emit: suspend (Session) -> Unit,
    ) {
        if (settings.autoGenerateTitle == false) return
        val firstUser = session.messages.firstOrNull { it.role == MessageRole.USER } ?: return
        // 仅在名字仍是本地截断（未命名 / 未手动改名 / 未命名过）时触发
        val derived = deriveTitle(messageText(firstUser))
        if (session.name.isNotBlank() && session.name != derived) return

        val namingModel = settings.threadNamingModel ?: modelRef
        val providerSettings = settings.providers[namingModel.provider] ?: ProviderSettings()
        val custom = settings.customProviders.firstOrNull { it.id == namingModel.provider }

        val language = languageName(settings.language)
        val excerpt = session.messages
            .filter { it.role != MessageRole.SYSTEM && it.isSummary != true }
            .take(5)
            .joinToString("\n\n---------\n\n") { messageText(it).take(100) }
        val prompt = buildString {
            append(
                "Based on the chat history, give this conversation a name. Keep it short - " +
                    "10 words max, no quotes. Use $language. Just provide the name, nothing else.",
            )
            append("\n\n").append(excerpt)
            append("\n\nName this conversation in 10 words or less. The name is:")
        }

        val name = runCatching {
            completeText(namingModel, providerSettings, custom, prompt, maxTokens = 48)
        }.getOrNull()
            ?.let(::cleanTitle)
            ?.takeIf { it.isNotBlank() } ?: return

        val renamed = session.copy(name = name)
        runCatching {
            sessionRepository.upsertSession(renamed)
            emit(renamed)
        } // 命名失败不影响聊天主流程
    }

    private fun cleanTitle(raw: String): String = raw
        .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
        .trim()
        .trim('"', '\'', '“', '”', '‘', '’', '「', '」')
        .trim()
        .lineSequence().firstOrNull()?.take(64) ?: ""

    /** 用指定模型做一次性文本补全（复用流式通道，收集文本直到结束）。 */
    private suspend fun completeText(
        modelRef: ModelRef,
        providerSettings: ProviderSettings,
        custom: com.aibox.kotlin.core.model.CustomProvider?,
        prompt: String,
        maxTokens: Long,
    ): String? {
        val provider = providerFactory.create(modelRef.provider, providerSettings, custom)
        val request = ChatRequest(
            model = modelRef.model,
            messages = listOf(
                RequestMessage(
                    role = RequestRole.USER,
                    parts = listOf(RequestPart.Text(prompt)),
                ),
            ),
            maxTokens = maxTokens,
        )
        val builder = StringBuilder()
        provider.streamChat(request).collect { event ->
            when (event) {
                is StreamEvent.TextDelta -> builder.append(event.text)
                is StreamEvent.Error -> throw event.error
                else -> Unit
            }
        }
        return builder.toString()
    }

    private fun languageName(language: String?): String = when {
        language == null || language == "en" -> "English"
        language.startsWith("zh") -> "Simplified Chinese"
        else -> "English"
    }

    private fun messageText(message: Message): String =
        message.contentParts.filterIsInstance<MessageContentPart.Text>().joinToString("\n") { it.text }

    // ---------------------------------------------------------------------

    private suspend fun persistCompleted(session: Session, assistant: Message): Session {
        val updated = session.copy(
            messages = session.messages.filter { it.id != assistant.id } + assistant,
        )
        sessionRepository.upsertSession(updated)
        return updated
    }

    private fun resolveModelRef(session: Session, defaultModel: ModelRef?): ModelRef {
        val s = session.settings
        if (s != null) {
            val provider = s.provider
            val modelId = s.modelId
            if (!provider.isNullOrBlank() && !modelId.isNullOrBlank()) {
                return ModelRef(provider = provider, model = modelId)
            }
        }
        return defaultModel ?: throw NoModelConfiguredException()
    }

    private fun buildUserMessage(
        userText: String,
        imageStorageKeys: List<String>,
        files: List<com.aibox.kotlin.core.model.MessageFile>,
    ): Message {
        val parts = mutableListOf<MessageContentPart>()
        if (userText.isNotBlank()) parts.add(MessageContentPart.Text(userText))
        imageStorageKeys.forEach { parts.add(MessageContentPart.Image(storageKey = it)) }
        return Message(
            id = newId(),
            role = MessageRole.USER,
            timestamp = System.currentTimeMillis(),
            contentParts = parts,
            files = files,
        )
    }

    /** 去掉末尾连续的助手消息（保留用户消息作为新的生成起点）。 */
    private fun trimTrailingAssistant(session: Session): Session {
        val messages = session.messages.toMutableList()
        while (messages.isNotEmpty() && messages.last().role == MessageRole.ASSISTANT) {
            messages.removeAt(messages.size - 1)
        }
        return session.copy(messages = messages)
    }

    companion object {
        const val DRAFT_SESSION_ID = "new"
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant."
        const val MAX_SUMMARY_INPUT_MESSAGES = 40

        /** 本地截断生成会话标题；LLM 自动命名前的占位。 */
        fun deriveTitle(userText: String): String {
            val cleaned = userText.replace('\n', ' ').trim()
            return if (cleaned.length <= 24) cleaned.ifBlank { "New Chat" } else cleaned.take(24) + "…"
        }
    }
}

/**
 * 流式事件 → 消息内容块的增量折叠器。
 */
internal class MessageFolding(
    initial: Message,
    private val onSnapshot: (Message) -> Unit,
) {
    private var message = initial
    private val parts = mutableListOf<MessageContentPart>()
    private val toolCallIndex = mutableMapOf<Int, Int>() // 流 index -> parts 下标
    private val toolArgsBuffer = mutableMapOf<Int, StringBuilder>()

    init {
        parts.addAll(initial.contentParts)
    }

    fun snapshot(): Message = message.copy(contentParts = parts.toList())

    private fun push() {
        message = message.copy(contentParts = parts.toList())
        onSnapshot(message)
    }

    fun appendText(delta: String) {
        val last = parts.lastOrNull()
        if (last is MessageContentPart.Text) {
            parts[parts.size - 1] = last.copy(text = last.text + delta)
        } else {
            parts.add(MessageContentPart.Text(delta))
        }
        push()
    }

    fun appendReasoning(delta: String) {
        if (delta.isBlank()) return // 部分服务商会发送空白 reasoning 帧
        val last = parts.lastOrNull()
        if (last is MessageContentPart.Reasoning) {
            parts[parts.size - 1] = last.copy(text = last.text + delta)
        } else {
            parts.add(MessageContentPart.Reasoning(text = delta, startTime = System.currentTimeMillis()))
        }
        push()
    }

    fun appendImage(storageKey: String) {
        parts.add(MessageContentPart.Image(storageKey = storageKey))
        push()
    }

    fun toolCallStart(index: Int, toolCallId: String, name: String) {
        toolCallIndex[index] = parts.size
        toolArgsBuffer[index] = StringBuilder()
        parts.add(
            MessageContentPart.ToolCall(
                state = ToolCallState.CALL,
                toolCallId = toolCallId,
                toolName = name,
                startTime = System.currentTimeMillis(),
            ),
        )
        push()
    }

    fun toolCallArgs(index: Int, delta: String) {
        toolArgsBuffer[index]?.append(delta)
        push()
    }

    fun toolCallEnd(index: Int) {
        val partIndex = toolCallIndex[index] ?: return
        val raw = toolArgsBuffer[index]?.toString().orEmpty()
        val args = try {
            Json.parseToJsonElement(raw.ifBlank { "{}" })
        } catch (_: Exception) {
            JsonPrimitive(raw)
        }
        val part = parts.getOrNull(partIndex) as? MessageContentPart.ToolCall ?: return
        parts[partIndex] = part.copy(args = args)
        push()
    }

    fun usage(usage: com.aibox.kotlin.core.model.TokenUsage) {
        message = message.copy(usage = usage)
        onSnapshot(message)
    }

    fun finish(reason: String) {
        message = message.copy(finishReason = reason)
        onSnapshot(message)
    }

    fun updateMeta(firstTokenLatency: Long?) {
        if (firstTokenLatency != null && message.firstTokenLatency == null) {
            message = message.copy(firstTokenLatency = firstTokenLatency)
            onSnapshot(message)
        }
    }
}
