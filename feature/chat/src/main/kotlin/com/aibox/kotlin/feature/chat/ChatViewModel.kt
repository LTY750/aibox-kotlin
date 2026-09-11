package com.aibox.kotlin.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aibox.kotlin.core.common.I18n
import com.aibox.kotlin.core.conversation.ChatEngine
import com.aibox.kotlin.core.conversation.ChatGenerationEvent
import com.aibox.kotlin.core.conversation.ForkNavInfo
import com.aibox.kotlin.core.conversation.MessageForks
import com.aibox.kotlin.core.model.Message
import com.aibox.kotlin.core.model.MessageContentPart
import com.aibox.kotlin.core.model.MessageFile
import com.aibox.kotlin.core.model.MessageRole
import com.aibox.kotlin.core.model.Session
import com.aibox.kotlin.core.model.SessionSettings
import com.aibox.kotlin.core.storage.SessionRepository
import com.aibox.kotlin.core.storage.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val sessionId: String = ChatEngine.DRAFT_SESSION_ID,
    val sessionName: String = "",
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val pendingFiles: List<MessageFile> = emptyList(),
    val generating: Boolean = false,
    val retryNotice: String? = null,
    val modelProviderId: String? = null,
    val modelId: String? = null,
    /** 推理强度：null=默认 off/low/medium/high。 */
    val reasoningLevel: String? = null,
    /** 分叉导航信息：分叉点消息 id → 分支位置。 */
    val forks: Map<String, ForkNavInfo> = emptyMap(),
    val error: String? = null,
    /** 是否检测到上次生成被中断（进程被杀），供 UI 提示与重试。 */
    val interrupted: Boolean = false,
    /** 消息渲染显示选项（聚合自 Settings；UI 不直接依赖 SettingsRepository）。 */
    val display: MessageDisplayOptions = MessageDisplayOptions.Default,
) {
    val canContinue: Boolean
        get() = messages.lastOrNull()?.let { it.role == MessageRole.ASSISTANT && it.error == null } == true
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatEngine: ChatEngine,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val blobStore: com.aibox.kotlin.core.storage.BlobStore,
    private val attachmentManager: com.aibox.kotlin.feature.attachments.AttachmentManager,
    private val foregroundController: GenerationForegroundController,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state

    init {
        // 显示选项随设置实时更新（设置页改动 → 聊天渲染即时生效）
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                _state.update { it.copy(display = MessageDisplayOptions.from(settings)) }
            }
        }
    }

    /** 按 storageKey 取本地文件（供图片缩略图 / 查看器加载，避免 UI 依赖 BlobStore 实现）。 */
    suspend fun blobFile(storageKey: String): java.io.File? = blobStore.file(storageKey)

    private var generationJob: Job? = null

    /** 当前持有前台服务的会话 id；null 表示未启动保活。 */
    private var foregroundSessionId: String? = null

    /** 打开会话（null = 新会话草稿）。 */
    fun load(sessionId: String?) {
        generationJob?.cancel()
        if (sessionId == null) {
            val settings = settingsRepository.snapshot()
            _state.value = ChatUiState(
                modelProviderId = settings.defaultChatModel?.provider,
                modelId = settings.defaultChatModel?.model,
                display = MessageDisplayOptions.from(settings),
            )
            return
        }
        viewModelScope.launch {
            val first = sessionRepository.getSession(sessionId)
            val settings = settingsRepository.snapshot()
            if (first == null) {
                _state.update { it.copy(error = I18n.t("chat.session_not_found")) }
                return@launch
            }
            // 进程被杀后恢复：清理残留的流式占位消息并提示（T02 / REQ-007）
            val recovered = chatEngine.recoverInterrupted(sessionId)
            val session = if (recovered) (sessionRepository.getSession(sessionId) ?: first) else first
            _state.value = ChatUiState(
                sessionId = session.id,
                sessionName = session.name,
                messages = session.messages,
                modelProviderId = session.settings?.provider ?: settings.defaultChatModel?.provider,
                modelId = session.settings?.modelId ?: settings.defaultChatModel?.model,
                reasoningLevel = session.settings?.reasoningLevel,
                forks = forkNavOf(session),
                interrupted = recovered,
                display = MessageDisplayOptions.from(settings),
            )
        }
    }

    fun updateInput(text: String) {
        _state.update { it.copy(input = text) }
    }

    fun addFiles(files: List<MessageFile>) {
        _state.update { it.copy(pendingFiles = it.pendingFiles + files) }
    }

    /** 从系统文件选择器导入附件（ContentResolver 由调用方传入，ViewModel 不持有上下文）。 */
    fun addFilesFromUris(uris: List<android.net.Uri>, resolver: android.content.ContentResolver) {
        viewModelScope.launch {
            val files = attachmentManager.importUris(uris, resolver)
            if (files.isNotEmpty()) addFiles(files)
        }
    }

    fun removeFile(file: MessageFile) {
        _state.update { it.copy(pendingFiles = it.pendingFiles - file) }
    }

    fun selectModel(providerId: String, modelId: String) {
        _state.update { it.copy(modelProviderId = providerId, modelId = modelId) }
        persistSessionSettings { it.copy(provider = providerId, modelId = modelId) }
    }

    /** 设置推理强度；null = 恢复默认。 */
    fun setReasoningLevel(level: String?) {
        _state.update { it.copy(reasoningLevel = level) }
        persistSessionSettings { it.copy(reasoningLevel = level) }
    }

    /** 切换分叉分支（目标分支下标）。 */
    fun switchFork(forkMessageId: String, toPosition: Int) {
        val current = _state.value
        if (current.generating) return
        viewModelScope.launch {
            val session = sessionRepository.getSession(current.sessionId) ?: return@launch
            val switched = MessageForks.switchFork(session, forkMessageId, toPosition)
            if (switched !== session) {
                sessionRepository.upsertSession(switched)
                _state.update {
                    it.copy(
                        messages = switched.messages,
                        forks = forkNavOf(switched),
                    )
                }
            }
        }
    }

    /** 发送消息：当前 state 合成会话（新会话为草稿 id）。 */
    fun send() {
        val current = _state.value
        val text = current.input.trim()
        if (text.isEmpty() && current.pendingFiles.isEmpty()) return
        if (current.generating) return
        val imageKeys = current.pendingFiles
            .filter { it.fileType.startsWith("image/") }
            .mapNotNull { it.rawStorageKey }
        val otherFiles = current.pendingFiles.filterNot { it.fileType.startsWith("image/") }

        val draft = Session(
            id = current.sessionId,
            name = current.sessionName,
            messages = current.messages,
            settings = SessionSettings(
                provider = current.modelProviderId,
                modelId = current.modelId,
                reasoningLevel = current.reasoningLevel,
            ),
        )

        _state.update { it.copy(input = "", pendingFiles = emptyList(), generating = true, retryNotice = null, error = null, interrupted = false) }
        // 开始生成：启动前台服务保活（失败自动降级，不影响生成）
        startForegroundGeneration(current.sessionId, current.sessionName)
        generationJob = viewModelScope.launch {
            try {
                chatEngine.send(draft, text, imageKeys, otherFiles).collect { event ->
                    onGenerationEvent(event)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 用户停止：状态已在流内处理
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, error = e.message ?: I18n.t("chat.generate_failed")) }
            }
        }
    }

    fun stop() {
        generationJob?.cancel()
        stopForegroundGeneration()
        _state.update { it.copy(generating = false) }
    }

    /** 顶栏重新生成：默认最后一条助手消息。 */
    fun regenerate() = regenerateAt(null)

    /** 定位重新生成：在目标消息前的用户消息处建分支（旧回复保留可切换）。 */
    fun regenerateAt(messageId: String?) {
        val current = _state.value
        if (current.generating || current.sessionId == ChatEngine.DRAFT_SESSION_ID) return
        generationJob?.cancel()
        _state.update { it.copy(generating = true, retryNotice = null, error = null, interrupted = false) }
        startForegroundGeneration(current.sessionId, current.sessionName)
        generationJob = viewModelScope.launch {
            try {
                val session = sessionRepository.getSession(current.sessionId) ?: return@launch
                chatEngine.regenerate(session, messageId).collect { event -> onGenerationEvent(event) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Unit
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, error = e.message ?: I18n.t("chat.generate_failed")) }
            }
        }
    }

    /** 编辑用户消息并重发：在该消息处建分支，旧回复保留可切换。 */
    fun editAndResend(messageId: String, newText: String) {
        val current = _state.value
        if (current.generating || current.sessionId == ChatEngine.DRAFT_SESSION_ID) return
        generationJob?.cancel()
        _state.update { it.copy(generating = true, retryNotice = null, error = null, interrupted = false) }
        startForegroundGeneration(current.sessionId, current.sessionName)
        generationJob = viewModelScope.launch {
            try {
                val session = sessionRepository.getSession(current.sessionId) ?: return@launch
                chatEngine.editAndResend(session, messageId, newText).collect { event -> onGenerationEvent(event) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Unit
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, error = e.message ?: I18n.t("chat.generate_failed")) }
            }
        }
    }

    /** 继续生成：向最后一条助手消息追加内容。 */
    fun continueGeneration() {
        val current = _state.value
        if (current.generating || !current.canContinue || current.sessionId == ChatEngine.DRAFT_SESSION_ID) return
        generationJob?.cancel()
        _state.update { it.copy(generating = true, retryNotice = null, error = null, interrupted = false) }
        startForegroundGeneration(current.sessionId, current.sessionName)
        generationJob = viewModelScope.launch {
            try {
                val session = sessionRepository.getSession(current.sessionId) ?: return@launch
                chatEngine.continueGeneration(session).collect { event -> onGenerationEvent(event) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                Unit
            } catch (e: Exception) {
                _state.update { it.copy(generating = false, error = e.message ?: I18n.t("chat.generate_failed")) }
            }
        }
    }

    private fun onGenerationEvent(event: ChatGenerationEvent) {
        when (event) {
            is ChatGenerationEvent.SessionSaved -> {
                // 草稿会话在首条消息落库后拿到真实 id，刷新保活通知的会话指向
                refreshForegroundGeneration(event.session.id, event.session.name)
                _state.update {
                    // 会话已持久化（含新用户消息 / 分叉 / 压缩摘要 / 自动命名）
                    it.copy(
                        sessionId = event.session.id,
                        sessionName = event.session.name,
                        messages = event.session.messages,
                        forks = forkNavOf(event.session),
                    )
                }
            }
            is ChatGenerationEvent.AssistantStarted,
            is ChatGenerationEvent.AssistantUpdated,
            -> {
                val message = when (event) {
                    is ChatGenerationEvent.AssistantStarted -> event.message
                    is ChatGenerationEvent.AssistantUpdated -> event.message
                    else -> return
                }
                updateForegroundProgress(message)
                _state.update { state ->
                    state.copy(messages = replaceOrAppend(state.messages, message))
                }
            }
            is ChatGenerationEvent.Retrying -> _state.update {
                it.copy(retryNotice = I18n.t("chat.retry_notice", event.attempt, event.maxAttempts))
            }
            is ChatGenerationEvent.Completed -> {
                stopForegroundGeneration()
                _state.update {
                    it.copy(
                        messages = replaceOrAppend(it.messages, event.message),
                        generating = false,
                        retryNotice = null,
                    )
                }
            }
            is ChatGenerationEvent.Failed -> {
                stopForegroundGeneration()
                _state.update {
                    it.copy(
                        messages = replaceOrAppend(it.messages, event.message),
                        generating = false,
                        retryNotice = null,
                        error = event.error.message,
                    )
                }
            }
        }
    }

    private fun forkNavOf(session: Session): Map<String, ForkNavInfo> =
        session.messages.mapNotNull { message ->
            MessageForks.forkNavInfo(session, message.id)?.let { message.id to it }
        }.toMap()

    /** 草稿/已有会话的设置项持久化（模型、推理强度等）。 */
    private fun persistSessionSettings(transform: (SessionSettings) -> SessionSettings) {
        val current = _state.value
        if (current.sessionId == ChatEngine.DRAFT_SESSION_ID) return // 草稿在 send() 时落库
        viewModelScope.launch {
            val session = sessionRepository.getSession(current.sessionId) ?: return@launch
            val updated = session.copy(
                settings = transform(session.settings ?: SessionSettings()),
            )
            sessionRepository.upsertSession(updated)
        }
    }

    /** 会话持久化后同步刷新消息列表（SessionSaved 不带 messages 更新，这里冗余保护）。 */
    private fun replaceOrAppend(list: List<Message>, message: Message): List<Message> {
        val index = list.indexOfFirst { it.id == message.id }
        return if (index >= 0) {
            list.toMutableList().also { it[index] = message }
        } else {
            list + message
        }
    }

    // ---------------------------------------------------------------------
    // 前台服务保活挂钩（T02）
    // ---------------------------------------------------------------------

    /** 启动保活：仅首个会话 id 触发；失败由控制器静默降级。 */
    private fun startForegroundGeneration(sessionId: String, title: String) {
        if (foregroundSessionId != null) return
        foregroundController.start(sessionId, title)
        foregroundSessionId = sessionId
    }

    /** 拿到真实会话 id 后刷新保活指向（草稿 "new" → 真实 id / 标题）。 */
    private fun refreshForegroundGeneration(sessionId: String, title: String) {
        if (foregroundSessionId == null || foregroundSessionId == sessionId) return
        foregroundController.start(sessionId, title)
        foregroundSessionId = sessionId
    }

    /** 节流进度更新（不包含回复正文，仅展示已生成字数）。 */
    private fun updateForegroundProgress(message: Message) {
        if (foregroundSessionId == null) return
        val chars = message.contentParts
            .filterIsInstance<MessageContentPart.Text>()
            .sumOf { it.text.length }
        foregroundController.updateProgress(I18n.t("chat.generating_progress", chars))
    }

    /** 结束保活。 */
    private fun stopForegroundGeneration() {
        if (foregroundSessionId == null) return
        foregroundController.stop()
        foregroundSessionId = null
    }

    override fun onCleared() {
        generationJob?.cancel()
        stopForegroundGeneration()
        super.onCleared()
    }
}
