package com.aibox.kotlin.feature.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aibox.kotlin.core.common.appString
import com.aibox.kotlin.core.model.Message
import com.aibox.kotlin.core.model.MessageContentPart
import com.aibox.kotlin.core.provider.ReasoningDialects
import com.aibox.kotlin.feature.chat.attachment.ImageViewerRequest
import com.aibox.kotlin.feature.chat.attachment.ImageViewerScreen
import com.aibox.kotlin.feature.modelselector.ModelSelectorSheet

/**
 * 聊天屏：顶部栏（模型选择 + 推理档位）+ 消息列表 + 输入面板。
 * 附件选择器与消息编辑对话框内置于本屏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: String?,
    onBack: () -> Unit,
    onOpenSessions: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(sessionId) { viewModel.load(sessionId) }
    val state by viewModel.state.collectAsState()
    var showModelSelector by remember { mutableStateOf(false) }
    var showReasoningMenu by remember { mutableStateOf(false) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var imageViewer by remember { mutableStateOf<ImageViewerRequest?>(null) }
    val context = LocalContext.current

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addFilesFromUris(uris, context.contentResolver)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.sessionName.ifBlank { appString("chat.new_chat") },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = appString("common.back"))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSessions) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = appString("chat.sessions"))
                    }
                    IconButton(
                        onClick = { viewModel.regenerate() },
                        enabled = !state.generating && state.messages.isNotEmpty(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = appString("chat.regenerate"))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = appString("common.settings"))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = appString(
                            "chat.model_prefix",
                            state.modelId ?: appString("chat.model_select_hint"),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = { showModelSelector = true })
                            .padding(vertical = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // 推理档位（方言参数仅在当前模型支持时生效）
                    Box {
                        Text(
                            text = appString("chat.reasoning") + ": " + reasoningLabel(state.reasoningLevel) + " ▾",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .clickable(onClick = { showReasoningMenu = true })
                                .padding(vertical = 4.dp),
                        )
                        DropdownMenu(
                            expanded = showReasoningMenu,
                            onDismissRequest = { showReasoningMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(appString("chat.reasoning.default")) },
                                onClick = {
                                    viewModel.setReasoningLevel(null)
                                    showReasoningMenu = false
                                },
                            )
                            ReasoningDialects.LEVELS.forEach { level ->
                                DropdownMenuItem(
                                    text = { Text(reasoningLabel(level)) },
                                    onClick = {
                                        viewModel.setReasoningLevel(level)
                                        showReasoningMenu = false
                                    },
                                )
                            }
                        }
                    }
                }
                if (state.interrupted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = appString("chat.interrupted"),
                            color = MaterialTheme.colorScheme.tertiary,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.regenerate() }) {
                            Text(appString("chat.regenerate"))
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    MessageList(
                        viewModel = viewModel,
                        onEditRequest = { message -> editingMessage = message },
                        onImageClick = { keys, index -> imageViewer = ImageViewerRequest(keys, index) },
                    )
                }
                state.error?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                ChatInput(
                    onPickFiles = { filePicker.launch(arrayOf("*/*")) },
                    viewModel = viewModel,
                )
            }
            if (showModelSelector) {
                ModelSelectorSheet(
                    currentProviderId = state.modelProviderId,
                    currentModelId = state.modelId,
                    onSelect = viewModel::selectModel,
                    onDismiss = { showModelSelector = false },
                )
            }
        }
    }

    editingMessage?.let { message ->
        EditMessageDialog(
            message = message,
            onConfirm = { newText ->
                viewModel.editAndResend(message.id, newText)
                editingMessage = null
            },
            onDismiss = { editingMessage = null },
        )
    }

    imageViewer?.let { request ->
        ImageViewerScreen(
            request = request,
            resolveBlob = viewModel::blobFile,
            onDismiss = { imageViewer = null },
        )
    }
}

@Composable
private fun reasoningLabel(level: String?): String = when (level) {
    null -> appString("chat.reasoning.default")
    ReasoningDialects.LEVEL_OFF -> appString("chat.reasoning.off")
    ReasoningDialects.LEVEL_LOW -> appString("chat.reasoning.low")
    ReasoningDialects.LEVEL_MEDIUM -> appString("chat.reasoning.medium")
    ReasoningDialects.LEVEL_HIGH -> appString("chat.reasoning.high")
    else -> level
}

/** 编辑用户消息：保存即在该消息处建分支并重新生成（旧回复保留可切换）。 */
@Composable
private fun EditMessageDialog(
    message: Message,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initial = message.contentParts.filterIsInstance<MessageContentPart.Text>()
        .joinToString("\n") { it.text }
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(appString("chat.edit_message")) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
            ) { Text(appString("chat.save_and_resend")) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(appString("common.cancel")) }
        },
    )
}
