package com.aibox.kotlin.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aibox.kotlin.feature.attachments.AttachmentBar

/**
 * 输入面板：附件条 + 文本框 + 发送/停止按钮，软键盘自动避让。
 */
@Composable
fun ChatInput(
    onPickFiles: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
    ) {
        state.retryNotice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        AttachmentBar(files = state.pendingFiles, onRemove = { viewModel.removeFile(it) })

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::updateInput,
                placeholder = { Text("输入消息…") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
            )
            IconButton(onClick = onPickFiles) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加附件",
                    modifier = Modifier.size(28.dp),
                )
            }
            if (state.generating) {
                FilledIconButton(
                    onClick = viewModel::stop,
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "停止生成",
                    )
                }
            } else {
                FilledIconButton(
                    onClick = viewModel::send,
                    enabled = state.input.isNotBlank() || state.pendingFiles.isNotEmpty(),
                    modifier = Modifier.size(52.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                    )
                }
            }
        }
    }
}
